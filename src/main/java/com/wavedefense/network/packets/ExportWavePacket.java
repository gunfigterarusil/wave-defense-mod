package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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

    public static void encode(ExportWavePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.mode);
    }

    public static ExportWavePacket decode(FriendlyByteBuf buf) {
        return new ExportWavePacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(ExportWavePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            Location loc = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (loc == null) return;

            File dir = new File(WaveDefenseMod.getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "wavedefense/wave_export");
            dir.mkdirs();

            try {
                if ("all".equals(p.mode)) {
                    // Зберігаємо всі хвилі в один файл
                    CompoundTag root = new CompoundTag();
                    root.putString("location", p.locationName);
                    ListTag list = new ListTag();
                    for (WaveConfig wc : loc.getWaves()) list.add(wc.save());
                    root.put("waves", list);
                    String fileName = sanitize(p.locationName) + "_all.nbt";
                    NbtIo.writeCompressed(root, new File(dir, fileName));
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "wavedefense.msg.wave_export_all_success", loc.getWaves().size(), fileName), false);
                } else if (p.mode.startsWith("wave:")) {
                    int idx = Integer.parseInt(p.mode.substring(5));
                    if (idx < 0 || idx >= loc.getWaves().size()) return;
                    WaveConfig wc = loc.getWaves().get(idx);
                    CompoundTag root = new CompoundTag();
                    root.putString("location", p.locationName);
                    root.putString("type", "single");
                    root.put("wave", wc.save());
                    String fileName = sanitize(p.locationName) + "_wave" + (idx + 1) + ".nbt";
                    NbtIo.writeCompressed(root, new File(dir, fileName));
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "wavedefense.msg.wave_export_single_success", (idx + 1), fileName), false);
                }
            } catch (Exception e) {
                WaveDefenseMod.LOGGER.error("ExportWavePacket error", e);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "wavedefense.msg.export_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
