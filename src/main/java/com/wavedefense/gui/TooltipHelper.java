package com.wavedefense.gui;

import com.wavedefense.config.WaveDefenseConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Допоміжний клас для відображення підказок (tooltips) при наведенні.
 * Підказки можна вимкнути в конфігурації (ENABLE_UI_TOOLTIPS = false).
 *
 * Використання:
 *   TooltipHelper.SHOP_OPEN = "Відкрити магазин з товарами";
 *   if (hovering over button) TooltipHelper.render(graphics, font, "...", mx, my);
 */
public class TooltipHelper {

    // ── Стандартні підказки ─────────────────────────────────────────────
    public static final String SHOP_OPEN     = "§7Відкрити магазин з товарами\n§8Гаряча клавіша: §fB (без меню)\n§8V — відкрити головне меню";
    public static final String SURRENDER     = "§7Здатися та покинути поточну локацію\n§c⚠ Ваш прогрес буде втрачено!";
    public static final String STATS         = "§7Переглянути K/D/A статистику команд\n§8Ніки ворогів приховані під час раунду";
    public static final String HUD_SETTINGS  = "§7Налаштування відображення HUD";
    public static final String ZONE_ACTIVATE = "§7Авто-активація: коли гравець фізично\n§8входить у зону → показується таймер\n§8Гравець може вийти щоб скасувати";
    public static final String ZONE_RADIUS   = "§7Радіус зони авто-активації (блоки)";
    public static final String PVP_FF        = "§7Дружній вогонь: гравці однієї команди\n§8можуть завдавати шкоди один одному";
    public static final String PVP_ROUNDS    = "§7Кількість раундів у матчі\n§8Мінімум 1, рекомендовано 10-30";
    public static final String PVP_BUY_TIME  = "§7Час на покупки між раундами (секунди)\n§8Мінімум 5 сек";
    public static final String MOB_ARMOR     = "§7Видати броню мобам цього типу\n§8Вибір через меню предметів";
    public static final String MOB_WEAPON    = "§7Видати зброю мобам (права рука)\n§8Вибір через меню предметів";
    public static final String MOB_EFFECT    = "§7Додати ефекти мобам\n§8Формат: effectId:рівень:тіків";
    public static final String ITEM_SELECT   = "§7Вибрати предмет зі списку\n§8Підтримує всі модові предмети";
    public static final String MOB_SELECT    = "§7Вибрати тип моба\n§8Підтримує всі модові моби";
    public static final String SHOP_CATEGORY    = "§7Фільтрувати товари за категорією";
    public static final String STARTING_POINTS = "§7Стартові поінти — гравці отримують\n§8цю суму при вході на локацію\n§8для покупок в магазині";
    public static final String LOBBY_TIMER     = "§7Таймер лоббі перезапускається\n§8при кожному новому гравцеві.\n§8Коли гравці більше не заходять —\n§8таймер добігає до кінця і гра стартує";
    public static final String KEEP_INVENTORY  = "§7Якщо ВИМКНЕНО: речі гравця замінюються\n§8стартовим спорядженням. §7Речі гравця\n§8ЗАВЖДИ зберігаються і відновлюються\n§8після виходу з локації";
    public static final String SPAWN_COORDS    = "§7Введіть X Y Z координати точки спавну.\n§8Порожнє поле = ваша поточна позиція.\n§8Або натисніть §f📌§8 щоб вставити поточне місце";

    /**
     * Відображає tooltip якщо функція увімкнена в конфігурації.
     * @param tooltipText текст підказки (підтримує §n кольори, \n для переносу)
     */
    public static void renderIfEnabled(GuiGraphics g, Font font,
                                        String tooltipText, int mouseX, int mouseY) {
        if (!WaveDefenseConfig.ENABLE_UI_TOOLTIPS.get()) return;
        render(g, font, tooltipText, mouseX, mouseY);
    }

    public static void render(GuiGraphics g, Font font,
                               String tooltipText, int mouseX, int mouseY) {
        if (tooltipText == null || tooltipText.isBlank()) return;
        // Strip color codes for blank check
        String stripped = tooltipText.replaceAll("§.", "").trim();
        if (stripped.isEmpty()) return;
        String[] lines = tooltipText.split("\\\\n|\n");
        java.util.List<Component> comps = new java.util.ArrayList<>();
        for (String line : lines) comps.add(Component.literal(line));
        g.renderComponentTooltip(font, comps, mouseX, mouseY);
    }
}
