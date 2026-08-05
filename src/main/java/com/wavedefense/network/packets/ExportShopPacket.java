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
import java.util.function.Supplier;

/**
 * C→S: Експорт магазину локації у .nbt файл.
 * mode: "global" = глобальний магазин, "point:name" = конкретна точка.
 */
public class ExportShopPacket {
    private final String locationName;
    private final String mode; // "global" або "point:PointName"

    public ExportShopPacket(String locationName, String mode) {
        this.locationName = locationName;
        this.mode = mode;
    }

    public static void encode(ExportShopPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.mode);
    }
    public static ExportShopPacket decode(FriendlyByteBuf buf) {
        return new ExportShopPacket(buf.readUtf(64), buf.readUtf(128));
    }

    public static void handle(ExportShopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            Location loc = WaveDefenseMod.locationManager.getLocation(pkt.locationName);
            if (loc == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.локацію_не_знайдено_value_a5c2c216", pkt.locationName), false);
                return;
            }
            try {
                File dir = getShopExportDir();
                dir.mkdirs();
                CompoundTag tag = new CompoundTag();
                String fileName;

                if ("global".equals(pkt.mode)) {
                    // Зберігаємо глобальний список товарів
                    ListTag items = new ListTag();
                    for (ShopItem si : loc.getShopItems()) items.add(si.save());
                    tag.put("items", items);
                    tag.putString("mode", "global");
                    tag.putString("location", pkt.locationName);
                    fileName = com.wavedefense.network.FilePathGuard.sanitizeFileName(pkt.locationName)
                        + "_shop_global";
                } else if (pkt.mode.startsWith("point:")) {
                    String pointName = pkt.mode.substring(6);
                    ShopPoint sp = loc.getShopPoints().stream()
                        .filter(p -> p.getName().equals(pointName)).findFirst().orElse(null);
                    if (sp == null) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.точку_магазину_не_знайдено_value_6a6709d5", pointName), false);
                        return;
                    }
                    tag.put("point", sp.save());
                    tag.putString("mode", "point");
                    tag.putString("location", pkt.locationName);
                    fileName = com.wavedefense.network.FilePathGuard.sanitizeFileName(pkt.locationName)
                        + "_shop_" + com.wavedefense.network.FilePathGuard.sanitizeFileName(pointName);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("wavedefense.auto.невідомий_режим_value_c998740d", pkt.mode), false);
                    return;
                }

                File file = new File(dir, fileName + ".nbt");
                // Defence in depth: the name is sanitised above, but locations created
                // before name validation existed may still carry anything.
                if (!com.wavedefense.network.FilePathGuard.checkInside(
                        dir, file, player.getName().getString(), fileName)) {
                    return;
                }
                NbtIo.writeCompressed(tag, file);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wavedefense.msg.export_ok", file.getName()), false);
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("Shop export failed", e);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wavedefense.msg.export_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static File getShopExportDir() {
        if (WaveDefenseMod.getServer() == null) return new File("wavedefense/shop_export");
        return new File(WaveDefenseMod.getServer().getWorldPath(
            net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "wavedefense/shop_export");
    }
}
