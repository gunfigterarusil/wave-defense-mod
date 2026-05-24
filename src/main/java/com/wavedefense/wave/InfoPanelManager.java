package com.wavedefense.wave;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.InfoPanelSettings;
import com.wavedefense.data.Location;
import com.wavedefense.data.MobSpawnPoint;
import com.wavedefense.data.WaveConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Управляє TextDisplay-сутностями що відображають інформацію про локацію
 * (поточна хвиля, таймер, кількість мобів, гравців тощо).
 *
 * Весь стан (infoPanelEntityIds) живе у {@link LocationSession}.
 * Клас не потребує посилання на WaveManager — використовує лише WaveContext.
 */
public class InfoPanelManager {

    private final WaveContext ctx;

    public InfoPanelManager(WaveContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Головний тік — викликається з WaveManager раз на секунду (кожні 20 тіків).
     */
    public void tick() {
        if (WaveDefenseMod.getServer() == null) return;

        Set<String> allLocNames = WaveDefenseMod.locationManager.getAllLocations()
            .stream().map(Location::getName).collect(Collectors.toSet());

        // Видаляємо панелі для сесій локацій що більше не існують
        ServerLevel world = getOverworld();
        for (LocationSession session : ctx.sessions.values()) {
            if (!allLocNames.contains(session.locationName)) {
                removeTextDisplayFromSession(world, session, "spawn");
                int idx = 0;
                while (session.infoPanelEntityIds.containsKey("mob_" + idx)) {
                    removeTextDisplayFromSession(world, session, "mob_" + idx);
                    idx++;
                }
            }
        }

        Set<String> activeNames = ctx.getActiveLocationNames();

        for (Location loc : WaveDefenseMod.locationManager.getAllLocations()) {
            String locName = loc.getName();
            InfoPanelSettings ips = loc.getInfoPanel();
            if (world == null) continue;

            LocationSession s = ctx.getSession(locName);

            // ── Панель на точці спавну гравців ─────────────────────────────
            if (ips.isSpawnPanelEnabled() && loc.getPlayerSpawn() != null) {
                // Only update panel when there is an active session.
                // Do NOT call getOrCreateSession — that would create ghost sessions for every
                // configured location, polluting waveCtx.sessions and corrupting timer counters.
                LocationSession session = s;
                if (session == null) continue;
                boolean isActive = activeNames.contains(locName);
                Component text = isActive
                    ? buildSpawnPanelText(locName, loc, ips, session)
                    : buildIdlePanelText(locName, loc, ips);
                updateOrCreateTextDisplayInSession(world, session, "spawn",
                    loc.getPlayerSpawn(), ips.getSpawnPanelOffsetY(), text, ips);
            } else {
                if (s != null) {
                    removeTextDisplayFromSession(getOverworld(), s, "spawn");
                }
            }

            // ── Панелі на точках спавну мобів ──────────────────────────────
            List<MobSpawnPoint> mobSpawns = loc.getMobSpawns();
            LocationSession session = s; // never create a session just for panel display
            if (session == null) continue; // no active session → skip mob-spawn panels
            for (int mi = 0; mi < mobSpawns.size(); mi++) {
                String key = "mob_" + mi;
                if (ips.isMobSpawnPanelEnabled()) {
                    Component text = buildMobSpawnPanelText(locName, loc, ips, mi, session);
                    updateOrCreateTextDisplayInSession(world, session, key,
                        mobSpawns.get(mi).getPos(), ips.getMobSpawnOffsetY(), text, ips);
                } else {
                    removeTextDisplayFromSession(world, session, key);
                }
            }
            // Видаляємо панелі для видалених точок мобів
            int idx = mobSpawns.size();
            while (session.infoPanelEntityIds.containsKey("mob_" + idx)) {
                removeTextDisplayFromSession(world, session, "mob_" + idx);
                idx++;
            }
        }
    }

    /** Видаляє всі TextDisplay-сутності інфо-панелей для даної локації. */
    public void removeInfoPanelEntities(String locationName) {
        ServerLevel world = getOverworld();
        LocationSession s = ctx.getSession(locationName);
        if (s == null) return;
        removeTextDisplayFromSession(world, s, "spawn");
        int idx = 0;
        while (s.infoPanelEntityIds.containsKey("mob_" + idx)) {
            removeTextDisplayFromSession(world, s, "mob_" + idx);
            idx++;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — text builders
    // ════════════════════════════════════════════════════════════════════

    private Component buildSpawnPanelText(String locName, Location loc, InfoPanelSettings ips,
                                          LocationSession s) {
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        int curWave   = s != null ? s.currentWave : 1;
        int normalWaves  = (int) loc.getWaves().stream().filter(w -> !w.isTriggerEnabled()).count();
        int secretWaves  = (int) loc.getWaves().stream()
            .filter(w -> w.isTriggerEnabled() && w.isOneTimeOnly()).count();
        int shopSecrets  = (int) loc.getShopItems().stream()
            .filter(sh -> sh.hasAvailabilityTrigger()
                && sh.getAvailabilityTrigger() != com.wavedefense.data.WaveTrigger.SHOP_LOCATION_START)
            .count();
        int playerCount  = ctx.getPlayersInLocation(locName).size();
        int mobsLeft     = s != null ? s.spawnedMobs.size() : 0;

        if (ips.isShowWaveNumber())
            result.append(Component.translatable("wavedefense.panel.wave_line", curWave, normalWaves)).append("\n");
        if (ips.isShowMobsRemaining() && mobsLeft > 0)
            result.append(Component.translatable("wavedefense.panel.mobs_line", mobsLeft)).append("\n");
        if (ips.isShowWaveTimer()) {
            int wt = s != null ? s.waveTimerTicks : 0;
            if (wt > 0) {
                int secsLeft = wt / 20;
                if (secsLeft > 0)
                    result.append(Component.translatable("wavedefense.panel.timer_line", secsLeft)).append("\n");
            }
        }
        if (ips.isShowFirstWaveTimer()) {
            int lwt  = s != null ? s.waveTimerTicks : 0;
            int curW = s != null ? s.currentWave : 1;
            long startMs = s != null ? s.startTimerMs : 0L;
            if (lwt > 0 && curW == 1 && startMs == 0L) {
                int secsLeft = lwt / 20;
                if (secsLeft > 0)
                    result.append(Component.translatable("wavedefense.panel.first_wave_line", secsLeft)).append("\n");
            } else if (lwt > 0 && curW == 1 && startMs > 0L) {
                int secsToStart = (int) Math.max(0, (startMs - System.currentTimeMillis()) / 1000);
                if (secsToStart > 0)
                    result.append(Component.translatable("wavedefense.panel.start_in_line", secsToStart)).append("\n");
            } else if (lwt > 0 && curW == 1) {
                int secsLeft = lwt / 20;
                if (secsLeft > 0)
                    result.append(Component.translatable("wavedefense.panel.first_wave_line", secsLeft)).append("\n");
            }
        }
        if (ips.isShowLobbyTimer() && s != null && s.startTimerMs > 0L) {
            long startMs    = s.startTimerMs;
            int secsToStart = (int) Math.max(0, (startMs - System.currentTimeMillis()) / 1000);
            if (secsToStart > 0)
                result.append(Component.translatable("wavedefense.panel.lobby_line", secsToStart)).append("\n");
        }
        if (ips.isShowPlayerCount())
            result.append(Component.translatable("wavedefense.panel.players_line", playerCount)).append("\n");
        if (ips.isShowSecretCount() && secretWaves > 0)
            result.append(Component.translatable("wavedefense.panel.secrets_line", secretWaves)).append("\n");
        if (ips.isShowShopSecrets() && shopSecrets > 0)
            result.append(Component.translatable("wavedefense.panel.shop_secrets_line", shopSecrets)).append("\n");
        if (ips.isShowPoints()) {
            int totalPts = ctx.getPlayersInLocation(locName).stream()
                .mapToInt(p -> loc.getPlayerPoints(p.getUUID())).sum();
            if (totalPts > 0)
                result.append(Component.translatable("wavedefense.panel.points_line", totalPts)).append("\n");
        }

        return result.getString().isBlank() ? Component.literal("§7" + loc.getName()) : result;
    }

    private Component buildIdlePanelText(String locName, Location loc, InfoPanelSettings ips) {
        int normalWaves = (int) loc.getWaves().stream().filter(w -> !w.isTriggerEnabled()).count();
        net.minecraft.network.chat.MutableComponent result = Component.literal("§e§l" + loc.getName() + "\n");
        if (normalWaves > 0)
            result.append(Component.translatable("wavedefense.panel.idle_waves", normalWaves)).append("\n");
        result.append(Component.translatable("wavedefense.panel.idle_ready"));
        return result;
    }

    private Component buildMobSpawnPanelText(String locName, Location loc,
                                             InfoPanelSettings ips, int spawnIdx,
                                             LocationSession s) {
        net.minecraft.network.chat.MutableComponent result = Component.empty();
        int curWave    = s != null ? s.currentWave : 1;
        int normalWaves = loc.getTotalWaves();

        if (ips.isMobShowWaveNumber())
            result.append(Component.literal("§e" + curWave + "/" + normalWaves)).append("\n");
        if (ips.isMobShowWaveTimer()) {
            int wt = s != null ? s.waveTimerTicks : 0;
            if (wt > 0) {
                int secsLeft = wt / 20;
                if (secsLeft > 0)
                    result.append(Component.translatable("wavedefense.panel.timer_line", secsLeft));
            }
        }
        if (ips.isMobShowMobCount()) {
            int cnt = 0;
            if (curWave > 0 && !loc.getWaves().isEmpty()) {
                WaveConfig wc = null;
                int normalCount = 0;
                for (WaveConfig w : loc.getWaves()) {
                    if (!w.isTriggerEnabled()) {
                        normalCount++;
                        if (normalCount == curWave) { wc = w; break; }
                    }
                }
                if (wc != null) cnt = wc.getMobs().stream().mapToInt(m -> m.getCount()).sum();
            }
            if (cnt > 0)
                result.append(Component.literal("\n")).append(
                    Component.translatable("wavedefense.panel.mob_spawn_mobs", cnt));
        }

        return result.getString().isBlank() ? Component.literal("§7►") : result;
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — TextDisplay CRUD (session-based)
    // ════════════════════════════════════════════════════════════════════

    private void updateOrCreateTextDisplayInSession(ServerLevel world, LocationSession session,
            String key, BlockPos blockPos, float offsetY, Component text, InfoPanelSettings ips) {
        if (world == null || blockPos == null) return;
        if (!world.isLoaded(blockPos)) return; // don't spawn/update in unloaded chunks
        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + offsetY;
        double z = blockPos.getZ() + 0.5;

        UUID existingId = session.infoPanelEntityIds.get(key);
        if (existingId != null) {
            Entity ent = world.getEntity(existingId);
            if (ent instanceof Display.TextDisplay td) {
                applyTextDisplayNbt(td, text, ips);
                return;
            } else {
                session.infoPanelEntityIds.remove(key);
            }
        }

        try {
            Display.TextDisplay td = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
            td.moveTo(x, y, z, 0f, 0f);
            td.setNoGravity(true);
            td.setInvulnerable(true);
            td.setSilent(true);
            world.addFreshEntity(td);
            session.infoPanelEntityIds.put(key, td.getUUID());
            applyTextDisplayNbt(td, text, ips);
        } catch (Exception e) {
            debugLog("Failed to create TextDisplay for " + session.locationName + "/" + key + ": " + e.getMessage());
        }
    }

    private void removeTextDisplayFromSession(ServerLevel world, LocationSession session, String key) {
        if (world == null) return;
        UUID id = session.infoPanelEntityIds.remove(key);
        if (id != null) {
            Entity ent = world.getEntity(id);
            if (ent != null) ent.discard();
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Private — TextDisplay appearance (NBT + reflection)
    // ════════════════════════════════════════════════════════════════════

    private void applyTextDisplayNbt(Display.TextDisplay td, Component text, InfoPanelSettings ips) {
        setTextDisplayText(td, text);
        setBillboardViaReflection(td);

        CompoundTag nbt = new CompoundTag();
        String json = Component.Serializer.toJson(text);
        nbt.putString("text", json);
        nbt.putString("billboard", "center");
        nbt.putFloat("shadow_strength", ips.isHasShadow() ? 1.0f : 0.0f);

        float s = ips.getTextScale();
        CompoundTag transTag = new CompoundTag();
        ListTag lq = new ListTag();
        lq.add(FloatTag.valueOf(0f)); lq.add(FloatTag.valueOf(0f));
        lq.add(FloatTag.valueOf(0f)); lq.add(FloatTag.valueOf(1f));
        ListTag scaleList = new ListTag();
        scaleList.add(FloatTag.valueOf(s)); scaleList.add(FloatTag.valueOf(s));
        scaleList.add(FloatTag.valueOf(s));
        ListTag rq = new ListTag();
        rq.add(FloatTag.valueOf(0f)); rq.add(FloatTag.valueOf(0f));
        rq.add(FloatTag.valueOf(0f)); rq.add(FloatTag.valueOf(1f));
        ListTag trl = new ListTag();
        trl.add(FloatTag.valueOf(0f)); trl.add(FloatTag.valueOf(0f));
        trl.add(FloatTag.valueOf(0f));
        transTag.put("left_rotation", lq);
        transTag.put("scale", scaleList);
        transTag.put("right_rotation", rq);
        transTag.put("translation", trl);
        nbt.put("transformation", transTag);
        try { td.load(nbt); } catch (Exception ignored) {}

        setBillboardViaReflection(td);
        broadcastEntityData(td);
    }

    @SuppressWarnings("unchecked")
    private void setTextDisplayText(Display.TextDisplay td, Component comp) {
        try {
            Class<?> cls = Display.TextDisplay.class;
            while (cls != null && cls != Object.class) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try {
                        Object val = f.get(null);
                        if (!(val instanceof EntityDataAccessor<?> accessor)) continue;
                        Object cur = td.getEntityData().get(
                            (EntityDataAccessor<Object>) accessor);
                        if (cur instanceof Optional) {
                            td.getEntityData().set(
                                (EntityDataAccessor<Optional<Component>>) accessor,
                                Optional.of(comp));
                            return;
                        }
                    } catch (Exception ignored2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            debugLog("setTextDisplayText failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void setBillboardViaReflection(Display.TextDisplay td) {
        try {
            Class<?> cls = Display.class;
            while (cls != null && cls != Object.class) {
                for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try {
                        Object val = f.get(null);
                        if (!(val instanceof EntityDataAccessor<?> accessor)) continue;
                        Object cur = td.getEntityData().get(
                            (EntityDataAccessor<Object>) accessor);
                        if (cur instanceof Display.BillboardConstraints) {
                            td.getEntityData().set(
                                (EntityDataAccessor<Display.BillboardConstraints>) accessor,
                                Display.BillboardConstraints.CENTER);
                            return;
                        }
                    } catch (Exception ignored2) {}
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            debugLog("setBillboardViaReflection failed: " + e.getMessage());
        }
    }

    private static void broadcastEntityData(Entity entity) {
        if (!(entity.level() instanceof ServerLevel sl)) return;
        List<SynchedEntityData.DataValue<?>> dirty = entity.getEntityData().getNonDefaultValues();
        if (dirty != null && !dirty.isEmpty()) {
            sl.getChunkSource().broadcastAndSend(entity,
                new ClientboundSetEntityDataPacket(entity.getId(), dirty));
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  Utility
    // ════════════════════════════════════════════════════════════════════

    private static ServerLevel getOverworld() {
        if (WaveDefenseMod.getServer() == null) return null;
        return (ServerLevel) WaveDefenseMod.getServer().getLevel(Level.OVERWORLD);
    }

    private static void debugLog(String msg) {
        if (com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get())
            WaveDefenseMod.LOGGER.info("[WaveDefense] " + msg);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Save/Load for backup system
    // ─────────────────────────────────────────────────────────────────

    /** Серіалізація стану InfoPanelManager. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // InfoPanel state is stored in LocationSession, not here
        return tag;
    }

    /** Відновлення стану InfoPanelManager. */
    public void load(CompoundTag tag) {
        // No state to restore
    }
}
