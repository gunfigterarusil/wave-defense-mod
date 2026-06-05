package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** C→S: Запит списку файлів магазину. S→C: відповідає ShopExportListPacket. */
public class RequestShopExportListPacket {
    public static void encode(RequestShopExportListPacket p, PacketBuffer buf) {}
    public static RequestShopExportListPacket decode(PacketBuffer buf) { return new RequestShopExportListPacket(); }

    public static void handle(RequestShopExportListPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            File dir = ExportShopPacket.getShopExportDir();
            List<String> names = new ArrayList<>();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, n) -> n.endsWith(".nbt"));
                if (files != null) {
                    for (File f : files) names.add(f.getName().replace(".nbt", ""));
                }
            }
            com.wavedefense.network.PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ShopExportListPacket(names));
        });
        ctx.get().setPacketHandled(true);
    }
}
