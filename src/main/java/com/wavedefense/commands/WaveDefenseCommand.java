package com.wavedefense.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.config.WaveGameRules;
import com.wavedefense.data.Location;
import com.wavedefense.monitor.WaveDefenseMonitor;
import com.wavedefense.network.packets.AdminTeleportPacket;
import com.wavedefense.network.packets.OpenMenuPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;

public class WaveDefenseCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wavedefense")
                .requires(src -> src.hasPermission(2))

                // /wavedefense menu <гравці> [admin|player]
                .then(Commands.literal("menu")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> openMenuFor(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"), false))
                                .then(Commands.literal("admin")
                                        .executes(ctx -> openMenuFor(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"), true)))
                                .then(Commands.literal("player")
                                        .executes(ctx -> openMenuFor(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"), false)))
                        )
                )

                // /wavedefense tp <локація> <гравці> — телепортує на локацію
                .then(Commands.literal("tp")
                        .then(Commands.argument("location", StringArgumentType.word())
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> {
                                            String locName = StringArgumentType.getString(ctx, "location");
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                            return teleportPlayersToLocation(ctx.getSource(), locName, targets);
                                        })
                                )
                        )
                )

                // /wavedefense kick <гравці> — примусово викидає з локації
                .then(Commands.literal("kick")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> kickPlayersFromLocation(
                                        ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets")))
                        )
                )

                // /wavedefense entry <on|off>
                .then(Commands.literal("entry")
                        .then(Commands.literal("on")
                                .executes(ctx -> setLocationEntry(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setLocationEntry(ctx.getSource(), false)))
                )

                // /wavedefense debug admin <on|off> / log <on|off>
                .then(Commands.literal("debug")
                        .then(Commands.literal("admin")
                                .then(Commands.literal("on").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.set(true);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("wavedefense.auto.повідомлення_відладки_для_адміні_04805f1e"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.set(false);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("wavedefense.auto.повідомлення_відладки_для_адміні_719b97a8"), true);
                                    return 1;
                                }))
                        )
                        .then(Commands.literal("log")
                                .then(Commands.literal("on").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.set(true);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("wavedefense.auto.логування_wavedefense_у_server_l_5d6f563a"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.set(false);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("wavedefense.auto.логування_wavedefense_у_server_l_60f768d6"), true);
                                    return 1;
                                }))
                        )
                        .executes(ctx -> {
                            boolean adminMsg = com.wavedefense.config.WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.get();
                            boolean logEnabled = com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "?7[WaveDefense Debug]\n  ?eadmin messages: ?r" + (adminMsg ? "?aon" : "?coff")
                                    + "\n  ?eserver log: ?r" + (logEnabled ? "?aon" : "?coff")
                            ), false);
                            return 1;
                        })
                )

                 // /wavedefense reload
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            if (WaveDefenseMod.locationManager == null) {
                                ctx.getSource().sendFailure(Component.literal("[WaveDefense] Not yet initialized."));
                                return 0;
                            }
                            WaveDefenseMod.locationManager.loadLocations();
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("wavedefense.auto.локації_перезавантажено_5c281373"), true);
                            return 1;
                        })
                )

                // /wavedefense monitor [summary|detailed|alerts|reset]
                .then(Commands.literal("monitor")
                        .executes(ctx -> showMonitorSummary(ctx.getSource()))
                        .then(Commands.literal("summary")
                                .executes(ctx -> showMonitorSummary(ctx.getSource())))
                        .then(Commands.literal("detailed")
                                .executes(ctx -> showMonitorDetailed(ctx.getSource())))
                        .then(Commands.literal("alerts")
                                .executes(ctx -> showMonitorAlerts(ctx.getSource())))
                        .then(Commands.literal("reset")
                                .executes(ctx -> resetMonitor(ctx.getSource())))
                )

                // /wavedefense stats [location]
                .then(Commands.literal("stats")
                        .executes(ctx -> showGlobalStats(ctx.getSource()))
                        .then(Commands.argument("location", StringArgumentType.word())
                                .executes(ctx -> showLocationStats(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "location"))))
                )
        );

        // /wdmon <гравці> [summary|detailed|alerts] — короткий аліас для monitor
        dispatcher.register(Commands.literal("wdmon")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> showMonitorSummary(ctx.getSource()))
                .then(Commands.literal("summary")
                        .executes(ctx -> showMonitorSummary(ctx.getSource())))
                .then(Commands.literal("detailed")
                        .executes(ctx -> showMonitorDetailed(ctx.getSource())))
                .then(Commands.literal("alerts")
                        .executes(ctx -> showMonitorAlerts(ctx.getSource())))
        );

        // /wdstats [location] — короткий аліас для stats
        dispatcher.register(Commands.literal("wdstats")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> showGlobalStats(ctx.getSource()))
                .then(Commands.argument("location", StringArgumentType.word())
                        .executes(ctx -> showLocationStats(ctx.getSource(),
                                StringArgumentType.getString(ctx, "location"))))
        );

        // /wdm <гравці> [admin] — короткий аліас для menu
        dispatcher.register(Commands.literal("wdm")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> openMenuFor(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets"), false))
                        .then(Commands.literal("admin")
                                .executes(ctx -> openMenuFor(ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"), true)))
                )
        );

        // /wdtp <локація> <гравці> — короткий аліас для tp
        dispatcher.register(Commands.literal("wdtp")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("location", StringArgumentType.word())
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> {
                                    String locName = StringArgumentType.getString(ctx, "location");
                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                    return teleportPlayersToLocation(ctx.getSource(), locName, targets);
                                })
                        )
                )
        );

        // /wdkick <гравці> — короткий аліас для kick
        dispatcher.register(Commands.literal("wdkick")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> kickPlayersFromLocation(
                                ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets")))
                )
        );
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static int openMenuFor(CommandSourceStack source,
                                    Collection<ServerPlayer> players, boolean adminMode) {
        for (ServerPlayer target : players) {
            WaveDefenseMod.packetHandler.send(
                    PacketDistributor.PLAYER.with(() -> target),
                    new OpenMenuPacket(adminMode));
            source.sendSuccess(() -> Component.translatable(
                    "wavedefense.cmd.menu.opened", target.getGameProfile().getName()), false);
        }
        return players.size();
    }

    private static int teleportPlayersToLocation(CommandSourceStack source,
                                                  String locationName,
                                                  Collection<ServerPlayer> players) {
        if (WaveDefenseMod.locationManager == null) {
            source.sendFailure(Component.literal("[WaveDefense] Not yet initialized."));
            return 0;
        }
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null || location.getPlayerSpawn() == null) {
            source.sendFailure(Component.translatable("wavedefense.auto.локація_value_879f640b", locationName + "\" не знайдена або не має точки спавну!"));
            return 0;
        }
        int count = 0;
        for (ServerPlayer target : players) {
            target.removeAllEffects();
            WaveDefenseMod.waveManager.addPlayerToLocation(target, location);
            source.sendSuccess(() -> Component.translatable(
                    "wavedefense.cmd.location.teleport_ok",
                    target.getGameProfile().getName(), locationName), false);
            count++;
        }
        return count;
    }

    /**
     * /wavedefense kick <гравці> — примусово викидає гравців з їх поточної локації.
     * Рівноцінно surrenderPlayer, але ініційовано адміном.
     */
    private static int kickPlayersFromLocation(CommandSourceStack source,
                                                Collection<ServerPlayer> players) {
        int count = 0;
        for (ServerPlayer target : players) {
            com.wavedefense.wave.PlayerWaveData data =
                WaveDefenseMod.waveManager.getPlayerData(target.getUUID());
            if (data == null || data.getCurrentLocation() == null) {
                source.sendFailure(Component.translatable(
                    "wavedefense.cmd.location.not_in", target.getGameProfile().getName()));
                continue;
            }
            String locName = data.getCurrentLocation().getName();
            WaveDefenseMod.waveManager.surrenderPlayer(target);
            target.displayClientMessage(
                Component.translatable("wavedefense.auto.вас_примусово_виведено_з_локації_2ce3049e"), false);
            source.sendSuccess(() -> Component.translatable(
                "wavedefense.cmd.location.kick_ok",
                target.getGameProfile().getName(), locName), true);
            count++;
        }
        return count;
    }

    private static int setLocationEntry(CommandSourceStack source, boolean allowed) {
        WaveGameRules.setLocationEntryAllowed(allowed);
        source.sendSuccess(() -> Component.translatable(
                allowed ? "wavedefense.cmd.location.entry_allowed"
                        : "wavedefense.cmd.location.entry_denied"), true);
        return 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MONITORING COMMAND HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * /wavedefense monitor [summary] — показує зведений моніторинг
     */
    private static int showMonitorSummary(CommandSourceStack source) {
        WaveDefenseMonitor monitor = WaveDefenseMonitor.getInstance();
        String report = monitor.generateSummaryReport();
        for (String line : report.split("\\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * /wavedefense monitor detailed — показує детальний моніторинг
     */
    private static int showMonitorDetailed(CommandSourceStack source) {
        WaveDefenseMonitor monitor = WaveDefenseMonitor.getInstance();
        String report = monitor.generateDetailedReport();
        for (String line : report.split("\\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * /wavedefense monitor alerts — показує активні алерти
     */
    private static int showMonitorAlerts(CommandSourceStack source) {
        WaveDefenseMonitor monitor = WaveDefenseMonitor.getInstance();
        List<WaveDefenseMonitor.Alert> activeAlerts = monitor.getActiveAlerts();

        if (activeAlerts.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wavedefense.auto.немає_активних_алертів_76fd2487"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("§c═══════════════════════════════════════════════════════════════"), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.alerts.header", activeAlerts.size()), false);
        source.sendSuccess(() -> Component.literal("§c═══════════════════════════════════════════════════════════════"), false);

        for (WaveDefenseMonitor.Alert alert : activeAlerts) {
            String severityColor = switch (alert.getSeverity()) {
                case CRITICAL -> "§4";
                case WARNING -> "§e";
                case INFO -> "§9";
            };
            source.sendSuccess(() -> Component.literal(
                severityColor + "[" + alert.getSeverity() + "] §r" + alert.getMessage()
            ), false);
            source.sendSuccess(() -> Component.literal(
                "  §7ID: " + alert.getRuleId()
            ), false);
            source.sendSuccess(() -> Component.translatable("wavedefense.cmd.alerts.time",
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                    .format(java.time.Instant.ofEpochMilli(alert.getTimestamp())
                        .atZone(java.time.ZoneId.systemDefault()))
            ), false);
            source.sendSuccess(() -> Component.literal(""), false);
        }

        source.sendSuccess(() -> Component.literal("§c═══════════════════════════════════════════════════════════════"), false);
        return 1;
    }

    /**
     * /wavedefense monitor reset — скидує моніторинг
     */
    private static int resetMonitor(CommandSourceStack source) {
        // Note: In a production system, you might want to create a new instance
        // For now, we just notify that reset would require a server restart
        source.sendSuccess(() -> Component.translatable("wavedefense.auto.скидання_моніторингу_вимагає_пер_9f1550eb"), false);
        return 1;
    }

    /**
     * /wavedefense stats [location] — показує статистику
     */
    private static int showGlobalStats(CommandSourceStack source) {
        WaveDefenseMonitor monitor = WaveDefenseMonitor.getInstance();
        source.sendSuccess(() -> Component.literal("§e═══════════════════════════════════════════════════════════════"), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.auto.глобальна_статистика_wave_defens_40120440"), false);
        source.sendSuccess(() -> Component.literal("§e═══════════════════════════════════════════════════════════════"), false);
        source.sendSuccess(() -> Component.literal("§7"),
            false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.uptime",
            monitor.formatDuration(monitor.getUptimeSeconds())), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.active_locations",
            monitor.getTotalActiveLocations()), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.players_online",
            WaveDefenseMod.getServer().getPlayerList().getPlayers().size()), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.active_mobs",
            monitor.getTotalActiveMobs()), false);
        source.sendSuccess(() -> Component.literal("§7"), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.waves_completed",
            String.format("%,d", monitor.getTotalWavesCompleted())), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.mobs_killed",
            String.format("%,d", monitor.getTotalMobsKilled())), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.player_deaths",
            String.format("%,d", monitor.getTotalPlayerDeaths())), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.pvp_kills",
            String.format("%,d", monitor.getTotalPvpKills())), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.points_awarded",
            String.format("%,d", monitor.getTotalPointsAwarded())), false);
        source.sendSuccess(() -> Component.literal("§7"), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.avg_tps",
            String.format("%.1f", monitor.getAverageTPS())), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.memory",
            monitor.getUsedMemoryMB(), monitor.getMaxMemoryMB()), false);
        source.sendSuccess(() -> Component.literal("§e═══════════════════════════════════════════════════════════════"), false);
        return 1;
    }

    /**
     * /wavedefense stats <location> — показує статистику локації
     */
    private static int showLocationStats(CommandSourceStack source, String locationName) {
        WaveDefenseMonitor monitor = WaveDefenseMonitor.getInstance();
        WaveDefenseMonitor.LocationHistory history = monitor.getLocationHistories().get(locationName);

        if (history == null) {
            source.sendFailure(Component.translatable("wavedefense.auto.локація_value_879f640b", locationName + "\" не знайдена або не має статистики."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§e═══════════════════════════════════════════════════════════════"), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.auto.статистика_локації_value_14ece11a", locationName), false);
        source.sendSuccess(() -> Component.literal("§e═══════════════════════════════════════════════════════════════"), false);
        source.sendSuccess(() -> Component.literal("§7"), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.waves_completed",
            history.getTotalWavesCompleted()), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.mobs_spawned",
            history.getTotalMobsSpawned()), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.mobs_killed",
            history.getTotalMobsKilled()), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.player_deaths",
            history.getTotalPlayerDeaths()), false);
        source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.points_awarded",
            String.format("%,d", history.getTotalPointsAwarded())), false);
        source.sendSuccess(() -> Component.literal("§7"), false);

        if (!history.getPlayerPoints().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("wavedefense.auto.топ_гравців_за_поінтами_fb8bba14"), false);
            history.getPlayerPoints().entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(entry -> {
                    String playerName = monitor.getPlayerName(entry.getKey());
                    source.sendSuccess(() -> Component.translatable("wavedefense.cmd.stats.top_player",
                        playerName, String.format("%,d", entry.getValue())), false);
                });
        }

        source.sendSuccess(() -> Component.literal("§e═══════════════════════════════════════════════════════════════"), false);
        return 1;
    }
}
