package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TeleportPacket {
    private final String locationName;
    private final int pvpSpawnIndex; // -1 = PvE (звичайний вхід)

    public TeleportPacket(String locationName) {
        this(locationName, -1);
    }

    /** Конструктор для PvP: вказується індекс команди (точки спавну) */
    public TeleportPacket(String locationName, int pvpSpawnIndex) {
        this.locationName = locationName;
        this.pvpSpawnIndex = pvpSpawnIndex;
    }

    public static void encode(TeleportPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
        buf.writeInt(packet.pvpSpawnIndex);
    }

    public static TeleportPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        int idx = buf.readInt();
        return new TeleportPacket(name, idx);
    }

    public static void handle(TeleportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (packet.locationName.equals("surrender")) {
                WaveDefenseMod.waveManager.surrenderPlayer(player);
                return;
            }

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null) {
                WaveDefenseMod.LOGGER.warn("Invalid location: " + packet.locationName);
                return;
            }

            // Перевіряємо ігрове правило заборони входу
            if (!com.wavedefense.config.WaveGameRules.isLocationEntryAllowed(player)) {
                player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("wavedefense.msg.entry_blocked"),
                    true
                );
                return;
            }

            // PvP: окрема перевірка і вхід
            if (location.isPvp()) {
                int spawnIdx = packet.pvpSpawnIndex;
                if (location.getPvpSpawnPoints().isEmpty()) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.no_pvp_spawns"), true);
                    return;
                }
                // Battle Royale: завжди випадкова точка
                if (location.isBattleRoyale()) {
                    spawnIdx = new java.util.Random().nextInt(location.getPvpSpawnPoints().size());
                } else if (spawnIdx < 0 && location.isPvpTeamAutoBalance()) {
                    spawnIdx = WaveDefenseMod.waveManager.getAutoBalancedSpawnIndex(location, player.getUUID());
                }
                if (spawnIdx < 0 || spawnIdx >= location.getPvpSpawnPoints().size()) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("wavedefense.msg.no_pvp_spawns"), true);
                    return;
                }
                player.removeAllEffects();
                WaveDefenseMod.waveManager.addPlayerToPvpLocation(player, location, spawnIdx);
                return;
            }

            // PvE: стандартна перевірка точки спавну
            if (location.getPlayerSpawn() == null) {
                WaveDefenseMod.LOGGER.warn("No player spawn for PvE location: " + packet.locationName);
                return;
            }

            player.removeAllEffects();
            WaveDefenseMod.waveManager.addPlayerToLocation(player, location);
        });
        ctx.get().setPacketHandled(true);
    }
}

