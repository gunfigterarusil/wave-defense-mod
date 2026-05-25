package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * S→C: syncs capture-point state (owners, progress, scores, timer) every second.
 * Decoded by {@link com.wavedefense.gui.ClientCtpStateManager}.
 */
public class SyncCtpStatePacket {

    public final String locationName;
    public final Map<String, String>  pointOwners;       // pointId → team (null = neutral)
    public final Map<String, String>  pointNames;        // pointId → display name
    public final Map<String, Integer> captureTimeTicks;  // H-3: pointId → capTimeSec*20 (denominator for progress bar)
    public final Map<String, Integer> captureProgress;   // pointId → signed ticks
    public final Map<String, Integer> objectiveScore;    // teamName → score
    public final int scoreToWin;
    public final int roundTicksLeft;

    public SyncCtpStatePacket(String locationName,
                              Map<String, String>  pointOwners,
                              Map<String, String>  pointNames,
                              Map<String, Integer> captureTimeTicks,
                              Map<String, Integer> captureProgress,
                              Map<String, Integer> objectiveScore,
                              int scoreToWin,
                              int roundTicksLeft) {
        this.locationName    = locationName;
        this.pointOwners     = pointOwners;
        this.pointNames      = pointNames;
        this.captureTimeTicks= captureTimeTicks;
        this.captureProgress = captureProgress;
        this.objectiveScore  = objectiveScore;
        this.scoreToWin      = scoreToWin;
        this.roundTicksLeft  = roundTicksLeft;
    }

    // ── Encode ────────────────────────────────────────────────────────────
    public static void encode(SyncCtpStatePacket p, FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        tag.putString("loc", p.locationName != null ? p.locationName : "");
        tag.putInt("scoreToWin", p.scoreToWin);
        tag.putInt("roundTicksLeft", p.roundTicksLeft);

        CompoundTag ownersTag = new CompoundTag();
        if (p.pointOwners != null)
            p.pointOwners.forEach((id, team) -> ownersTag.putString(id, team != null ? team : ""));
        tag.put("owners", ownersTag);

        CompoundTag namesTag = new CompoundTag();
        if (p.pointNames != null)
            p.pointNames.forEach(namesTag::putString);
        tag.put("names", namesTag);

        // H-3: capture time denominator per point
        CompoundTag capTicksTag = new CompoundTag();
        if (p.captureTimeTicks != null)
            p.captureTimeTicks.forEach((id, t) -> capTicksTag.putInt(id, t));
        tag.put("capTicks", capTicksTag);

        CompoundTag progTag = new CompoundTag();
        if (p.captureProgress != null)
            p.captureProgress.forEach((id, prog) -> progTag.putInt(id, prog));
        tag.put("prog", progTag);

        CompoundTag scoreTag = new CompoundTag();
        if (p.objectiveScore != null)
            p.objectiveScore.forEach((team, score) -> scoreTag.putInt(team, score));
        tag.put("score", scoreTag);

        buf.writeNbt(tag);
    }

    // ── Decode ────────────────────────────────────────────────────────────
    public static SyncCtpStatePacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) return new SyncCtpStatePacket("",
                new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), 0, 0);

        String loc      = tag.getString("loc");
        int    stw      = tag.getInt("scoreToWin");
        int    rtl      = tag.getInt("roundTicksLeft");

        Map<String, String> owners = new LinkedHashMap<>();
        CompoundTag ownersTag = tag.getCompound("owners");
        for (String key : ownersTag.getAllKeys()) {
            String val = ownersTag.getString(key);
            owners.put(key, val.isEmpty() ? null : val);
        }

        Map<String, String> names = new LinkedHashMap<>();
        CompoundTag namesTag = tag.getCompound("names");
        for (String key : namesTag.getAllKeys()) names.put(key, namesTag.getString(key));

        // H-3: decode capture time denominator
        Map<String, Integer> capTicks = new LinkedHashMap<>();
        CompoundTag capTicksTag = tag.getCompound("capTicks");
        for (String key : capTicksTag.getAllKeys()) capTicks.put(key, capTicksTag.getInt(key));

        Map<String, Integer> prog = new LinkedHashMap<>();
        CompoundTag progTag = tag.getCompound("prog");
        for (String key : progTag.getAllKeys()) prog.put(key, progTag.getInt(key));

        Map<String, Integer> score = new LinkedHashMap<>();
        CompoundTag scoreTag = tag.getCompound("score");
        for (String key : scoreTag.getAllKeys()) score.put(key, scoreTag.getInt(key));

        return new SyncCtpStatePacket(loc, owners, names, capTicks, prog, score, stw, rtl);
    }

    // ── Handle ────────────────────────────────────────────────────────────
    public static void handle(SyncCtpStatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncCtpStatePacket p) {
            com.wavedefense.gui.ClientCtpStateManager.update(p);
        }
    }
}
