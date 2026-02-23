package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер: телепортує вказаного гравця на локацію від імені адміна (мине перевірку gamerule).
 */
public class AdminTeleportPacket {
    private final String locationName;
    private final String targetPlayerName;

    public AdminTeleportPacket(String locationName, String targetPlayerName) {
        this.locationName = locationName;
        this.targetPlayerName = targetPlayerName;
    }

    public static void encode(AdminTeleportPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.targetPlayerName);
    }

    public static AdminTeleportPacket decode(FriendlyByteBuf buf) {
        return new AdminTeleportPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(AdminTeleportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null || location.getPlayerSpawn() == null) {
                sender.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cЛокація не знайдена або не має точки спавну!"), true);
                return;
            }

            ServerPlayer target = WaveDefenseMod.getServer().getPlayerList()
                    .getPlayerByName(packet.targetPlayerName);
            if (target == null) {
                sender.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§cГравця " + packet.targetPlayerName + " не знайдено!"), true);
                return;
            }

            target.removeAllEffects();
            WaveDefenseMod.waveManager.addPlayerToLocation(target, location);
        });
        ctx.get().setPacketHandled(true);
    }
}
