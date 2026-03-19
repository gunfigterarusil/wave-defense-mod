package com.wavedefense.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.wavedefense.WaveDefenseMod;
import com.wavedefense.config.WaveGameRules;
import com.wavedefense.data.Location;
import com.wavedefense.network.packets.AdminTeleportPacket;
import com.wavedefense.network.packets.OpenMenuPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

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
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§a✓ Повідомлення відладки для адмінів УВІМКНЕНО"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.set(false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§7Повідомлення відладки для адмінів ВИМКНЕНО"), true);
                                    return 1;
                                }))
                        )
                        .then(Commands.literal("log")
                                .then(Commands.literal("on").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.set(true);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§a✓ Логування WaveDefense у server.log УВІМКНЕНО"), true);
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.set(false);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§7Логування WaveDefense у server.log ВИМКНЕНО"), true);
                                    return 1;
                                }))
                        )
                        .executes(ctx -> {
                            boolean adminMsg = com.wavedefense.config.WaveDefenseConfig.DEBUG_ADMIN_MESSAGES.get();
                            boolean logEnabled = com.wavedefense.config.WaveDefenseConfig.DEBUG_LOGGING_ENABLED.get();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§7[WaveDefense Debug]\n  §eadmin messages: §r" + (adminMsg ? "§aon" : "§coff")
                                + "\n  §eserver log: §r" + (logEnabled ? "§aon" : "§coff")
                            ), false);
                            return 1;
                        })
                )

                // /wavedefense reload
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            WaveDefenseMod.locationManager.loadLocations();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a✓ Локації перезавантажено!"), true);
                            return 1;
                        })
                )
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
            source.sendSuccess(() -> Component.literal(
                    "§a✓ Відкрито меню для §e" + target.getGameProfile().getName()), false);
        }
        return players.size();
    }

    private static int teleportPlayersToLocation(CommandSourceStack source,
                                                  String locationName,
                                                  Collection<ServerPlayer> players) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null || location.getPlayerSpawn() == null) {
            source.sendFailure(Component.literal(
                "§cЛокація \"" + locationName + "\" не знайдена або не має точки спавну!"));
            return 0;
        }
        int count = 0;
        for (ServerPlayer target : players) {
            target.removeAllEffects();
            WaveDefenseMod.waveManager.addPlayerToLocation(target, location);
            source.sendSuccess(() -> Component.literal(
                    "§a✓ §e" + target.getGameProfile().getName()
                    + " §aвідправлений на §6" + locationName), false);
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
                source.sendFailure(Component.literal(
                    "§e" + target.getGameProfile().getName()
                    + " §cне знаходиться на жодній локації."));
                continue;
            }
            String locName = data.getCurrentLocation().getName();
            WaveDefenseMod.waveManager.surrenderPlayer(target);
            target.displayClientMessage(
                Component.literal("§c⚠ Вас примусово виведено з локації адміністратором."), false);
            source.sendSuccess(() -> Component.literal(
                "§a✓ §e" + target.getGameProfile().getName()
                + " §aвиведено з локації §6" + locName), true);
            count++;
        }
        return count;
    }

    private static int setLocationEntry(CommandSourceStack source, boolean allowed) {
        WaveGameRules.setLocationEntryAllowed(allowed);
        String status = allowed ? "§aувімкнено" : "§cвимкнено";
        source.sendSuccess(() -> Component.literal("§7Вхід на локацію: " + status), true);
        return 1;
    }
}
