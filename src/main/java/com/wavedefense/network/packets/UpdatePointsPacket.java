package com.wavedefense.network.packets;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdatePointsPacket {
    private final int points;
    private final String locationName;

    public UpdatePointsPacket(int points, String locationName) {
        this.points = points;
        this.locationName = locationName;
    }

    public static void encode(UpdatePointsPacket packet, PacketBuffer buf) {
        buf.writeInt(packet.points);
        buf.writeUtf(packet.locationName);
    }

    public static UpdatePointsPacket decode(PacketBuffer buf) {
        return new UpdatePointsPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(UpdatePointsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(packet))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(UpdatePointsPacket packet) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            com.wavedefense.data.Location loc =
                    com.wavedefense.gui.ClientLocationManager.getLocation(packet.locationName);
            if (loc == null) return;
            int current = loc.getPlayerPoints(mc.player.getUUID());
            loc.addPoints(mc.player.getUUID(), packet.points - current);
            if (mc.screen instanceof com.wavedefense.gui.PlayerShopScreen) {

                com.wavedefense.gui.PlayerShopScreen curShop = (com.wavedefense.gui.PlayerShopScreen) mc.screen;
                mc.setScreen(new com.wavedefense.gui.PlayerShopScreen(loc, curShop.getShopPoint()));
            }
        }
    }
}
