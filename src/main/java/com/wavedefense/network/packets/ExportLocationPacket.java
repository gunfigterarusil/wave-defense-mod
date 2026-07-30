package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.LocationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;

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

    public static void encode(ExportLocationPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.locationName);
    }

    public static ExportLocationPacket decode(FriendlyByteBuf buf) {
        return new ExportLocationPacket(buf.readUtf(64));
    }

    public static void handle(ExportLocationPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
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

            Location loc = WaveDefenseMod.locationManager.getLocation(pkt.locationName);
            if (loc == null) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.location_invalid"), false);
                return;
            }
            try {
                File exportDir = getExportDir();
                exportDir.mkdirs();
                File file = new File(exportDir, pkt.locationName + ".nbt");
                // Path-traversal guard: reject filenames that escape the export dir
                // (defence-in-depth — the location name is admin-controlled but
                // never charset-validated at creation time).
                if (!file.getCanonicalPath().startsWith(exportDir.getCanonicalPath() + File.separator)) {
                    WaveDefenseMod.LOGGER.warn("[WaveDefense] Rejected path-traversal export attempt by {}: {}",
                        player.getName().getString(), pkt.locationName);
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.export_error", "invalid name"), false);
                    return;
                }
                CompoundTag nbt = loc.save();
                NbtIo.writeCompressed(nbt, file);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.export_ok", file.getPath()), false);
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("Export failed", e);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.export_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static File getExportDir() {
        if (WaveDefenseMod.getServer() == null) return new File("wavedefense/export");
        return new File(WaveDefenseMod.getServer().getWorldPath(
            net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "wavedefense/export");
    }
}
