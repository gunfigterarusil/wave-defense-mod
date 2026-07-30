package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.LocationSection;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * C→S: section-merge save from the location editor.
 *
 * <p>Instead of replacing the whole {@link Location} (last-write-wins, which
 * loses a concurrent admin's edits to other sections), this carries:
 * <ul>
 *   <li>the location name,</li>
 *   <li>the set of {@link LocationSection}s the admin actually changed, and</li>
 *   <li>the admin's full working-copy NBT.</li>
 * </ul>
 *
 * <p>The server takes the <b>current live</b> location's NBT and copies in only
 * the keys belonging to the dirty sections from the working NBT. Keys owned by
 * other sections — and all {@link LocationSection#RUNTIME} keys (play-lock,
 * stats, live points/teams) — keep their live values. So two admins editing
 * different sections of the same location both land their changes without
 * stepping on each other.
 */
public class MergeLocationPacket {

    private final String locationName;
    private final EnumSet<LocationSection> dirty;
    private final CompoundTag workingNbt;

    public MergeLocationPacket(String locationName, Set<LocationSection> dirty, CompoundTag workingNbt) {
        this.locationName = locationName;
        this.dirty = EnumSet.copyOf(dirty.isEmpty() ? EnumSet.noneOf(LocationSection.class) : dirty);
        this.workingNbt = workingNbt;
    }

    public static void encode(MergeLocationPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.locationName, 256);
        buf.writeVarInt(p.dirty.size());
        for (LocationSection s : p.dirty) buf.writeEnum(s);
        buf.writeNbt(p.workingNbt);
    }

    public static MergeLocationPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(256);
        int n = buf.readVarInt();
        EnumSet<LocationSection> dirty = EnumSet.noneOf(LocationSection.class);
        for (int i = 0; i < n; i++) dirty.add(buf.readEnum(LocationSection.class));
        CompoundTag nbt = buf.readNbt();
        return new MergeLocationPacket(name, dirty, nbt);
    }

    public static void handle(MergeLocationPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (p.locationName == null || p.workingNbt == null) return;
            // Rate-limit: NBT decode + merge + broadcast is non-trivial.
            if (!com.wavedefense.network.PacketRateLimiter.allow(
                    player.getUUID(), MergeLocationPacket.class, 500L)) return;

            Location live = WaveDefenseMod.locationManager.getLocation(p.locationName);
            if (live == null) {
                // Location was deleted while the editor was open — nothing to merge.
                WaveDefenseMod.LOGGER.debug("[WaveDefense] Merge dropped: location '{}' no longer exists", p.locationName);
                return;
            }

            // ── Section-level NBT merge ──────────────────────────────────────
            CompoundTag merged = live.save();              // current server state
            CompoundTag working = p.workingNbt;            // admin's edited copy
            int applied = 0;

            // Union of keys across both tags (so removed-in-editor keys are handled too)
            Set<String> allKeys = new java.util.HashSet<>(merged.getAllKeys());
            allKeys.addAll(working.getAllKeys());

            for (String key : allKeys) {
                if (LocationSection.isRuntime(key)) continue; // never let editor touch live runtime state
                LocationSection sec = LocationSection.sectionOf(key);
                // Unmapped keys (sec == null) are merged unconditionally so they're
                // never silently lost (degrades to full-replace for that one key).
                boolean merge = (sec == null) || p.dirty.contains(sec);
                if (!merge) continue;

                if (working.contains(key)) {
                    merged.put(key, working.get(key).copy());
                    applied++;
                } else if (merged.contains(key)) {
                    // Key existed live but the editor's working copy dropped it within
                    // a dirty section → honour the removal.
                    merged.remove(key);
                    applied++;
                }
            }

            Location result = Location.load(merged);
            WaveDefenseMod.locationManager.updateLocation(result);
            WaveDefenseMod.waveManager.broadcastLocationData();

            WaveDefenseMod.LOGGER.debug("[WaveDefense] Merged '{}' from {} ({} keys, sections {})",
                p.locationName, player.getGameProfile().getName(), applied, p.dirty);

            // Sync shop to players currently in this location
            CompoundTag locNbt = result.save();
            SyncShopPacket syncPkt = new SyncShopPacket(p.locationName, locNbt);
            for (ServerPlayer sp : player.getServer().getPlayerList().getPlayers()) {
                PlayerWaveData pd = WaveDefenseMod.waveManager.getPlayerData(sp.getUUID());
                if (pd != null && pd.getCurrentLocation() != null
                        && pd.getCurrentLocation().getName().equals(p.locationName)) {
                    PacketHandler.sendToPlayer(sp, syncPkt);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
