package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;

import java.io.File;
import java.util.function.Supplier;

/** C→S: Завантажити локацію з файлу. */
public class ImportLocationPacket {
    private final String fileName;

    public ImportLocationPacket(String fileName) { this.fileName = fileName; }

    public static void encode(ImportLocationPacket pkt, FriendlyByteBuf buf) { buf.writeUtf(pkt.fileName); }
    public static ImportLocationPacket decode(FriendlyByteBuf buf) { return new ImportLocationPacket(buf.readUtf(64)); }

    public static void handle(ImportLocationPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            try {
                File exportDir = ExportLocationPacket.getExportDir();
                File file = new File(exportDir, pkt.fileName + ".nbt");
                // Path traversal guard: reject filenames that escape the export directory.
                if (!file.getCanonicalPath().startsWith(exportDir.getCanonicalPath())) {
                    WaveDefenseMod.LOGGER.warn("[WaveDefense] Rejected path-traversal import attempt by {}: {}", player.getName().getString(), pkt.fileName);
                    return;
                }
                if (!file.exists()) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.import_not_found", pkt.fileName), false);
                    return;
                }
                CompoundTag nbt = NbtIo.readCompressed(file);
                Location loc = Location.load(nbt);
                // Якщо локація з такою назвою вже існує — замінюємо
                Location existing = WaveDefenseMod.locationManager.getLocation(loc.getName());
                if (existing != null) WaveDefenseMod.locationManager.removeLocation(loc.getName());
                WaveDefenseMod.locationManager.addLocation(loc);
                WaveDefenseMod.locationManager.saveToFile();
                // Синхронізуємо всім гравцям
                com.wavedefense.network.PacketHandler.sendToAll(
                    new SyncLocationDataPacket(WaveDefenseMod.locationManager.save()));
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.import_ok", loc.getName()), false);
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("Import failed", e);
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.import_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
