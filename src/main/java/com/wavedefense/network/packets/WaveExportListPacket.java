package com.wavedefense.network.packets;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C: Список файлів у wave_export/. */
public class WaveExportListPacket {
    private final List<String> fileNames;

    public WaveExportListPacket(List<String> fileNames) { this.fileNames = fileNames; }

    public static void encode(WaveExportListPacket p, PacketBuffer buf) {
        buf.writeInt(p.fileNames.size());
        for (String s : p.fileNames) buf.writeUtf(s);
    }

    public static WaveExportListPacket decode(PacketBuffer buf) {
        int sz = buf.readInt();
        List<String> list = new ArrayList<>(sz);
        for (int i = 0; i < sz; i++) list.add(buf.readUtf());
        return new WaveExportListPacket(list);
    }

    public static void handle(WaveExportListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(WaveExportListPacket p) {
            com.wavedefense.gui.ClientWaveExportManager.update(p.fileNames);
        }
    }
}
