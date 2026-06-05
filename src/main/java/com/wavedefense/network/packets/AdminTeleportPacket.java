package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void encode(AdminTeleportPacket p, PacketBuffer buf) {
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.targetPlayerName);
    }

    public static AdminTeleportPacket decode(PacketBuffer buf) {
        return new AdminTeleportPacket(buf.readUtf(), buf.readUtf());
    }

    public static void handle(AdminTeleportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            Location location = WaveDefenceMod.locationManager.getLocation(packet.locationName);
            if (location == null) {
                sender.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.location_invalid"), true);
                return;
            }
            // PvP locations require a team spawn point; PvE locations require a player spawn.
            if (!location.isPvp() && location.getPlayerSpawn() == null) {
                sender.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.location_invalid"), true);
                return;
            }

            ServerPlayerEntity target = WaveDefenceMod.getServer().getPlayerList()
                    .getPlayerByName(packet.targetPlayerName);
            if (target == null) {
                sender.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.player_not_found", packet.targetPlayerName), true);
                return;
            }

            target.removeAllEffects();
            if (location.isPvp()) {
                // Use spawn index 0 (first team) for admin-forced teleport.
                WaveDefenceMod.waveManager.addPlayerToPvpLocation(target, location, 0);
            } else {
                WaveDefenceMod.waveManager.addPlayerToLocation(target, location);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
