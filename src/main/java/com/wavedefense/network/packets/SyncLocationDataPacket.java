package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncLocationDataPacket {
    private final CompoundNBT data;

    public SyncLocationDataPacket(CompoundNBT data) {
        this.data = data;
    }

    public static void encode(SyncLocationDataPacket packet, PacketBuffer buf) {
        buf.writeNbt(packet.data);
    }

    public static SyncLocationDataPacket decode(PacketBuffer buf) {
        return new SyncLocationDataPacket(buf.readNbt());
    }

    public static void handle(SyncLocationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    public CompoundNBT getData() {
        return data;
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncLocationDataPacket packet) {
            com.wavedefense.gui.ClientLocationManager.updateLocations(packet.getData());
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.tell(() -> {
                net.minecraft.client.gui.screen.Screen s = mc.screen;
                if (s instanceof com.wavedefense.gui.AdminMenuScreen) {

                    com.wavedefense.gui.AdminMenuScreen ams = (com.wavedefense.gui.AdminMenuScreen) s;
                    ams.init(mc, s.width, s.height);
                } else if (s instanceof com.wavedefense.gui.PlayerMenuScreen) {
     com.wavedefense.gui.PlayerMenuScreen pms = (com.wavedefense.gui.PlayerMenuScreen) s;
                    // Після отримання даних — перебудовуємо список локацій
                    pms.init(mc, s.width, s.height);
                }
            });
        }
    }
}
