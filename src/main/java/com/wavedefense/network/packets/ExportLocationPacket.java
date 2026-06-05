package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.LocationManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.CompoundNBT;

import java.io.File;
import java.util.function.Supplier;

/**
 * C→S: Запит на експорт локації або отримання списку файлів.
 * locationName == "__list__" → відповісти списком файлів
 */
public class ExportLocationPacket {
    private final String locationName;

    public ExportLocationPacket(String locationName) {
        this.locationName = locationName;
    }

    public static void encode(ExportLocationPacket pkt, PacketBuffer buf) {
        buf.writeUtf(pkt.locationName);
    }

    public static ExportLocationPacket decode(PacketBuffer buf) {
        return new ExportLocationPacket(buf.readUtf(64));
    }

    public static void handle(ExportLocationPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            if ("__list__".equals(pkt.locationName)) {
                // Повертаємо список файлів
                File exportDir = getExportDir();
                java.util.List<String> names = new java.util.ArrayList<>();
                if (exportDir.exists()) {
                    File[] files = exportDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.getName().endsWith(".nbt")) names.add(f.getName().replace(".nbt", ""));
                        }
                    }
                }
                com.wavedefense.network.PacketHandler.sendToPlayer(player,
                    new ExportListResponsePacket(names));
                return;
            }

            Location loc = WaveDefenceMod.locationManager.getLocation(pkt.locationName);
            if (loc == null) {
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.location_invalid"), false);
                return;
            }
            try {
                File exportDir = getExportDir();
                exportDir.mkdirs();
                File file = new File(exportDir, pkt.locationName + ".nbt");
                CompoundNBT nbt = loc.save();
                CompressedStreamTools.writeCompressed(nbt, file);
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.export_ok", file.getPath()), false);
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.error("Export failed", e);
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.export_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static File getExportDir() {
        if (WaveDefenceMod.getServer() == null) return new File("wavedefense/export");
        return new File(WaveDefenceMod.getServer().getWorldPath(
            net.minecraft.world.storage.FolderName.ROOT).toFile(), "wavedefense/export");
    }
}
