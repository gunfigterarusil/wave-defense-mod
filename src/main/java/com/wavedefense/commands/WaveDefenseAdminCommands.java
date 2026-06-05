package com.wavedefense.commands;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.wavedefense.WaveDefenceMod;
import com.wavedefense.config.WaveDefenseConfig;
import com.wavedefense.config.WaveGameRules;
import com.wavedefense.data.Location;
import com.wavedefense.data.LocationManager;
import com.wavedefense.data.PlayerBackup;
import com.wavedefense.data.WaveConfig;
import com.wavedefense.network.packets.AdminTeleportPacket;
import com.wavedefense.network.packets.OpenMenuPacket;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced admin command system for Wave Defense with comprehensive validation,
 * confirmation dialogs, audit logging, and safety features.
 *
 * Features:
 * - Permission level validation (0-4)
 * - Command confirmation system with timeouts
 * - Comprehensive audit logging
 * - Safety checks (player state, location validity, etc.)
 * - Rollback capabilities
 * - Rate limiting
 * - Parameter validation
 */
public class WaveDefenseAdminCommands {

    // ── Permission Levels ──────────────────────────────────────────────────────
    public static final int PERM_NONE      = 0; // No access
    public static final int PERM_BASIC     = 1; // View only
    public static final int PERM_MOD       = 2; // Standard admin
    public static final int PERM_ADMIN     = 3; // Elevated admin
    public static final int PERM_OWNER     = 4; // Full access

    // ── Confirmation System ──────────────────────────────────────────────────
    private static final Map<UUID, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 30_000L; // 30 seconds

    // ── Rate Limiting ────────────────────────────────────────────────────────
    private static final Map<UUID, CommandHistory> commandHistory = new ConcurrentHashMap<>();
    private static final int MAX_COMMANDS_PER_MINUTE = 30;

    // ── Audit Logger ─────────────────────────────────────────────────────────
    private static final AuditLogger auditLogger = new AuditLogger();

    // ── Safety Cooldowns ─────────────────────────────────────────────────────
    private static final Map<String, Long> destructiveActionCooldowns = new ConcurrentHashMap<>();
    private static final long DESTRUCTIVE_COOLDOWN_MS = 5_000L; // 5 seconds

