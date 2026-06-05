package com.wavedefense.network.packets;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** S→C: Список .nbt файлів магазину для import-UI. */
public class ShopExportListPacket {
    private final List<String> names;

    public ShopExportListPacket(List<String> names) { this.names = names; }

    public static void encode(ShopExportListPacket p, PacketBuffer buf) {
        buf.writeInt(p.names.size());
        for (String n : p.names) buf.writeUtf(n);
    }
    public static ShopExportListPacket decode(PacketBuffer buf) {
        int size = buf.readInt();
        List<String> names = new ArrayList<>(size);
        for (int i = 0; i < size; i++) names.add(buf.readUtf(128));
        return new ShopExportListPacket(names);
    }

    public static void handle(ShopExportListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(ShopExportListPacket p) {
            com.wavedefense.gui.ClientShopExportManager.setFiles(p.names);
        }
    }
}
