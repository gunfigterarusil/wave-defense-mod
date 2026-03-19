package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** C→S: Запит списку файлів магазину. S→C: відповідає ShopExportListPacket. */
public class RequestShopExportListPacket {
    public static void encode(RequestShopExportListPacket p, FriendlyByteBuf buf) {}
    public static RequestShopExportListPacket decode(FriendlyByteBuf buf) { return new RequestShopExportListPacket(); }

    public static void handle(RequestShopExportListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            File dir = ExportShopPacket.getShopExportDir();
            List<String> names = new ArrayList<>();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".nbt"));
                if (files != null) {
                    for (File f : files) names.add(f.getName().replace(".nbt", ""));
                }
            }
            WaveDefenseMod.packetHandler.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ShopExportListPacket(names));
        });
        ctx.get().setPacketHandled(true);
    }
}
