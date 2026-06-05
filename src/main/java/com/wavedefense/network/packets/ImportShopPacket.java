package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void encode(ImportShopPacket p, PacketBuffer buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.fileName);
        buf.writeUtf(p.targetMode);
    }
    public static ImportShopPacket decode(PacketBuffer buf) {
        return new ImportShopPacket(buf.readUtf(64), buf.readUtf(128), buf.readUtf(128));
    }

    public static void handle(ImportShopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            Location loc = WaveDefenceMod.locationManager.getLocation(pkt.locationName);
            if (loc == null) {
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.локацію_не_знайдено_value_a5c2c216", pkt.locationName), false);
                return;
            }
            try {
                File shopDir = ExportShopPacket.getShopExportDir();
                File file = new File(shopDir, pkt.fileName + ".nbt");
                // Path traversal guard: reject filenames that escape the export directory.
                if (!file.getCanonicalPath().startsWith(shopDir.getCanonicalPath())) {
                    WaveDefenceMod.LOGGER.warn("[WaveDefense] Rejected path-traversal shop import attempt by {}: {}", player.getName().getString(), pkt.fileName);
                    return;
                }
                if (!file.exists()) {
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.файл_не_знайдено_value_e8d8c6cc", pkt.fileName + ".nbt"), false);
                    return;
                }
                CompoundNBT tag = CompressedStreamTools.readCompressed(file);
                String mode = tag.getString("mode");

                if ("global".equals(mode) && "global".equals(pkt.targetMode)) {
                    ListNBT items = tag.getList("items", 10);
                    List<ShopItem> loaded = new ArrayList<>();
                    for (int i = 0; i < items.size(); i++) {
                        ShopItem si = ShopItem.load(items.getCompound(i));
                        if (si != null) loaded.add(si);
                    }
                    loc.getShopItems().clear();
                    loaded.forEach(loc::addShopItem);
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                        "wavedefense.msg.shop_import_global_success", loaded.size(), pkt.locationName), false);

                } else if ("point".equals(mode)) {
                    ShopPoint sp = ShopPoint.load(tag.getCompound("point"));
                    if (sp == null) { player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.помилка_читання_точки_62a2ffb4"), false); return; }
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
                        player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                            "wavedefense.msg.shop_import_point_replaced", sp.getName(), pkt.locationName), false);
                    } else {
                        loc.addShopPoint(sp);
                        player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                            "wavedefense.msg.shop_import_point_added", sp.getName(), pkt.locationName), false);
                    }
                } else {
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.error.shop_mode_incompatible", mode, pkt.targetMode), false);
                    return;
                }

                WaveDefenceMod.locationManager.updateLocation(loc);
                WaveDefenceMod.waveManager.broadcastLocationData();
                // Sync shop до гравців на локації
                CompoundNBT locNbt = loc.save();
                com.wavedefense.network.packets.SyncShopPacket syncPkt = new com.wavedefense.network.packets.SyncShopPacket(pkt.locationName, locNbt);
                for (ServerPlayerEntity p : player.getServer().getPlayerList().getPlayers()) {
                    com.wavedefense.wave.PlayerWaveData pd = WaveDefenceMod.waveManager.getPlayerData(p.getUUID());
                    if (pd != null && pd.getCurrentLocation() != null && pd.getCurrentLocation().getName().equals(pkt.locationName)) {
                        com.wavedefense.network.PacketHandler.sendToPlayer(p, syncPkt);
                    }
                }
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.error("Shop import failed", e);
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                    "wavedefense.msg.import_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
