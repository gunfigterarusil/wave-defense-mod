package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

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

    public static void encode(TeleportPacket packet, PacketBuffer buf) {
        buf.writeUtf(packet.locationName);
        buf.writeInt(packet.pvpSpawnIndex);
    }

    public static TeleportPacket decode(PacketBuffer buf) {
        String name = buf.readUtf();
        int idx = buf.readInt();
        return new TeleportPacket(name, idx);
    }

    public static void handle(TeleportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null) return;
            // G1 fix: rate-limit join/team-select to once per 3 s to prevent spam
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), TeleportPacket.class, 3_000L)) return;

            if (packet.locationName.equals("surrender")) {
                WaveDefenceMod.waveManager.surrenderPlayer(player);
                return;
            }

            Location location = WaveDefenceMod.locationManager.getLocation(packet.locationName);
            if (location == null) {
                WaveDefenceMod.LOGGER.warn("Invalid location: " + packet.locationName);
                return;
            }

            // Перевіряємо ігрове правило заборони входу
            if (!com.wavedefense.config.WaveGameRules.isLocationEntryAllowed(player)) {
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.entry_blocked"),
                    true
                );
                return;
            }

            // Перевірка ліміту гравців на локації
            int maxPlayers = com.wavedefense.config.WaveDefenseConfig.MAX_PLAYERS_PER_LOCATION.get();
            if (maxPlayers > 0 && WaveDefenceMod.waveManager.getPlayersInLocation(location.getName()).size() >= maxPlayers) {
                player.displayClientMessage(
                    new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.location_full", maxPlayers),
                    true);
                return;
            }

            // PvP: окрема перевірка і вхід
            if (location.isPvp()) {
                int spawnIdx = packet.pvpSpawnIndex;
                if (location.getPvpSpawnPoints().isEmpty()) {
                    player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.no_pvp_spawns"), true);
                    return;
                }
                // Battle Royale: завжди випадкова точка
                if (location.isBattleRoyale()) {
                    spawnIdx = new java.util.Random().nextInt(location.getPvpSpawnPoints().size());
                } else if (spawnIdx < 0 && location.isPvpTeamAutoBalance()) {
                    spawnIdx = WaveDefenceMod.waveManager.getAutoBalancedSpawnIndex(location, player.getUUID());
                }
                if (spawnIdx < 0 || spawnIdx >= location.getPvpSpawnPoints().size()) {
                    player.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent("wavedefense.msg.no_pvp_spawns"), true);
                    return;
                }
                player.removeAllEffects();
                WaveDefenceMod.waveManager.addPlayerToPvpLocation(player, location, spawnIdx);
                return;
            }

            // PvE: стандартна перевірка точки спавну
            if (location.getPlayerSpawn() == null) {
                WaveDefenceMod.LOGGER.warn("No player spawn for PvE location: " + packet.locationName);
                return;
            }

            player.removeAllEffects();
            WaveDefenceMod.waveManager.addPlayerToLocation(player, location);
        });
        ctx.get().setPacketHandled(true);
    }
}

