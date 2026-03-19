package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** C→S: Запитати список файлів у wave_export/. */
public class RequestWaveExportListPacket {
    public static void encode(RequestWaveExportListPacket p, FriendlyByteBuf buf) {}
    public static RequestWaveExportListPacket decode(FriendlyByteBuf buf) { return new RequestWaveExportListPacket(); }

    public static void handle(RequestWaveExportListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            File dir = new File(WaveDefenseMod.getServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "wavedefense/wave_export");
            List<String> names = new ArrayList<>();
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".nbt"));
                if (files != null) for (File f : files) names.add(f.getName().replace(".nbt", ""));
                names.sort(String::compareToIgnoreCase);
            }
            WaveDefenseMod.packetHandler.send(
                PacketDistributor.PLAYER.with(() -> player),
                new WaveExportListPacket(names));
        });
        ctx.get().setPacketHandled(true);
    }
}
