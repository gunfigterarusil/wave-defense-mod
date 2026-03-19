package com.wavedefense.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → Клієнт: відкрити Wave Defense меню
 */
public class OpenMenuPacket {
    private final boolean adminMode;

    public OpenMenuPacket(boolean adminMode) {
        this.adminMode = adminMode;
    }

    public static void encode(OpenMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.adminMode);
    }

    public static OpenMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenMenuPacket(buf.readBoolean());
    }

    public static void handle(OpenMenuPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    // Клієнтський код винесено в окремий клас щоб сервер не завантажував Screen/Minecraft
    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(OpenMenuPacket packet) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            if (packet.adminMode) {
                mc.setScreen(new com.wavedefense.gui.AdminMenuScreen());
            } else {
                mc.setScreen(new com.wavedefense.gui.PlayerMenuScreen());
            }
        }
    }
}
