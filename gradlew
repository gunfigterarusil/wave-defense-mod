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

                // /wavedefense tp <локація> <гравець1> [гравець2 ...]
                // Телепортує гравців на локацію від імені адміна (обходить gamerule)
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

                // /wavedefense entry <true|false> — вмикає/вимикає можливість входу для гравців
                .then(Commands.literal("entry")
                        .then(Commands.literal("on")
                                .executes(ctx -> setLocationEntry(ctx.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setLocationEntry(ctx.getSource(), false)))
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

        // Короткий аліас /wdm
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

        // /wdtp <локація> <гравці> — короткий аліас для телепортації
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
    }

    private static int openMenuFor(CommandSourceStack source,
                                    Collection<ServerPlayer> players, boolean adminMode) {
        for (ServerPlayer target : players) {
            WaveDefenseMod.packetHandler.send(
                    PacketDistributor.PLAYER.with(() -> target),
                    new OpenMenuPacket(adminMode)
            );
            source.sendSuccess(() -> Component.literal(
                    "§a✓ Відкрито меню для §e" + target.getGameProfile().getName()), false);
        }
        return players.size();
    }

    private static int teleportPlayersToLocation(CommandSourceStack source,
                                                  String locationName, Collection<ServerPlayer> players) {
        Location location = WaveDefenseMod.locationManager.getLocation(locationName);
        if (location == null || location.getPlayerSpawn() == null) {
            source.sendFailure(Component.literal("§cЛокація \"" + locationName + "\" не знайдена або не має точки спавну!"));
            return 0;
        }
        int count = 0;
        for (ServerPlayer target : players) {
            target.removeAllEffects();
            WaveDefenseMod.waveManager.addPlayerToLocation(target, location);
            source.sendSuccess(() -> Component.literal(
                    "§a✓ §e" + target.getGameProfile().getName() + " §aвідправлений на §6" + locationName), false);
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
