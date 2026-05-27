package com.wavedefense.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C: Список доступних .nbt файлів для імпорту. */
public class ExportListResponsePacket {
    private final List<String> names;

    public ExportListResponsePacket(List<String> names) { this.names = names; }

    /** Max name length: 256 chars — matches readUtf limit in decode. */
    private static final int MAX_NAME_LENGTH = 256;

    public static void encode(ExportListResponsePacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.names.size());
        // Truncate at MAX_NAME_LENGTH to match the decode limit and prevent DecoderException.
        for (String n : pkt.names) buf.writeUtf(n.length() > MAX_NAME_LENGTH ? n.substring(0, MAX_NAME_LENGTH) : n);
    }

    public static ExportListResponsePacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) names.add(buf.readUtf(MAX_NAME_LENGTH));
        return new ExportListResponsePacket(names);
    }

    public static void handle(ExportListResponsePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(pkt))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(ExportListResponsePacket pkt) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof com.wavedefense.gui.ImportExportScreen screen) {
                screen.setAvailableExports(pkt.names);
                screen.setStatus(String.format(net.minecraft.client.resources.language.I18n.get("wavedefense.export.status_updated"), pkt.names.size()));
            }
        }
    }
}
