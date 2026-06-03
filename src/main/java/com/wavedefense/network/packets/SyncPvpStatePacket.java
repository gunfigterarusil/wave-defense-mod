package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SyncPvpStatePacket {

    private final CompoundTag data;

    public SyncPvpStatePacket(CompoundTag data) { this.data = data; }

    public static void encode(SyncPvpStatePacket p, FriendlyByteBuf buf) { buf.writeNbt(p.data); }

    public static SyncPvpStatePacket decode(FriendlyByteBuf buf) {
        return new SyncPvpStatePacket(buf.readNbt());
    }

    public static void handle(SyncPvpStatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncPvpStatePacket p) {
            com.wavedefense.gui.ClientPvpStateManager.update(p.data);
        }
    }

    // ── Builder (сервер) ──────────────────────────────────────────────────

    public static CompoundTag build(
            String locationName,
            String phase,
            int currentRound,
            int totalRounds,
            int timerSeconds,
            Map<String, Integer> teamWins,
            List<PlayerEntry> players,
            String myTeam
    ) {
        return build(locationName, phase, currentRound, totalRounds, timerSeconds,
            teamWins, players, myTeam, java.util.Collections.emptySet());
    }

    /** v0.2.62 overload: include readyPlayer names so client can render the
     *  PvpReadyHud overlay during READY_CHECK phase. */
    public static CompoundTag build(
            String locationName,
            String phase,
            int currentRound,
            int totalRounds,
            int timerSeconds,
            Map<String, Integer> teamWins,
            List<PlayerEntry> players,
            String myTeam,
            java.util.Set<String> readyPlayerNames
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putString("location", locationName);
        tag.putString("phase", phase);
        tag.putInt("currentRound", currentRound);
        tag.putInt("totalRounds", totalRounds);
        tag.putInt("timerSeconds", timerSeconds);
        tag.putString("myTeam", myTeam != null ? myTeam : "");

        CompoundTag wins = new CompoundTag();
        if (teamWins != null) teamWins.forEach((t, w) -> wins.putInt(t, w));
        tag.put("teamWins", wins);

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

        // v0.2.62: ready-player names (omit when empty/non-READY_CHECK to save bytes)
        if (readyPlayerNames != null && !readyPlayerNames.isEmpty()) {
            ListTag rl = new ListTag();
            for (String n : readyPlayerNames) rl.add(StringTag.valueOf(n));
            tag.put("readyPlayers", rl);
        }
        return tag;
    }

    public record PlayerEntry(String name, String team, int kills, int deaths, int assists, boolean alive) {}
}
