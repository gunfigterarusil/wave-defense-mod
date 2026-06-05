package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class SyncPvpStatePacket {

    private final CompoundNBT data;

    public SyncPvpStatePacket(CompoundNBT data) { this.data = data; }

    public static void encode(SyncPvpStatePacket p, PacketBuffer buf) { buf.writeNbt(p.data); }

    public static SyncPvpStatePacket decode(PacketBuffer buf) {
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

    public static CompoundNBT build(
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
    public static CompoundNBT build(
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
        CompoundNBT tag = new CompoundNBT();
        tag.putString("location", locationName);
        tag.putString("phase", phase);
        tag.putInt("currentRound", currentRound);
        tag.putInt("totalRounds", totalRounds);
        tag.putInt("timerSeconds", timerSeconds);
        tag.putString("myTeam", myTeam != null ? myTeam : "");

        CompoundNBT wins = new CompoundNBT();
        if (teamWins != null) teamWins.forEach((t, w) -> wins.putInt(t, w));
        tag.put("teamWins", wins);

        ListNBT playerList = new ListNBT();
        if (players != null) {
            for (PlayerEntry e : players) {
                CompoundNBT pe = new CompoundNBT();
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
            ListNBT rl = new ListNBT();
            for (String n : readyPlayerNames) rl.add(StringNBT.valueOf(n));
            tag.put("readyPlayers", rl);
        }
        return tag;
    }

    /** 1.16.5 port: record → plain final class. */
    public static final class PlayerEntry {
        public final String name;
        public final String team;
        public final int kills, deaths, assists;
        public final boolean alive;

        public PlayerEntry(String name, String team, int kills, int deaths, int assists, boolean alive) {
            this.name = name; this.team = team;
            this.kills = kills; this.deaths = deaths; this.assists = assists;
            this.alive = alive;
        }
    }
}
