package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncLocationDataPacket {
    private final CompoundTag data;

    public SyncLocationDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(SyncLocationDataPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.data);
    }

    public static SyncLocationDataPacket decode(FriendlyByteBuf buf) {
        return new SyncLocationDataPacket(buf.readNbt());
    }

    public static void handle(SyncLocationDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    public CompoundTag getData() {
        return data;
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncLocationDataPacket packet) {
            com.wavedefense.gui.ClientLocationManager.updateLocations(packet.getData());
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.tell(() -> {
                net.minecraft.client.gui.screens.Screen s = mc.screen;
                if (s instanceof com.wavedefense.gui.AdminMenuScreen ams) {
                    ams.init(mc, s.width, s.height);
                } else if (s instanceof com.wavedefense.gui.PlayerMenuScreen pms) {
                    // Після отримання даних — перебудовуємо список локацій
                    pms.init(mc, s.width, s.height);
                }
            });
        }
    }
}
