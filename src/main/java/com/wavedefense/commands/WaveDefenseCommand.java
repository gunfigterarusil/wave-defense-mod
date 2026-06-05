package com.wavedefense.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.wavedefense.WaveDefenceMod;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;

import java.util.List;

/**
 * Player-facing {@code /wd} command (1.16.5 port).
 *
 * <p>Phase 1 v0.0.1: only {@code /wd list} works — prints all loaded
 * locations. Used as smoke-test for the foundation pass.
 *
 * <p>1.16.5 port notes:
 * <ul>
 *   <li>{@code CommandSourceStack} → {@code CommandSource}</li>
 *   <li>{@code Component.literal} → {@code new StringTextComponent}</li>
 *   <li>{@code src.sendSuccess(Supplier, boolean)} → {@code src.sendSuccess(ITextComponent, boolean)}
 *       — no Supplier wrapper in 1.16.5; pass component directly.</li>
 * </ul>
 */
public final class WaveDefenseCommand {
    private WaveDefenseCommand() {}

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(
            Commands.literal("wd")
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (WaveDefenceMod.locationManager == null) {
                        src.sendFailure(new StringTextComponent("§c✗ LocationManager not ready"));
                        return 0;
                    }
                    List<String> names = WaveDefenceMod.locationManager.getAllLocationNames();
                    if (names.isEmpty()) {
                        src.sendSuccess(new StringTextComponent("§7No locations defined"), false);
                    } else {
                        src.sendSuccess(new StringTextComponent(
                            "§eLocations (" + names.size() + "): §f" + String.join(", ", names)), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("version").executes(ctx -> {
                    ctx.getSource().sendSuccess(new StringTextComponent(
                        "§eWave Defence §f1.16.5-0.0.1 §7(Phase 1 stub)"), false);
                    return 1;
                }))
        );
    }
}
