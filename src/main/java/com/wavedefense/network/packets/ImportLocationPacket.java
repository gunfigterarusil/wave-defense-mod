package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.CompoundNBT;

import java.io.File;
import java.util.function.Supplier;

/** C→S: Завантажити локацію з файлу. */
public class ImportLocationPacket {
    private final String fileName;

    public ImportLocationPacket(String fileName) { this.fileName = fileName; }

    public static void encode(ImportLocationPacket pkt, PacketBuffer buf) { buf.writeUtf(pkt.fileName); }
    public static ImportLocationPacket decode(PacketBuffer buf) { return new ImportLocationPacket(buf.readUtf(64)); }

    public static void handle(ImportLocationPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            try {
                File exportDir = ExportLocationPacket.getExportDir();
                File file = new File(exportDir, pkt.fileName + ".nbt");
                // Path traversal guard: reject filenames that escape the export directory.
                if (!file.getCanonicalPath().startsWith(exportDir.getCanonicalPath())) {
                    WaveDefenceMod.LOGGER.warn("[WaveDefense] Rejected path-traversal import attempt by {}: {}", player.getName().getString(), pkt.fileName);
                    return;
                }
                if (!file.exists()) {
                    player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.import_not_found", pkt.fileName), false);
                    return;
                }
                CompoundNBT nbt = CompressedStreamTools.readCompressed(file);
                Location loc = Location.load(nbt);
                // Якщо локація з такою назвою вже існує — замінюємо
                Location existing = WaveDefenceMod.locationManager.getLocation(loc.getName());
                if (existing != null) WaveDefenceMod.locationManager.removeLocation(loc.getName());
                WaveDefenceMod.locationManager.addLocation(loc);
                WaveDefenceMod.locationManager.saveToFile();
                // Синхронізуємо всім гравцям
                com.wavedefense.network.PacketHandler.sendToAll(
                    new SyncLocationDataPacket(WaveDefenceMod.locationManager.save()));
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.import_ok", loc.getName()), false);
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.error("Import failed", e);
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.import_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