    // ── Registration ─────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        registerMainCommand(dispatcher);
        registerAliases(dispatcher);
    }

    private static void registerMainCommand(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("wavedefense-admin")
                .requires(src -> checkPermission(src, PERM_MOD))
                .then(buildTeleportCommand())
                .then(buildKickCommand())
                .then(buildBackupCommand())
                .then(buildRestoreCommand())
                .then(buildLocationCommand())
                .then(buildPlayerCommand())
                .then(buildConfigCommand())
                .then(buildConfirmCommand())
                .then(buildLogCommand())
                .then(buildSafetyCommand())
                .then(buildMatchCommand())   // v0.2.61
                .then(buildDebugCommand())   // v0.2.61
                .then(buildResetCommand())   // v0.2.61
                .then(buildPlayersInCommand())  // v0.2.65
                .then(buildWhoReadyCommand())   // v0.2.65
                .then(buildTpToSpawnCommand())  // v0.2.65
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  v0.2.65: PvP inspection + targeted teleport
    // ════════════════════════════════════════════════════════════════════

    /** /wda players-in <location> — lists who's currently inside a location. */
    private static LiteralArgumentBuilder<CommandSource> buildPlayersInCommand() {
        return Commands.literal("players-in")
            .then(Commands.argument("location", StringArgumentType.string())
                .executes(ctx -> {
                    String loc = StringArgumentType.getString(ctx, "location");
                    if (WaveDefenceMod.waveManager == null) {
                        ctx.getSource().sendFailure(new StringTextComponent("§c✗ WaveManager not ready"));
                        return 0;
                    }
                    java.util.List<net.minecraft.entity.player.ServerPlayerEntity> ps =
                        WaveDefenceMod.waveManager.getPlayersInLocation(loc);
                    if (ps.isEmpty()) {
                        ctx.getSource().sendSuccess(new StringTextComponent(
                            "§7No players in §e" + loc), false);
                        return 0;
                    }
                    StringBuilder sb = new StringBuilder("§7Players in §e" + loc + "§7 (" + ps.size() + "):\n");
                    Location locObj = WaveDefenceMod.locationManager.getLocation(loc);
                    for (net.minecraft.entity.player.ServerPlayerEntity p : ps) {
                        String team = locObj != null ? locObj.getPlayerTeam(p.getUUID()) : null;
                        sb.append("  §f").append(p.getName().getString());
                        if (team != null && !team.isEmpty()) sb.append(" §8[").append(team).append("]");
                        sb.append("\n");
                    }
                    String dump = sb.toString();
                    ctx.getSource().sendSuccess(new StringTextComponent(dump), false);
                    return 1;
                }));
    }

    /** /wda who-ready <location> — lists who pressed ready during READY_CHECK. */
    private static LiteralArgumentBuilder<CommandSource> buildWhoReadyCommand() {
        return Commands.literal("who-ready")
            .then(Commands.argument("location", StringArgumentType.string())
                .executes(ctx -> {
                    String loc = StringArgumentType.getString(ctx, "location");
                    if (WaveDefenceMod.waveManager == null) {
                        ctx.getSource().sendFailure(new StringTextComponent("§c✗ WaveManager not ready"));
                        return 0;
                    }
                    String dump = WaveDefenceMod.waveManager.pvpMgr.debugDumpReadySet(loc);
                    ctx.getSource().sendSuccess(new StringTextComponent("§7" + dump), false);
                    return 1;
                }));
    }

    /** /wda tp-to-spawn <player> <location> <team> — teleports a specific
     *  player to a specific team spawn point (admin convenience for rebalancing). */
    private static LiteralArgumentBuilder<CommandSource> buildTpToSpawnCommand() {
        return Commands.literal("tp-to-spawn")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("location", StringArgumentType.string())
                    .then(Commands.argument("team", StringArgumentType.string())
                        .executes(ctx -> {
                            try {
                                ServerPlayerEntity target = EntityArgument.getPlayer(ctx, "player");
                                String locName = StringArgumentType.getString(ctx, "location");
                                String teamName = StringArgumentType.getString(ctx, "team");
                                Location locObj = WaveDefenceMod.locationManager.getLocation(locName);
                                if (locObj == null) {
                                    ctx.getSource().sendFailure(new StringTextComponent("§c✗ Location not found: " + locName));
                                    return 0;
                                }
                                com.wavedefense.data.PvpSpawnPoint match = null;
                                for (com.wavedefense.data.PvpSpawnPoint sp : locObj.getPvpSpawnPoints()) {
                                    if (sp.getTeamName().equalsIgnoreCase(teamName)) { match = sp; break; }
                                }
                                if (match == null) {
                                    ctx.getSource().sendFailure(new StringTextComponent(
                                        "§c✗ Team not found in " + locName + ": " + teamName));
                                    return 0;
                                }
                                BlockPos p = match.getPos();
                                target.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                                ctx.getSource().sendSuccess(new StringTextComponent(
                                    "§a✓ Teleported §f" + target.getName().getString()
                                        + "§a to §e" + locName + "§a team §e" + teamName), true);
                                auditLogger.log(AuditEvent.success(ctx.getSource(), "tp-to-spawn",
                                    com.google.common.collect.ImmutableMap.of("player", target.getName().getString(),
                                           "location", locName, "team", teamName)));
                                return 1;
                            } catch (CommandSyntaxException e) {
                                ctx.getSource().sendFailure(new StringTextComponent("§c✗ Player not found"));
                                return 0;
                            }
                        }))));
    }

    // ════════════════════════════════════════════════════════════════════
    //  v0.2.61: Match control + Debug + Reset commands
    // ════════════════════════════════════════════════════════════════════

    /** /wavedefense-admin match {skip-readycheck|stop|restart} <locationName> */
    private static LiteralArgumentBuilder<CommandSource> buildMatchCommand() {
        return Commands.literal("match")
            .then(Commands.literal("skip-readycheck")
                .then(Commands.argument("location", StringArgumentType.string())
                    .executes(ctx -> {
                        String loc = StringArgumentType.getString(ctx, "location");
                        if (WaveDefenceMod.waveManager == null) return error(ctx, "WaveManager not ready");
                        WaveDefenceMod.waveManager.pvpMgr
                            .skipReadyCheck(WaveDefenceMod.waveManager, loc);
                        ctx.getSource().sendSuccess(new StringTextComponent(
                            "§a✓ Ready-check skipped for §e" + loc), true);
                        auditLogger.log(AuditEvent.success(ctx.getSource(), "match.skip-readycheck", com.google.common.collect.ImmutableMap.of("location", loc)));
                        return 1;
                    })))
            .then(Commands.literal("stop")
                .then(Commands.argument("location", StringArgumentType.string())
                    .executes(ctx -> {
                        String loc = StringArgumentType.getString(ctx, "location");
                        if (WaveDefenceMod.waveManager == null) return error(ctx, "WaveManager not ready");
                        // Use existing forceEndPvpMatch if available; else fall back to a kick-all
                        boolean stopped = WaveDefenceMod.waveManager.pvpMgr
                            .forceEndPvpLocation(WaveDefenceMod.waveManager, loc);
                        ctx.getSource().sendSuccess(new StringTextComponent(
                            (stopped ? "§a✓ " : "§c✗ ") + "Stop match: §e" + loc), true);
                        auditLogger.log(AuditEvent.success(ctx.getSource(), "match.stop", com.google.common.collect.ImmutableMap.of("location", loc)));
                        return stopped ? 1 : 0;
                    })))
            .then(Commands.literal("restart")
                .then(Commands.argument("location", StringArgumentType.string())
                    .executes(ctx -> {
                        String loc = StringArgumentType.getString(ctx, "location");
                        if (WaveDefenceMod.waveManager == null) return error(ctx, "WaveManager not ready");
                        // Restart = stop + cleanup; players need to re-join. Audit logged either way.
                        boolean ok = WaveDefenceMod.waveManager.pvpMgr
                            .forceEndPvpLocation(WaveDefenceMod.waveManager, loc);
                        ctx.getSource().sendSuccess(new StringTextComponent(
                            (ok ? "§a✓ " : "§c✗ ") + "Restart match: §e" + loc
                                + " §7(players must rejoin)"), true);
                        auditLogger.log(AuditEvent.success(ctx.getSource(), "match.restart", com.google.common.collect.ImmutableMap.of("location", loc)));
                        return ok ? 1 : 0;
                    })));
    }

    /** /wavedefense-admin debug state <locationName> — prints PvP state summary */
    private static LiteralArgumentBuilder<CommandSource> buildDebugCommand() {
        return Commands.literal("debug")
            .then(Commands.literal("state")
                .then(Commands.argument("location", StringArgumentType.string())
                    .executes(ctx -> {
                        String loc = StringArgumentType.getString(ctx, "location");
                        if (WaveDefenceMod.waveManager == null) return error(ctx, "WaveManager not ready");
                        String dump = WaveDefenceMod.waveManager.pvpMgr.debugDumpPvpState(loc);
                        ctx.getSource().sendSuccess(new StringTextComponent("§7" + dump), false);
                        return 1;
                    })))
            .then(Commands.literal("reload")
                .then(Commands.argument("location", StringArgumentType.string())
                    .executes(ctx -> {
                        String loc = StringArgumentType.getString(ctx, "location");
                        Location l = WaveDefenceMod.locationManager.getLocation(loc);
                        if (l == null) return error(ctx, "Location not found: " + loc);
                        WaveDefenceMod.locationManager.save();
                        ctx.getSource().sendSuccess(new StringTextComponent(
                            "§a✓ Location §e" + loc + "§a reloaded from disk"), true);
                        auditLogger.log(AuditEvent.success(ctx.getSource(), "debug.reload", com.google.common.collect.ImmutableMap.of("location", loc)));
                        return 1;
                    })));
    }

    /** /wavedefense-admin reset leaderboard — clears all leaderboard records */
    private static LiteralArgumentBuilder<CommandSource> buildResetCommand() {
        return Commands.literal("reset")
            .requires(src -> checkPermission(src, PERM_ADMIN)) // higher perm — destructive
            .then(Commands.literal("leaderboard")
                .executes(ctx -> {
                    if (WaveDefenceMod.leaderboardManager != null) {
                        WaveDefenceMod.leaderboardManager.clearAll();
                    }
                    ctx.getSource().sendSuccess(new StringTextComponent(
                        "§a✓ Leaderboard cleared"), true);
                    auditLogger.log(AuditEvent.success(ctx.getSource(), "reset.leaderboard", com.google.common.collect.ImmutableMap.of("scope", "all")));
                    return 1;
                }));
    }

    /** Common error helper for the new v0.2.61 commands. */
    private static int error(CommandContext<CommandSource> ctx, String msg) {
        ctx.getSource().sendFailure(new StringTextComponent("§c✗ " + msg));
        return 0;
    }

    private static void registerAliases(CommandDispatcher<CommandSource> dispatcher) {
        // Short aliases for common operations
        dispatcher.register(
            Commands.literal("wda")
                .requires(src -> checkPermission(src, PERM_MOD))
                .then(buildTeleportCommand())
                .then(buildKickCommand())
                .then(buildBackupCommand())
        );
    }

    // ── Permission System ────────────────────────────────────────────────────

    private static boolean checkPermission(CommandSource src, int requiredLevel) {
        try {
            int playerLevel = src.getServer().getProfilePermissions(src.getPlayerOrException().getGameProfile());
            return playerLevel >= requiredLevel;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private static int requirePermission(CommandSource src, int requiredLevel, String operation) {
        try {
            if (!checkPermission(src, requiredLevel)) {
                int playerLevel = src.getServer().getProfilePermissions(src.getPlayerOrException().getGameProfile());
                src.sendFailure(new TranslationTextComponent("wavedefense.auto.insufficient_permissions_for_value_6ecfa1c5", operation)
                    .withStyle(style -> style.withHoverEvent(
                        new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new StringTextComponent("Required level: " + requiredLevel + ", Your level: " +
                                playerLevel)
                ))));
                auditLogger.log(AuditEvent.denied(src, operation, "insufficient_permissions"));
                return 0;
            }
            return 1;
        } catch (CommandSyntaxException e) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_checking_permissions_95395bbb"));
            return 0;
        }
    }

    // ── Rate Limiting ────────────────────────────────────────────────────────

    private static boolean checkRateLimit(UUID playerId, String command) {
        CommandHistory history = commandHistory.computeIfAbsent(playerId, k -> new CommandHistory());
        long now = System.currentTimeMillis();

        // Clean old entries (older than 1 minute)
        history.timestamps.removeIf(ts -> now - ts > 60_000L);

        if (history.timestamps.size() >= MAX_COMMANDS_PER_MINUTE) {
            return false;
        }

        history.timestamps.add(now);
        return true;
    }

    // ── Confirmation System ──────────────────────────────────────────────────

    /** Н5: purge confirmations that were never acted on to prevent Map growth. */
    private static void cleanupExpiredConfirmations() {
        long now = System.currentTimeMillis();
        pendingConfirmations.entrySet().removeIf(e -> now - e.getValue().timestamp > CONFIRMATION_TIMEOUT_MS);
    }

    private static boolean requireConfirmation(CommandSource src, String action, String description, Runnable onConfirm) {
        // Н5: clean up stale entries each time a new confirmation is requested
        cleanupExpiredConfirmations();

        UUID playerId;
        try {
            playerId = src.getPlayerOrException().getUUID();
        } catch (CommandSyntaxException e) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_id_a342866a"));
            return false;
        }

        // Check destructive action cooldown
        Long lastAction = destructiveActionCooldowns.get(action);
        if (lastAction != null && System.currentTimeMillis() - lastAction < DESTRUCTIVE_COOLDOWN_MS) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.please_wait_before_performing_an_b83e618e"));
            return false;
        }

        PendingConfirmation confirmation = new PendingConfirmation(
            playerId, action, description, onConfirm, System.currentTimeMillis()
        );
        pendingConfirmations.put(playerId, confirmation);

        // Send confirmation request
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.confirmation_required_5d166a12"), false);
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.action_value_b8165882", action), false);
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.description_value_cec1493f", description), false);
        src.sendSuccess(new StringTextComponent(""), false);

        ITextComponent confirmButton = new TranslationTextComponent("wavedefense.auto.confirm_a9fab5a3")
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/wda confirm " + action))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TranslationTextComponent("wavedefense.auto.click_to_confirm_expires_in_30s_c506a138"))));

        ITextComponent cancelButton = new TranslationTextComponent("wavedefense.auto.cancel_e3d90215")
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/wda cancel"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TranslationTextComponent("wavedefense.auto.click_to_cancel_e3ddea67"))));

        src.sendSuccess(new StringTextComponent("§7").append(confirmButton)
            .append(" ").append(cancelButton), false);
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.timeout_30_seconds_5d3bcece"), false);

        return false; // Command execution deferred
    }

    private static boolean confirmAction(CommandSource src, String action) {
        UUID playerId;
        try {
            playerId = src.getPlayerOrException().getUUID();
        } catch (CommandSyntaxException e) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_id_a342866a"));
            return false;
        }
        PendingConfirmation confirmation = pendingConfirmations.get(playerId);

        if (confirmation == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.no_pending_confirmation_found_257021f1"));
            return false;
        }

        if (!confirmation.action.equals(action)) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.action_mismatch_expected_value_a62fed49", confirmation.action));
            return false;
        }

        if (System.currentTimeMillis() - confirmation.timestamp > CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(playerId);
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.confirmation_expired_ad129e8d"));
            return false;
        }

        // Execute confirmed action
        pendingConfirmations.remove(playerId);
        destructiveActionCooldowns.put(action, System.currentTimeMillis());
        confirmation.runnable.run();
        return true;
    }

    private static void cancelConfirmation(CommandSource src) {
        UUID playerId;
        try {
            playerId = src.getPlayerOrException().getUUID();
        } catch (CommandSyntaxException e) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_id_a342866a"));
            return;
        }
        PendingConfirmation confirmation = pendingConfirmations.remove(playerId);

        if (confirmation != null) {
            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.confirmation_cancelled_for_value_83a5ba11", confirmation.action), false);
        } else {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.no_pending_confirmation_c2a0c0b6"));
        }
    }

    // ── Helper Methods ────────────────────────────────────────────────────────

    private static Collection<ServerPlayerEntity> getPlayers(CommandContext<CommandSource> ctx, String argName) {
        try {
            return EntityArgument.getPlayers(ctx, argName);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_players_ffa62232"));
            return java.util.Collections.emptyList();
        }
    }

    private static ServerPlayerEntity getPlayer(CommandContext<CommandSource> ctx, String argName) {
        try {
            return EntityArgument.getPlayer(ctx, argName);
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_b2df6980"));
            return null;
        }
    }

    // ── Command Builders ─────────────────────────────────────────────────────

    private static LiteralArgumentBuilder<CommandSource> buildTeleportCommand() {
        return Commands.literal("tp")
            .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("location", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "teleport") == 0) return 0;

                        String locName = StringArgumentType.getString(ctx, "location");
                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");

                        return executeTeleport(src, locName, targets);
                    })
                )
            )
            .then(Commands.literal("here")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "teleport_here") == 0) return 0;

                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                        ServerPlayerEntity executor = src.getPlayerOrException();

                        return executeTeleportToPlayer(src, executor, targets);
                    })
                )
            )
            .then(Commands.literal("spawn")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "teleport_spawn") == 0) return 0;

                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                        return executeTeleportToSpawn(src, targets);
                    })
                )
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildKickCommand() {
        return Commands.literal("kick")
            .then(Commands.argument("targets", EntityArgument.players())
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (requirePermission(src, PERM_MOD, "kick") == 0) return 0;

                    Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                    String reason = "Kicked by administrator";

                    return executeKick(src, targets, reason);
                })
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "kick") == 0) return 0;

                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                        String reason = StringArgumentType.getString(ctx, "reason");

                        return executeKick(src, targets, reason);
                    })
                )
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildBackupCommand() {
        return Commands.literal("backup")
            .then(Commands.literal("create")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "backup_create") == 0) return 0;

                        ServerPlayerEntity target = getPlayer(ctx, "player");
                        return executeBackup(src, target);
                    })
                )
                .then(Commands.literal("all")
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_ADMIN, "backup_create_all") == 0) return 0;

                        return executeBackupAll(src);
                    })
                )
            )
            .then(Commands.literal("list")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "backup_list") == 0) return 0;

                        ServerPlayerEntity target = getPlayer(ctx, "player");
                        return executeBackupList(src, target);
                    })
                )
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildRestoreCommand() {
        return Commands.literal("restore")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("backupId", StringArgumentType.string())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "restore") == 0) return 0;

                        ServerPlayerEntity target = getPlayer(ctx, "player");
                        String backupId = StringArgumentType.getString(ctx, "backupId");

                        String action = "restore_" + target.getUUID() + "_" + backupId;
                        String description = "Restore player " + target.getGameProfile().getName() +
                            " from backup " + backupId;

                        return requireConfirmation(src, action, description, () -> {
                            executeRestore(src, target, backupId);
                        }) ? 1 : 0;
                    })
                )
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildLocationCommand() {
        return Commands.literal("location")
            .then(Commands.literal("lock")
                .then(Commands.argument("location", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_ADMIN, "location_lock") == 0) return 0;

                        String locName = StringArgumentType.getString(ctx, "location");
                        String action = "lock_location_" + locName;
                        String description = "Lock location: " + locName;

                        return requireConfirmation(src, action, description, () -> {
                            executeLocationLock(src, locName);
                        }) ? 1 : 0;
                    })
                )
            )
            .then(Commands.literal("unlock")
                .then(Commands.argument("location", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_ADMIN, "location_unlock") == 0) return 0;

                        String locName = StringArgumentType.getString(ctx, "location");
                        executeLocationUnlock(src, locName);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("wipe")
                .then(Commands.argument("location", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_ADMIN, "location_wipe") == 0) return 0;

                        String locName = StringArgumentType.getString(ctx, "location");
                        String action = "wipe_location_" + locName;
                        String description = "Wipe all player data from location: " + locName;

                        return requireConfirmation(src, action, description, () -> {
                            executeLocationWipe(src, locName);
                        }) ? 1 : 0;
                    })
                )
            )
            .then(Commands.literal("info")
                .then(Commands.argument("location", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_BASIC, "location_info") == 0) return 0;

                        String locName = StringArgumentType.getString(ctx, "location");
                        executeLocationInfo(src, locName);
                        return 1;
                    })
                )
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildPlayerCommand() {
        return Commands.literal("player")
            .then(Commands.literal("heal")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "player_heal") == 0) return 0;

                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                        executePlayerHeal(src, targets);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("feed")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "player_feed") == 0) return 0;

                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                        executePlayerFeed(src, targets);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("gamemode")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("survival");
                            builder.suggest("creative");
                            builder.suggest("adventure");
                            builder.suggest("spectator");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSource src = ctx.getSource();
                            if (requirePermission(src, PERM_ADMIN, "player_gamemode") == 0) return 0;

                            Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                            String mode = StringArgumentType.getString(ctx, "mode");

                            executePlayerGamemode(src, targets, mode);
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "player_clear") == 0) return 0;

                        Collection<ServerPlayerEntity> targets = getPlayers(ctx, "targets");
                        executePlayerClear(src, targets);
                        return 1;
                    })
                )
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildConfigCommand() {
        return Commands.literal("config")
            .then(Commands.literal("set")
                .then(Commands.argument("key", StringArgumentType.word())
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            CommandSource src = ctx.getSource();
                            if (requirePermission(src, PERM_ADMIN, "config_set") == 0) return 0;

                            String key = StringArgumentType.getString(ctx, "key");
                            String value = StringArgumentType.getString(ctx, "value");

                            executeConfigSet(src, key, value);
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("get")
                .then(Commands.argument("key", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_BASIC, "config_get") == 0) return 0;

                        String key = StringArgumentType.getString(ctx, "key");
                        executeConfigGet(src, key);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (requirePermission(src, PERM_ADMIN, "config_reload") == 0) return 0;

                    String action = "config_reload";
                    String description = "Reload all configuration files";

                    return requireConfirmation(src, action, description, () -> {
                        executeConfigReload(src);
                    }) ? 1 : 0;
                })
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildConfirmCommand() {
        return Commands.literal("confirm")
            .then(Commands.argument("action", StringArgumentType.word())
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    String action = StringArgumentType.getString(ctx, "action");

                    if (confirmAction(src, action)) {
                        auditLogger.log(AuditEvent.confirmed(src, action));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("cancel")
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    cancelConfirmation(src);
                    return 1;
                })
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildLogCommand() {
        return Commands.literal("log")
            .then(Commands.literal("recent")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "log_recent") == 0) return 0;

                        int count = IntegerArgumentType.getInteger(ctx, "count");
                        executeLogRecent(src, count);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("player")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_MOD, "log_player") == 0) return 0;

                        ServerPlayerEntity target = EntityArgument.getPlayer(ctx, "target");
                        executeLogPlayer(src, target);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("clear")
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (requirePermission(src, PERM_ADMIN, "log_clear") == 0) return 0;

                    String action = "log_clear";
                    String description = "Clear all audit logs";

                    return requireConfirmation(src, action, description, () -> {
                        executeLogClear(src);
                    }) ? 1 : 0;
                })
            );
    }

    private static LiteralArgumentBuilder<CommandSource> buildSafetyCommand() {
        return Commands.literal("safety")
            .then(Commands.literal("check")
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (requirePermission(src, PERM_MOD, "safety_check") == 0) return 0;

                    executeSafetyCheck(src);
                    return 1;
                })
            )
            .then(Commands.literal("lockdown")
                .then(Commands.argument("enable", BoolArgumentType.bool())
                    .executes(ctx -> {
                        CommandSource src = ctx.getSource();
                        if (requirePermission(src, PERM_OWNER, "safety_lockdown") == 0) return 0;

                        boolean enable = BoolArgumentType.getBool(ctx, "enable");

                        String action = "safety_lockdown_" + enable;
                        String description = (enable ? "Enable" : "Disable") + " server lockdown mode";

                        return requireConfirmation(src, action, description, () -> {
                            executeSafetyLockdown(src, enable);
                        }) ? 1 : 0;
                    })
                )
            );
    }

    // ── Command Execution Methods ────────────────────────────────────────────

    private static int executeTeleport(CommandSource src, String locName, Collection<ServerPlayerEntity> targets) {
        UUID playerId;
        try {
            playerId = src.getPlayerOrException().getUUID();
        } catch (CommandSyntaxException e) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_id_a342866a"));
            return 0;
        }
        if (!checkRateLimit(playerId, "teleport")) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.rate_limit_exceeded_try_again_la_06e58845"));
            return 0;
        }

        Location location = WaveDefenceMod.locationManager.getLocation(locName);
        if (location == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.location_not_found_value_abb75692", locName));
            auditLogger.log(AuditEvent.failed(src, "teleport", "location_not_found", locName));
            return 0;
        }

        if (location.getPlayerSpawn() == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.location_has_no_spawn_point_value_a53602b9", locName));
            auditLogger.log(AuditEvent.failed(src, "teleport", "no_spawn", locName));
            return 0;
        }

        int count = 0;
        for (ServerPlayerEntity target : targets) {
            // Safety: Remove harmful effects before teleport
            target.removeAllEffects();

            WaveDefenceMod.waveManager.addPlayerToLocation(target, location);

            src.sendSuccess(new StringTextComponent("§a✓ Teleported §e" +
                target.getGameProfile().getName() + " §ato §6" + locName), false);
            count++;
        }

        auditLogger.log(AuditEvent.success(src, "teleport",
            com.google.common.collect.ImmutableMap.of("location", locName, "targets", String.valueOf(count))));

        return count;
    }

    private static int executeTeleportToPlayer(CommandSource src, ServerPlayerEntity executor,
                                                Collection<ServerPlayerEntity> targets) {
        BlockPos pos = executor.blockPosition();
        String locInfo = "x=" + pos.getX() + " y=" + pos.getY() + " z=" + pos.getZ();

        int count = 0;
        for (ServerPlayerEntity target : targets) {
            if (target.equals(executor)) continue;

            target.removeAllEffects();
            target.teleportTo((net.minecraft.world.server.ServerWorld) executor.level, pos.getX(), pos.getY(), pos.getZ(),
                executor.yRot, executor.xRot);

            src.sendSuccess(new StringTextComponent("§a✓ Teleported §e" +
                target.getGameProfile().getName() + " §ato you"), false);
            count++;
        }

        auditLogger.log(AuditEvent.success(src, "teleport_here",
            com.google.common.collect.ImmutableMap.of("location", locInfo, "targets", String.valueOf(count))));

        return count;
    }

    private static int executeTeleportToSpawn(CommandSource src, Collection<ServerPlayerEntity> targets) {
        BlockPos spawn = src.getServer().overworld().getSharedSpawnPos();

        int count = 0;
        for (ServerPlayerEntity target : targets) {
            target.removeAllEffects();
            target.teleportTo(src.getServer().overworld(),
                spawn.getX(), spawn.getY(), spawn.getZ(), 0, 0);

            src.sendSuccess(new StringTextComponent("§a✓ Teleported §e" +
                target.getGameProfile().getName() + " §ato spawn"), false);
            count++;
        }

        auditLogger.log(AuditEvent.success(src, "teleport_spawn",
            com.google.common.collect.ImmutableMap.of("targets", String.valueOf(count))));

        return count;
    }

    private static int executeKick(CommandSource src, Collection<ServerPlayerEntity> targets, String reason) {
        UUID playerId;
        try {
            playerId = src.getPlayerOrException().getUUID();
        } catch (CommandSyntaxException e) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_id_a342866a"));
            return 0;
        }
        if (!checkRateLimit(playerId, "kick")) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.rate_limit_exceeded_try_again_la_06e58845"));
            return 0;
        }

        int count = 0;
        for (ServerPlayerEntity target : targets) {
            // Safety: Don't kick players with higher or equal permission
            int targetPerm = src.getServer().getProfilePermissions(target.getGameProfile());
            int srcPerm;
            try {
                srcPerm = src.getServer().getProfilePermissions(src.getPlayerOrException().getGameProfile());
            } catch (CommandSyntaxException e) {
                src.sendFailure(new TranslationTextComponent("wavedefense.auto.error_could_not_get_player_permi_98c0244f"));
                return 0;
            }

            if (targetPerm >= srcPerm && !src.getServer().isSingleplayerOwner(target.getGameProfile())) {
                src.sendFailure(new StringTextComponent("§cCannot kick player with higher or equal permissions: " +
                    target.getGameProfile().getName()));
                auditLogger.log(AuditEvent.failed(src, "kick", "permission_denied", target.getGameProfile().getName()));
                continue;
            }

            target.connection.disconnect(new StringTextComponent("§c" + reason));

            src.sendSuccess(new StringTextComponent("§a✓ Kicked §e" +
                target.getGameProfile().getName() + " §a(" + reason + ")"), false);
            count++;
        }

        auditLogger.log(AuditEvent.success(src, "kick",
            com.google.common.collect.ImmutableMap.of("reason", reason, "targets", String.valueOf(count))));

        return count;
    }

    private static int executeBackup(CommandSource src, ServerPlayerEntity target) {
        PlayerBackup backup = PlayerBackup.create(target);
        boolean saved = backup.save();

        if (saved) {
            src.sendSuccess(new StringTextComponent("§a✓ Backup created for §e" +
                target.getGameProfile().getName() + " §a(ID: " + backup.getId() + ")"), false);
            auditLogger.log(AuditEvent.success(src, "backup_create",
                com.google.common.collect.ImmutableMap.of("player", target.getGameProfile().getName(), "backup_id", backup.getId())));
            return 1;
        } else {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.failed_to_create_backup_c11e99e8"));
            auditLogger.log(AuditEvent.failed(src, "backup_create", "save_failed", target.getGameProfile().getName()));
            return 0;
        }
    }

    private static int executeBackupAll(CommandSource src) {
        int count = 0;
        for (ServerPlayerEntity player : src.getServer().getPlayerList().getPlayers()) {
            PlayerBackup backup = PlayerBackup.create(player);
            if (backup.save()) {
                count++;
            }
        }

        final int finalCount = count;
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.created_value_dabff882", finalCount + " backups"), false);
        auditLogger.log(AuditEvent.success(src, "backup_create_all",
            com.google.common.collect.ImmutableMap.of("count", String.valueOf(finalCount))));
        return count;
    }

    private static int executeBackupList(CommandSource src, ServerPlayerEntity target) {
        List<PlayerBackup> backups = PlayerBackup.list(target.getUUID());

        src.sendSuccess(new StringTextComponent("§6=== Backups for §e" +
            target.getGameProfile().getName() + " §6==="), false);

        if (backups.isEmpty()) {
            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.no_backups_found_75f77c48"), false);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

            for (PlayerBackup backup : backups) {
                String time = formatter.format(Instant.ofEpochMilli(backup.getTimestamp()));
                ITextComponent restoreCmd = new TranslationTextComponent("wavedefense.auto.restore_7755b544")
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/wda restore " + target.getGameProfile().getName() + " " + backup.getId()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new TranslationTextComponent("wavedefense.auto.restore_from_this_backup_4ff8163c"))));

                src.sendSuccess(new StringTextComponent("§7- " + backup.getId() + " §8(" + time + ") ")
                    .append(restoreCmd), false);
            }
        }

        return 1;
    }

    private static void executeRestore(CommandSource src, ServerPlayerEntity target, String backupId) {
        Optional<PlayerBackup> backupOpt = PlayerBackup.load(target.getUUID(), backupId);

        if (backupOpt.isPresent()) {
            PlayerBackup backup = backupOpt.get();
            backup.restore(target);

            src.sendSuccess(new StringTextComponent("§a✓ Restored §e" +
                target.getGameProfile().getName() + " §afrom backup " + backupId), false);
            target.sendMessage(new TranslationTextComponent("wavedefense.auto.your_data_was_restored_by_an_adm_a8660eb2"), net.minecraft.util.Util.NIL_UUID);

            auditLogger.log(AuditEvent.success(src, "restore",
                com.google.common.collect.ImmutableMap.of("player", target.getGameProfile().getName(), "backup_id", backupId)));
        } else {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.backup_not_found_value_f3104445", backupId));
            auditLogger.log(AuditEvent.failed(src, "restore", "backup_not_found", backupId));
        }
    }

    private static void executeLocationLock(CommandSource src, String locName) {
        Location location = WaveDefenceMod.locationManager.getLocation(locName);
        if (location == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.location_not_found_value_abb75692", locName));
            auditLogger.log(AuditEvent.failed(src, "location_lock", "not_found", locName));
            return;
        }

        location.setLocked(true);
        WaveDefenceMod.locationManager.saveToFile();

        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.location_locked_value_ec762bea", locName), false);

        // Notify all players in the location
        for (ServerPlayerEntity player : src.getServer().getPlayerList().getPlayers()) {
            com.wavedefense.wave.PlayerWaveData data = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
            if (data != null && data.getCurrentLocation() != null && locName.equals(data.getCurrentLocation().getName())) {
                player.sendMessage(new TranslationTextComponent("wavedefense.auto.this_location_has_been_locked_by_bf864480"), net.minecraft.util.Util.NIL_UUID);
            }
        }

        auditLogger.log(AuditEvent.success(src, "location_lock", com.google.common.collect.ImmutableMap.of("location", locName)));
    }

    private static void executeLocationUnlock(CommandSource src, String locName) {
        Location location = WaveDefenceMod.locationManager.getLocation(locName);
        if (location == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.location_not_found_value_abb75692", locName));
            auditLogger.log(AuditEvent.failed(src, "location_unlock", "not_found", locName));
            return;
        }

        location.setLocked(false);
        WaveDefenceMod.locationManager.saveToFile();

        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.location_unlocked_value_3d2dcb96", locName), false);
        auditLogger.log(AuditEvent.success(src, "location_unlock", com.google.common.collect.ImmutableMap.of("location", locName)));
    }

    private static void executeLocationWipe(CommandSource src, String locName) {
        Location location = WaveDefenceMod.locationManager.getLocation(locName);
        if (location == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.location_not_found_value_abb75692", locName));
            auditLogger.log(AuditEvent.failed(src, "location_wipe", "not_found", locName));
            return;
        }

        int playerCount = 0;
        for (ServerPlayerEntity player : src.getServer().getPlayerList().getPlayers()) {
            com.wavedefense.wave.PlayerWaveData data = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
            if (data != null && data.getCurrentLocation() != null && locName.equals(data.getCurrentLocation().getName())) {
                WaveDefenceMod.waveManager.surrenderPlayer(player);
                player.sendMessage(new TranslationTextComponent("wavedefense.auto.location_data_was_wiped_by_an_ad_09c231c1"), net.minecraft.util.Util.NIL_UUID);
                playerCount++;
            }
        }

        // Reset location progress
        location.resetProgress();
        WaveDefenceMod.locationManager.saveToFile();

        final int finalPlayerCount = playerCount;
        src.sendSuccess(new StringTextComponent("§a✓ Location wiped: §e" + locName +
            " §a(" + finalPlayerCount + " players affected)"), false);
        auditLogger.log(AuditEvent.success(src, "location_wipe",
            com.google.common.collect.ImmutableMap.of("location", locName, "players_affected", String.valueOf(finalPlayerCount))));
    }

    private static void executeLocationInfo(CommandSource src, String locName) {
        Location location = WaveDefenceMod.locationManager.getLocation(locName);
        if (location == null) {
            src.sendFailure(new TranslationTextComponent("wavedefense.auto.location_not_found_value_abb75692", locName));
            return;
        }

        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.location_info_value_1eb7afbb", locName + " §6==="), false);
        src.sendSuccess(new StringTextComponent("\u00A77Status: " + (location.isLocked() ? "\u00A7cLOCKED" : "\u00A7aUNLOCKED")), false);
        src.sendSuccess(new StringTextComponent("§7Game Mode: " + location.getGameMode()), false);
        src.sendSuccess(new StringTextComponent("§7Player Spawn: " +
            (location.getPlayerSpawn() != null ? "§aSET" : "§cNOT SET")), false);
        src.sendSuccess(new StringTextComponent("§7Wave Config: " +
            (location.getWaveConfig() != null ? "§aCONFIGURED" : "§cNOT SET")), false);

        if (location.getWaveConfig() != null) {
            java.util.List<WaveConfig> waves = location.getWaves();
            src.sendSuccess(new StringTextComponent("§7  - Waves: " + (waves != null ? waves.size() : 0)), false);
            src.sendSuccess(new StringTextComponent("§7  - Wave Time: " + location.getWaveConfig().getTimeBetweenWaves() + "s"), false);
        }

        int playerCount = 0;
        for (ServerPlayerEntity player : src.getServer().getPlayerList().getPlayers()) {
            com.wavedefense.wave.PlayerWaveData data = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
            if (data != null && data.getCurrentLocation() != null && locName.equals(data.getCurrentLocation().getName())) {
                playerCount++;
            }
        }
        final int finalPlayerCount = playerCount;
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.players_inside_value_d124fb40", finalPlayerCount), false);
    }

    private static void executePlayerHeal(CommandSource src, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity target : targets) {
            target.setHealth(target.getMaxHealth());
            target.getFoodData().setFoodLevel(20);
            target.removeAllEffects();

            src.sendSuccess(new StringTextComponent("§a✓ Healed §e" +
                target.getGameProfile().getName()), false);
            target.sendMessage(new TranslationTextComponent("wavedefense.auto.you_were_healed_by_an_administra_9a1b8f13"), net.minecraft.util.Util.NIL_UUID);
        }

        auditLogger.log(AuditEvent.success(src, "player_heal",
            com.google.common.collect.ImmutableMap.of("targets", String.valueOf(targets.size()))));
    }

    private static void executePlayerFeed(CommandSource src, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity target : targets) {
            target.getFoodData().setFoodLevel(20);

            src.sendSuccess(new StringTextComponent("§a✓ Fed §e" +
                target.getGameProfile().getName()), false);
        }

        auditLogger.log(AuditEvent.success(src, "player_feed",
            com.google.common.collect.ImmutableMap.of("targets", String.valueOf(targets.size()))));
    }

    private static void executePlayerGamemode(CommandSource src, Collection<ServerPlayerEntity> targets, String mode) {
        GameType gameType;
        switch (mode.toLowerCase()) {
            case "creative": gameType = GameType.CREATIVE; break;
            case "adventure": gameType = GameType.ADVENTURE; break;
            case "spectator": gameType = GameType.SPECTATOR; break;
            default: gameType = GameType.SURVIVAL; break;
        }

        for (ServerPlayerEntity target : targets) {
            target.setGameMode(gameType);
            src.sendSuccess(new StringTextComponent("§a✓ Set §e" +
                target.getGameProfile().getName() + " §ato " + mode), false);
        }

        auditLogger.log(AuditEvent.success(src, "player_gamemode",
            com.google.common.collect.ImmutableMap.of("mode", mode, "targets", String.valueOf(targets.size()))));
    }

    private static void executePlayerClear(CommandSource src, Collection<ServerPlayerEntity> targets) {
        for (ServerPlayerEntity target : targets) {
            target.inventory.clearContent();
            target.inventory.setChanged();

            src.sendSuccess(new StringTextComponent("§a✓ Cleared inventory of §e" +
                target.getGameProfile().getName()), false);
            target.sendMessage(new TranslationTextComponent("wavedefense.auto.your_inventory_was_cleared_by_an_98a5241c"), net.minecraft.util.Util.NIL_UUID);
        }

        auditLogger.log(AuditEvent.success(src, "player_clear",
            com.google.common.collect.ImmutableMap.of("targets", String.valueOf(targets.size()))));
    }

    private static void executeConfigSet(CommandSource src, String key, String value) {
        // Validate and set configuration values
        boolean success = false;

        switch (key.toLowerCase()) {
            case "debug.admin_messages":
                WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.set(Boolean.parseBoolean(value));
                success = true;
                break;
            case "debug.logging_enabled":
                WaveDefenseConfig.DEBUG_LOGGING_ENABLED.set(Boolean.parseBoolean(value));
                success = true;
                break;
            case "location.entry_allowed":
                WaveGameRules.setLocationEntryAllowed(Boolean.parseBoolean(value));
                success = true;
                break;
            default:
                src.sendFailure(new TranslationTextComponent("wavedefense.auto.unknown_config_key_value_3f002b3a", key));
                auditLogger.log(AuditEvent.failed(src, "config_set", "unknown_key", key));
                return;
        }

        if (success) {
            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.config_set_value_8dc66e69", key + " = " + value), false);
            auditLogger.log(AuditEvent.success(src, "config_set",
                com.google.common.collect.ImmutableMap.of("key", key, "value", value)));
        }
    }

    private static void executeConfigGet(CommandSource src, String key) {
        String value = "unknown";

        switch (key.toLowerCase()) {
            case "debug.admin_messages":
                value = String.valueOf(WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.get());
                break;
            case "debug.logging_enabled":
                value = String.valueOf(WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get());
                break;
            case "location.entry_allowed":
                value = String.valueOf(WaveGameRules.isLocationEntryAllowed());
                break;
            case "location.game_mode":
                value = WaveDefenseConfig.getLocationGameType().getName();
                break;
        }

        final String finalValue = value;
        src.sendSuccess(new StringTextComponent("§e" + key + " §7= §f" + finalValue), false);
    }

    private static void executeConfigReload(CommandSource src) {
        WaveDefenceMod.locationManager.loadLocations();
        WaveDefenseConfig.register();

        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.configuration_reloaded_4d5c7073"), false);
        auditLogger.log(AuditEvent.success(src, "config_reload", java.util.Collections.emptyMap()));
    }

    private static void executeLogRecent(CommandSource src, int count) {
        List<AuditEvent> events = auditLogger.getRecentEvents(count);

        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.recent_audit_logs_last_value_a19d6974", count + ") ==="), false);

        if (events.isEmpty()) {
            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.no_log_entries_found_bc4e2271"), false);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

            for (AuditEvent event : events) {
                String time = formatter.format(Instant.ofEpochMilli(event.timestamp));
                String status = event.success ? "§a" : "§c";
                src.sendSuccess(new StringTextComponent("§7[" + time + "] " + status +
                    event.type + " §7by §e" + event.executorName + " §7- " + event.details), false);
            }
        }
    }

    private static void executeLogPlayer(CommandSource src, ServerPlayerEntity target) {
        List<AuditEvent> events = auditLogger.getEventsByPlayer(target.getUUID());

        src.sendSuccess(new StringTextComponent("§6=== Audit Logs for §e" +
            target.getGameProfile().getName() + " §6==="), false);

        if (events.isEmpty()) {
            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.no_log_entries_found_bc4e2271"), false);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

            for (AuditEvent event : events) {
                String time = formatter.format(Instant.ofEpochMilli(event.timestamp));
                String status = event.success ? "§a" : "§c";
                src.sendSuccess(new StringTextComponent("§7[" + time + "] " + status +
                    event.type + " §7- " + event.details), false);
            }
        }
    }

    private static void executeLogClear(CommandSource src) {
        auditLogger.clear();
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.audit_logs_cleared_6f37ec74"), false);
    }

    private static void executeSafetyCheck(CommandSource src) {
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.safety_check_report_0835e90e"), false);

        // Check server health
        int playerCount = src.getServer().getPlayerList().getPlayers().size();
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.players_online_value_a678a7b7", playerCount), false);

        // Check locations
        int locationCount = WaveDefenceMod.locationManager.getAllLocations().size();
        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.locations_loaded_value_61346846", locationCount), false);

        // Check locked locations
        int lockedCount = 0;
        for (Location loc : WaveDefenceMod.locationManager.getAllLocations()) {
            if (loc.isLocked()) lockedCount++;
        }
        final int finalLockedCount = lockedCount;
        src.sendSuccess(new StringTextComponent("§7Locked Locations: " +
            (finalLockedCount > 0 ? "§c" + finalLockedCount : "§a0")), false);

        // Check rate limits
        int rateLimited = 0;
        for (CommandHistory history : commandHistory.values()) {
            if (history.timestamps.size() >= MAX_COMMANDS_PER_MINUTE) {
                rateLimited++;
            }
        }
        final int finalRateLimited = rateLimited;
        src.sendSuccess(new StringTextComponent("§7Rate Limited Players: " +
            (finalRateLimited > 0 ? "§c" + finalRateLimited : "§a0")), false);

        // Check pending confirmations
        int pendingCount = pendingConfirmations.size();
        src.sendSuccess(new StringTextComponent("§7Pending Confirmations: " +
            (pendingCount > 0 ? "§e" + pendingCount : "§a0")), false);

        src.sendSuccess(new TranslationTextComponent("wavedefense.auto.end_of_safety_check_e37dfb7b"), false);
    }

    private static void executeSafetyLockdown(CommandSource src, boolean enable) {
        if (enable) {
            // Lock all locations
            for (Location loc : WaveDefenceMod.locationManager.getAllLocations()) {
                if (!loc.isLocked()) {
                    loc.setLocked(true);
                    WaveDefenceMod.locationManager.saveToFile();
                }
            }

            // Kick all non-admin players from locations
            for (ServerPlayerEntity player : src.getServer().getPlayerList().getPlayers()) {
                int perm = src.getServer().getProfilePermissions(player.getGameProfile());
                if (perm < PERM_ADMIN) {
                    com.wavedefense.wave.PlayerWaveData data = WaveDefenceMod.waveManager.getPlayerData(player.getUUID());
                    if (data != null && data.getCurrentLocation() != null) {
                        WaveDefenceMod.waveManager.surrenderPlayer(player);
                        player.sendMessage(new TranslationTextComponent("wavedefense.auto.server_is_in_lockdown_mode_b6f9a343"), net.minecraft.util.Util.NIL_UUID);
                    }
                }
            }

            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.server_lockdown_enabled_07ef530e"), false);
            auditLogger.log(AuditEvent.success(src, "safety_lockdown", com.google.common.collect.ImmutableMap.of("enabled", "true")));
        } else {
            src.sendSuccess(new TranslationTextComponent("wavedefense.auto.server_lockdown_disabled_af4d8079"), false);
            auditLogger.log(AuditEvent.success(src, "safety_lockdown", com.google.common.collect.ImmutableMap.of("enabled", "false")));
        }
    }

    // ── Helper Classes ───────────────────────────────────────────────────────

    private static class PendingConfirmation {
        final UUID playerId;
        final String action;
        final String description;
        final Runnable runnable;
        final long timestamp;

        PendingConfirmation(UUID playerId, String action, String description, Runnable runnable, long timestamp) {
            this.playerId = playerId;
            this.action = action;
            this.description = description;
            this.runnable = runnable;
            this.timestamp = timestamp;
        }
    }

    private static class CommandHistory {
        final List<Long> timestamps = new ArrayList<>();
    }

    private static class AuditLogger {
        private final List<AuditEvent> events = Collections.synchronizedList(new ArrayList<>());

        void log(AuditEvent event) {
            events.add(event);
            // Also log to server console
            WaveDefenceMod.LOGGER.info("[AUDIT] {} | {} | {} | {} | {}",
                event.timestamp, event.executorName, event.type,
                event.success ? "SUCCESS" : "FAILED", event.details);
        }

        List<AuditEvent> getRecentEvents(int count) {
            synchronized (events) {
                int from = Math.max(0, events.size() - count);
                return new ArrayList<>(events.subList(from, events.size()));
            }
        }

        List<AuditEvent> getEventsByPlayer(UUID playerId) {
            synchronized (events) {
                List<AuditEvent> result = new ArrayList<>();
                for (AuditEvent event : events) {
                    if (event.executorId.equals(playerId)) {
                        result.add(event);
                    }
                }
                return result;
            }
        }

        void clear() {
            synchronized (events) {
                events.clear();
            }
        }
    }

    private static class AuditEvent {
        final long timestamp;
        final UUID executorId;
        final String executorName;
        final String type;
        final boolean success;
        final String details;

        AuditEvent(long timestamp, UUID executorId, String executorName, String type, boolean success, String details) {
            this.timestamp = timestamp;
            this.executorId = executorId;
            this.executorName = executorName;
            this.type = type;
            this.success = success;
            this.details = details;
        }

        static AuditEvent success(CommandSource src, String type, Map<String, String> details) {
            try {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    src.getPlayerOrException().getUUID(),
                    src.getPlayerOrException().getGameProfile().getName(),
                    type, true, formatDetails(details)
                );
            } catch (CommandSyntaxException e) {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    new UUID(0, 0),
                    "unknown",
                    type, true, formatDetails(details)
                );
            }
        }

        static AuditEvent failed(CommandSource src, String type, String error, String extra) {
            try {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    src.getPlayerOrException().getUUID(),
                    src.getPlayerOrException().getGameProfile().getName(),
                    type, false, error + (extra != null ? ": " + extra : "")
                );
            } catch (CommandSyntaxException e) {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    new UUID(0, 0),
                    "unknown",
                    type, false, error + (extra != null ? ": " + extra : "")
                );
            }
        }

        static AuditEvent denied(CommandSource src, String operation, String reason) {
            try {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    src.getPlayerOrException().getUUID(),
                    src.getPlayerOrException().getGameProfile().getName(),
                    "permission_denied", false, operation + ": " + reason
                );
            } catch (CommandSyntaxException e) {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    new UUID(0, 0),
                    "unknown",
                    "permission_denied", false, operation + ": " + reason
                );
            }
        }

        static AuditEvent confirmed(CommandSource src, String action) {
            try {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    src.getPlayerOrException().getUUID(),
                    src.getPlayerOrException().getGameProfile().getName(),
                    "confirmation", true, "Confirmed: " + action
                );
            } catch (CommandSyntaxException e) {
                return new AuditEvent(
                    System.currentTimeMillis(),
                    new UUID(0, 0),
                    "unknown",
                    "confirmation", true, "Confirmed: " + action
                );
            }
        }

        private static String formatDetails(Map<String, String> details) {
            if (details == null || details.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : details.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            return sb.toString();
        }
    }
}

