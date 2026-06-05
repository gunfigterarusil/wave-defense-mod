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

    public static void encode(ExportShopPacket p, PacketBuffer buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.mode);
    }
    public static ExportShopPacket decode(PacketBuffer buf) {
        return new ExportShopPacket(buf.readUtf(64), buf.readUtf(128));
    }

    public static void handle(ExportShopPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            Location loc = WaveDefenceMod.locationManager.getLocation(pkt.locationName);
            if (loc == null) {
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.локацію_не_знайдено_value_a5c2c216", pkt.locationName), false);
                return;
            }
            try {
                File dir = getShopExportDir();
                dir.mkdirs();
                CompoundNBT tag = new CompoundNBT();
                String fileName;

                if ("global".equals(pkt.mode)) {
                    // Зберігаємо глобальний список товарів
                    ListNBT items = new ListNBT();
                    for (ShopItem si : loc.getShopItems()) items.add(si.save());
                    tag.put("items", items);
                    tag.putString("mode", "global");
                    tag.putString("location", pkt.locationName);
                    fileName = pkt.locationName + "_shop_global";
                } else if (pkt.mode.startsWith("point:")) {
                    String pointName = pkt.mode.substring(6);
                    ShopPoint sp = loc.getShopPoints().stream()
                        .filter(p -> p.getName().equals(pointName)).findFirst().orElse(null);
                    if (sp == null) {
                        player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.точку_магазину_не_знайдено_value_6a6709d5", pointName), false);
                        return;
                    }
                    tag.put("point", sp.save());
                    tag.putString("mode", "point");
                    tag.putString("location", pkt.locationName);
                    fileName = pkt.locationName + "_shop_" + pointName.replaceAll("[^a-zA-Z0-9_-]", "_");
                } else {
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.невідомий_режим_value_c998740d", pkt.mode), false);
                    return;
                }

                File file = new File(dir, fileName + ".nbt");
                CompressedStreamTools.writeCompressed(tag, file);
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                    "wavedefense.msg.export_ok", file.getName()), false);
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.error("Shop export failed", e);
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                    "wavedefense.msg.export_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static File getShopExportDir() {
        if (WaveDefenceMod.getServer() == null) return new File("wavedefense/shop_export");
        return new File(WaveDefenceMod.getServer().getWorldPath(
            net.minecraft.world.storage.FolderName.ROOT).toFile(), "wavedefense/shop_export");
    }
}
