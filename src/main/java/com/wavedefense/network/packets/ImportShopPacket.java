package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C→S: Імпорт магазину з .nbt файлу у локацію.
 * targetMode: "global" = замінити глобальний магазин, "point:PointName" = замінити/додати точку.
 */
public class ImportShopPacket {
    private final String locationName;
    private final String fileName;
    private final String targetMode; // "global" або "point:PointName"

    public ImportShopPacket(String locationName, String fileName, String targetMode) {
        this.locationName = locationName;
        this.fileName = fileName;
        this.targetMode = targetMode;
    }

    public static void encode(ImportShopPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.fileName);
        buf.writeUtf(p.targetMode);
    }
    public static ImportShopPacket decode(FriendlyByteBuf buf) {
        return new ImportShopPacket(buf.readUtf(64), buf.readUtf(128), buf.readUtf(128));
    }

    public static void handle(ImportShopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            Location loc = WaveDefenseMod.locationManager.getLocation(pkt.locationName);
            if (loc == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.локацію_не_знайдено_value_a5c2c216", pkt.locationName), false);
                return;
            }
            try {
                File file = new File(ExportShopPacket.getShopExportDir(), pkt.fileName + ".nbt");
                if (!file.exists()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.файл_не_знайдено_value_e8d8c6cc", pkt.fileName + ".nbt"), false);
                    return;
                }
                CompoundTag tag = NbtIo.readCompressed(file);
                String mode = tag.getString("mode");

                if ("global".equals(mode) && "global".equals(pkt.targetMode)) {
                    ListTag items = tag.getList("items", 10);
                    List<ShopItem> loaded = new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) {
                        ShopItem si = ShopItem.load(items.getCompound(i));
                        if (si != null) loaded.add(si);
                    }
                    loc.getShopItems().clear();
                    loaded.forEach(loc::addShopItem);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§a✓ Імпортовано §e" + loaded.size() + " §aтоварів до глобального магазину §e" + pkt.locationName), false);

                } else if ("point".equals(mode)) {
                    ShopPoint sp = ShopPoint.load(tag.getCompound("point"));
                    if (sp == null) { player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.помилка_читання_точки_62a2ffb4"), false); return; }
                    if (pkt.targetMode.startsWith("point:")) {
                        String targetPointName = pkt.targetMode.substring(6);
                        // Якщо існує точка з такою назвою — замінюємо товари, інакше додаємо нову
                        boolean found = false;
                        for (ShopPoint existing : loc.getShopPoints()) {
                            if (existing.getName().equals(targetPointName)) {
                                existing.getItems().clear();
                                sp.getItems().forEach(existing::addItem);
                                found = true;
                                break;
                            }
                        }
                        if (!found) loc.addShopPoint(sp);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§a✓ Точку магазину §e" + sp.getName() + " §aімпортовано у §e" + pkt.locationName), false);
                    } else {
                        loc.addShopPoint(sp);
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "§a✓ Точку магазину §e" + sp.getName() + " §aдодано до §e" + pkt.locationName), false);
                    }
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.файл_несумісний_з_цільовим_режимом_файл_1219b59d", mode + ", ціль: " + pkt.targetMode + ")"), false);
                    return;
                }

                WaveDefenseMod.locationManager.updateLocation(loc);
                WaveDefenseMod.waveManager.broadcastLocationData();
                // Sync shop до гравців на локації
                CompoundTag locNbt = loc.save();
                com.wavedefense.network.packets.SyncShopPacket syncPkt = new com.wavedefense.network.packets.SyncShopPacket(pkt.locationName, locNbt);
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    com.wavedefense.wave.PlayerWaveData pd = WaveDefenseMod.waveManager.getPlayerData(p.getUUID());
                    if (pd != null && pd.getCurrentLocation() != null && pd.getCurrentLocation().getName().equals(pkt.locationName)) {
                        com.wavedefense.network.PacketHandler.sendToPlayer(p, syncPkt);
                    }
                }
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("Shop import failed", e);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cПомилка імпорту: " + e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
