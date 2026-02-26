package com.wavedefense.network.packets;

import com.wavedefense.gui.ClientPvpStateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Синхронізує стан PvP раунду з сервера на клієнт:
 * - фаза, раунд, таймер
 * - статистика гравців (ніки, команди, K/D/A)
 * - перемоги команд
 * - команда поточного гравця (для фільтру нікнеймів)
 */
public class SyncPvpStatePacket {

    private final CompoundTag data;

    public SyncPvpStatePacket(CompoundTag data) { this.data = data; }

    public static void encode(SyncPvpStatePacket p, FriendlyByteBuf buf) { buf.writeNbt(p.data); }
    public static SyncPvpStatePacket decode(FriendlyByteBuf buf) {
        return new SyncPvpStatePacket(buf.readNbt());
    }

    public static void handle(SyncPvpStatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPvpStateManager.update(p.data));
        ctx.get().setPacketHandled(true);
    }

    // ── Builder (сервер) ──────────────────────────────────────────────────

    public static CompoundTag build(
            String locationName,
            String phase,           // WAITING / BUY / ACTIVE / ENDED
            int currentRound,
            int totalRounds,
            int timerSeconds,
            Map<String, Integer> teamWins,
            List<PlayerEntry> players,
            String myTeam           // команда отримувача
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putString("location", locationName);
        tag.putString("phase", phase);
        tag.putInt("currentRound", currentRound);
        tag.putInt("totalRounds", totalRounds);
        tag.putInt("timerSeconds", timerSeconds);
        tag.putString("myTeam", myTeam != null ? myTeam : "");

        // Перемоги команд
        CompoundTag wins = new CompoundTag();
        if (teamWins != null) teamWins.forEach((t, w) -> wins.putInt(t, w));
        tag.put("teamWins", wins);

        // Гравці
        ListTag playerList = new ListTag();
        if (players != null) {
            for (PlayerEntry e : players) {
                CompoundTag pe = new CompoundTag();
                pe.putString("name", e.name);
                pe.putString("team", e.team);
                pe.putInt("kills", e.kills);
                pe.putInt("deaths", e.deaths);
                pe.putInt("assists", e.assists);
                pe.putBoolean("alive", e.alive);
                playerList.add(pe);
            }
        }
        tag.put("players", playerList);
        return tag;
    }

    public record PlayerEntry(String name, String team, int kills, int deaths, int assists, boolean alive) {}
}
