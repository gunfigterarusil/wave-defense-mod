package com.wavedefense.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server → Client: open the post-match scoreboard screen at the end of a PvP match.
 *
 * <p>Carries the match summary (mode label, winning team, per-player stats and
 * per-team round wins) so the client can render the scoreboard without further
 * round-trips.
 */
public class OpenPostMatchScoreboardPacket {

    /** Minimal per-player record sent to the client. */
    public static final class PlayerRow {
        public final String name;
        public final String team;
        public final int kills;
        public final int deaths;
        public final int assists;
        public final int points;

        public PlayerRow(String name, String team, int kills, int deaths, int assists, int points) {
            this.name = name;
            this.team = team;
            this.kills = kills;
            this.deaths = deaths;
            this.assists = assists;
            this.points = points;
        }
    }

    public final String modeLabel;            // localised mode label ("STANDARD", "DM", "BR", "CTP", "KOTH")
    public final String winnerTeam;           // empty string = draw / no winner
    public final List<PlayerRow> rows;
    public final Map<String, Integer> teamWins; // teamName → round wins (may be empty for DM/BR)

    public OpenPostMatchScoreboardPacket(String modeLabel, String winnerTeam,
                                          List<PlayerRow> rows, Map<String, Integer> teamWins) {
        this.modeLabel  = modeLabel != null ? modeLabel : "";
        this.winnerTeam = winnerTeam != null ? winnerTeam : "";
        this.rows       = rows != null ? rows : new ArrayList<>();
        this.teamWins   = teamWins != null ? teamWins : new LinkedHashMap<>();
    }

    public static void encode(OpenPostMatchScoreboardPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.modeLabel, 32);
        buf.writeUtf(p.winnerTeam, 64);
        buf.writeVarInt(p.rows.size());
        for (PlayerRow r : p.rows) {
            buf.writeUtf(r.name == null ? "" : r.name, 64);
            buf.writeUtf(r.team == null ? "" : r.team, 64);
            buf.writeVarInt(r.kills);
            buf.writeVarInt(r.deaths);
            buf.writeVarInt(r.assists);
            buf.writeVarInt(r.points);
        }
        buf.writeVarInt(p.teamWins.size());
        for (Map.Entry<String, Integer> e : p.teamWins.entrySet()) {
            buf.writeUtf(e.getKey() == null ? "" : e.getKey(), 64);
            buf.writeVarInt(e.getValue() == null ? 0 : e.getValue());
        }
    }

    public static OpenPostMatchScoreboardPacket decode(FriendlyByteBuf buf) {
        String mode = buf.readUtf(32);
        String winner = buf.readUtf(64);
        int n = buf.readVarInt();
        List<PlayerRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(64);
            String team = buf.readUtf(64);
            int k = buf.readVarInt();
            int d = buf.readVarInt();
            int a = buf.readVarInt();
            int pts = buf.readVarInt();
            rows.add(new PlayerRow(name, team, k, d, a, pts));
        }
        int m = buf.readVarInt();
        Map<String, Integer> tw = new LinkedHashMap<>();
        for (int i = 0; i < m; i++) tw.put(buf.readUtf(64), buf.readVarInt());
        return new OpenPostMatchScoreboardPacket(mode, winner, rows, tw);
    }

    public static void handle(OpenPostMatchScoreboardPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    /** Client-only screen opener. Isolated so the server JVM never loads {@code Screen}. */
    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(OpenPostMatchScoreboardPacket p) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new com.wavedefense.gui.PostMatchScoreboardScreen(
                p.modeLabel, p.winnerTeam, p.rows, p.teamWins));
        }
    }
}
