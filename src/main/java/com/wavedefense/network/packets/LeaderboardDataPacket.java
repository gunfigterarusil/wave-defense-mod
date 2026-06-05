package com.wavedefense.network.packets;

import com.wavedefense.data.LeaderboardRecord;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S→C: delivers leaderboard records for a given location + mode.
 * Decoded by {@link com.wavedefense.gui.ClientLeaderboardCache}.
 */
public class LeaderboardDataPacket {

    private static final int MAX_ENTRIES    = 10;
    private static final int MAX_NAME_LEN   = 40;

    public final String locationName;
    public final String modeKey;
    public final List<LeaderboardRecord> records;

    public LeaderboardDataPacket(String locationName, String modeKey, List<LeaderboardRecord> records) {
        this.locationName = locationName != null ? locationName : "";
        this.modeKey      = modeKey      != null ? modeKey      : "";
        this.records      = records      != null ? new ArrayList<>(records) : new ArrayList<>();
    }

    // ── Encode ────────────────────────────────────────────────────────────
    public static void encode(LeaderboardDataPacket p, PacketBuffer buf) {
        CompoundNBT root = new CompoundNBT();
        root.putString("loc",  p.locationName);
        root.putString("mode", p.modeKey);
        ListNBT list = new ListNBT();
        int count = Math.min(MAX_ENTRIES, p.records.size());
        for (int i = 0; i < count; i++) {
            LeaderboardRecord rec = p.records.get(i);
            CompoundNBT rt = rec.save();
            // Enforce max name length client-side safety
            if (rt.getString("name").length() > MAX_NAME_LEN)
                rt.putString("name", rt.getString("name").substring(0, MAX_NAME_LEN));
            list.add(rt);
        }
        root.put("records", list);
        buf.writeNbt(root);
    }

    // ── Decode ────────────────────────────────────────────────────────────
    public static LeaderboardDataPacket decode(PacketBuffer buf) {
        CompoundNBT root = buf.readNbt();
        if (root == null) return new LeaderboardDataPacket("", "", new ArrayList<>());
        String loc  = root.getString("loc");
        String mode = root.getString("mode");
        List<LeaderboardRecord> recs = new ArrayList<>();
        ListNBT list = root.getList("records", 10);
        for (int i = 0; i < Math.min(MAX_ENTRIES, list.size()); i++) {
            recs.add(LeaderboardRecord.load(list.getCompound(i)));
        }
        return new LeaderboardDataPacket(loc, mode, recs);
    }

    // ── Handle ────────────────────────────────────────────────────────────
    public static void handle(LeaderboardDataPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(LeaderboardDataPacket p) {
            com.wavedefense.gui.ClientLeaderboardCache.update(p.locationName, p.modeKey, p.records);
        }
    }
}
