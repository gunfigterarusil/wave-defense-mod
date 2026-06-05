package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;

import com.wavedefense.config.WaveDefenseConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayList;
import java.util.List;

public class TooltipHelper {

    // ── General ────────────────────────────────────────────────────────────
    public static final String SHOP_OPEN        = "wavedefense.tooltip.shop_open";
    public static final String SURRENDER        = "wavedefense.tooltip.surrender";
    public static final String STATS            = "wavedefense.tooltip.stats";
    public static final String HUD_SETTINGS     = "wavedefense.tooltip.hud_settings";
    public static final String KEEP_INVENTORY   = "wavedefense.tooltip.keep_inventory";
    public static final String STARTING_POINTS  = "wavedefense.tooltip.starting_points";
    public static final String LOBBY_TIMER      = "wavedefense.tooltip.lobby_timer";
    public static final String SPAWN_COORDS     = "wavedefense.tooltip.spawn_coords";
    public static final String ENFORCE_GAMEMODE = "wavedefense.tooltip.enforce_gamemode";

    // ── Auto-activation zone ───────────────────────────────────────────────
    public static final String ZONE_ACTIVATE    = "wavedefense.tooltip.zone_activate";
    public static final String ZONE_RADIUS      = "wavedefense.tooltip.zone_radius";

    // ── Location boundary ──────────────────────────────────────────────────
    public static final String BOUNDARY_TIMER    = "wavedefense.tooltip.boundary_timer";
    public static final String BOUNDARY_DAMAGE   = "wavedefense.tooltip.boundary_damage";
    public static final String BOUNDARY_TELEPORT = "wavedefense.tooltip.boundary_teleport";
    public static final String BOUNDARY_INSTANT  = "wavedefense.tooltip.boundary_instant";
    public static final String BOUNDARY_PARTICLES= "wavedefense.tooltip.boundary_particles";

    // ── PvP general ────────────────────────────────────────────────────────
    public static final String PVP_FF           = "wavedefense.tooltip.pvp_ff";
    public static final String PVP_ROUNDS       = "wavedefense.tooltip.pvp_rounds";
    public static final String PVP_BUY_TIME     = "wavedefense.tooltip.pvp_buy_time";
    public static final String PVP_WAIT_EFFECT  = "wavedefense.tooltip.pvp_wait_effect";
    public static final String PVP_AUTO_BALANCE = "wavedefense.tooltip.pvp_auto_balance";
    public static final String PVP_ROUND_DELAY  = "wavedefense.tooltip.pvp_round_delay";
    public static final String PVP_ROUND_POINTS = "wavedefense.tooltip.pvp_round_points";
    public static final String PVP_WIN_POINTS   = "wavedefense.tooltip.pvp_win_points";
    public static final String PVP_LOSE_POINTS  = "wavedefense.tooltip.pvp_lose_points";
    public static final String PVP_SPAWN_RADIUS = "wavedefense.tooltip.pvp_spawn_radius";

    // ── Deathmatch ─────────────────────────────────────────────────────────
    public static final String DM_KILLS_TO_WIN  = "wavedefense.tooltip.dm_kills_to_win";

    // ── Battle Royale ──────────────────────────────────────────────────────
    public static final String BR_BORDER_RADIUS = "wavedefense.tooltip.br_border_radius";
    public static final String BR_SHRINK        = "wavedefense.tooltip.br_shrink";
    public static final String BR_PARTICLE      = "wavedefense.tooltip.br_particle";
    public static final String BR_DAMAGE        = "wavedefense.tooltip.br_damage";
    public static final String BR_RANDOM_SPAWN  = "wavedefense.tooltip.br_random_spawn";

    // ── Mobs ───────────────────────────────────────────────────────────────
    public static final String MOB_ARMOR        = "wavedefense.tooltip.mob_armor";
    public static final String MOB_WEAPON       = "wavedefense.tooltip.mob_weapon";
    public static final String MOB_EFFECT       = "wavedefense.tooltip.mob_effect";
    public static final String MOB_SPAWN_RADIUS = "wavedefense.tooltip.mob_spawn_radius";
    public static final String MOB_SELECT       = "wavedefense.tooltip.mob_select";
    public static final String ITEM_SELECT      = "wavedefense.tooltip.item_select";

    // ── Shop ───────────────────────────────────────────────────────────────
    public static final String SHOP_CATEGORY    = "wavedefense.tooltip.shop_category";
    public static final String SHOP_IMPORT      = "wavedefense.tooltip.shop_import";
    public static final String SHOP_EXPORT      = "wavedefense.tooltip.shop_export";

    // ── Waves ──────────────────────────────────────────────────────────────
    public static final String WAVE_TIMER_BOX      = "wavedefense.tooltip.wave_timer_box";
    public static final String WAVE_IMPORT         = "wavedefense.tooltip.wave_import";
    public static final String WAVE_EXPORT         = "wavedefense.tooltip.wave_export";
    public static final String WAVE_DISCARD        = "wavedefense.tooltip.wave_discard";
    public static final String WAVE_TRIGGER_ONCE   = "wavedefense.tooltip.wave_trigger_once";
    public static final String WAVE_TRIGGER_AND    = "wavedefense.tooltip.wave_trigger_and";

    // ── Location editor misc ───────────────────────────────────────────────
    public static final String SAVE_ALL_SETTINGS   = "wavedefense.tooltip.save_all_settings";
    public static final String SAVE_SETTINGS       = "wavedefense.tooltip.save_settings";
    public static final String MODE_TOGGLE         = "wavedefense.tooltip.mode_toggle";
    public static final String BOUNDARY_ZONE       = "wavedefense.tooltip.boundary_zone";
    public static final String AUTO_TRIGGER        = "wavedefense.tooltip.auto_trigger";
    public static final String PORTAL_RANDOM       = "wavedefense.tooltip.portal_random";
    public static final String MOB_SPAWN_ADD       = "wavedefense.tooltip.mob_spawn_add";

    // ─────────────────────────────────────────────────────────────────────

    public static void renderIfEnabled(MatrixStack g, FontRenderer font,
                                        String key, int mouseX, int mouseY) {
        if (!WaveDefenseConfig.ENABLE_UI_TOOLTIPS.get()) return;
        render(g, font, key, mouseX, mouseY);
    }

    public static void render(MatrixStack g, FontRenderer font,
                               String key, int mouseX, int mouseY) {
        if (key == null || key.trim().isEmpty()) return;
        String text = I18n.get(key);
        String stripped = text.replaceAll("§.", "").trim();
        if (stripped.isEmpty()) return;
        String[] lines = text.split("\\\\n|\n");
        List<ITextComponent> comps = new ArrayList<>();
        for (String line : lines) comps.add(new StringTextComponent(line));
        /* renderComponentTooltip not directly available on 1.16.5 — stub */;
    }
}
