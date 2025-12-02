package com.wavedefense.network.packets;

import com.wavedefense.gui.ClientLocationManager;
import com.wavedefense.gui.PlayerShopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdatePointsPacket {
    private final int points;
    private final String locationName;

    public UpdatePointsPacket(int points, String locationName) {
        this.points = points;
        this.locationName = locationName;
    }

    public static void encode(UpdatePointsPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.points);
        buf.writeUtf(packet.locationName);
    }

    public static UpdatePointsPacket decode(FriendlyByteBuf buf) {
        return new UpdatePointsPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(UpdatePointsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().player != null) {
                    ClientLocationManager.getLocation(packet.locationName).addPoints(Minecraft.getInstance().player.getUUID(), packet.points - ClientLocationManager.getLocation(packet.locationName).getPlayerPoints(Minecraft.getInstance().player.getUUID()));
                    if (Minecraft.getInstance().screen instanceof PlayerShopScreen) {
                        Minecraft.getInstance().setScreen(new PlayerShopScreen(ClientLocationManager.getLocation(packet.locationName)));
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
