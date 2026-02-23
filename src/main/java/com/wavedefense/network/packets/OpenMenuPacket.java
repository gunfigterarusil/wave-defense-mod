package com.wavedefense.network.packets;

import com.wavedefense.gui.AdminMenuScreen;
import com.wavedefense.gui.PlayerMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → Клієнт: відкрити Wave Defense меню
 */
public class OpenMenuPacket {
    private final boolean adminMode; // true = AdminMenuScreen, false = PlayerMenuScreen

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
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (packet.adminMode) {
                mc.setScreen(new AdminMenuScreen());
            } else {
                mc.setScreen(new PlayerMenuScreen());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
