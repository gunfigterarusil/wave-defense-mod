package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** C→S: Запитати список файлів у wave_export/. */
public class RequestWaveExportListPacket {
    public static void encode(RequestWaveExportListPacket p, PacketBuffer buf) {}
    public static RequestWaveExportListPacket decode(PacketBuffer buf) { return new RequestWaveExportListPacket(); }

    public static void handle(RequestWaveExportListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            File dir = new File(WaveDefenceMod.getServer()
                .getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile(),
                "wavedefense/wave_export");
            List<String> names = new ArrayList<>();
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".nbt"));
                if (files != null) for (File f : files) names.add(f.getName().replace(".nbt", ""));
                names.sort(String::compareToIgnoreCase);
            }
            com.wavedefense.network.PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WaveExportListPacket(names));
        });
        ctx.get().setPacketHandled(true);
    }
}
