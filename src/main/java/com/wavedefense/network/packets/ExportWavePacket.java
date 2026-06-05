package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

import java.io.File;
import java.util.function.Supplier;

/**
 * C→S: Експортує одну або кілька хвиль локації у файл .nbt.
 * mode = "all"         → всі хвилі (файл: locationName_all.nbt)
 * mode = "wave:N"      → хвиля з індексом N (файл: locationName_waveN.nbt)
 * Файли зберігаються у world/wavedefense/wave_export/
 */
public class ExportWavePacket {
    private final String locationName;
    private final String mode; // "all" або "wave:0", "wave:1", ...

    public ExportWavePacket(String locationName, String mode) {
        this.locationName = locationName;
        this.mode = mode;
    }

    public static void encode(ExportWavePacket p, PacketBuffer buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.mode);
    }

    public static ExportWavePacket decode(PacketBuffer buf) {
        return new ExportWavePacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(ExportWavePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            Location loc = WaveDefenceMod.locationManager.getLocation(p.locationName);
            if (loc == null) return;

            File dir = new File(WaveDefenceMod.getServer()
                .getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile(),
                "wavedefense/wave_export");
            dir.mkdirs();

            try {
                if ("all".equals(p.mode)) {
                    // Зберігаємо всі хвилі в один файл
                    CompoundNBT root = new CompoundNBT();
                    root.putString("location", p.locationName);
                    ListNBT list = new ListNBT();
                    for (WaveConfig wc : loc.getWaves()) list.add(wc.save());
                    root.put("waves", list);
                    String fileName = sanitize(p.locationName) + "_all.nbt";
                    CompressedStreamTools.writeCompressed(root, new File(dir, fileName));
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                        "wavedefense.msg.wave_export_all_success", loc.getWaves().size(), fileName), false);
                } else if (p.mode.startsWith("wave:")) {
                    int idx = Integer.parseInt(p.mode.substring(5));
                    if (idx < 0 || idx >= loc.getWaves().size()) return;
                    WaveConfig wc = loc.getWaves().get(idx);
                    CompoundNBT root = new CompoundNBT();
                    root.putString("location", p.locationName);
                    root.putString("type", "single");
                    root.put("wave", wc.save());
                    String fileName = sanitize(p.locationName) + "_wave" + (idx + 1) + ".nbt";
                    CompressedStreamTools.writeCompressed(root, new File(dir, fileName));
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                        "wavedefense.msg.wave_export_single_success", (idx + 1), fileName), false);
                }
            } catch (Exception e) {
                WaveDefenceMod.LOGGER.error("ExportWavePacket error", e);
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                    "wavedefense.msg.export_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
