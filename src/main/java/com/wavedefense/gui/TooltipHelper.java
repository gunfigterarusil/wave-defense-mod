package com.wavedefense.gui;

import com.wavedefense.config.WaveDefenseConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Підказки (tooltips) для всіх меню мода.
 * Вмикаються/вимикаються через конфігурацію: ENABLE_UI_TOOLTIPS.
 */
public class TooltipHelper {

    // ── Загальні ───────────────────────────────────────────────────────────
    public static final String SHOP_OPEN        = "§7Відкрити магазин з товарами\n§8Гаряча клавіша: §fB §8(без меню)\n§8V — головне меню";
    public static final String SURRENDER        = "§7Здатися та покинути локацію\n§c⚠ Прогрес буде втрачено!";
    public static final String STATS            = "§7K/D/A статистика команд\n§8Ніки ворогів приховані під час раунду";
    public static final String HUD_SETTINGS     = "§7Налаштування відображення HUD";
    public static final String KEEP_INVENTORY   = "§7Якщо ВИМКНЕНО: речі замінюються стартовим\n§8екіпом при вході. Завжди зберігаються\n§8і відновлюються після виходу";
    public static final String STARTING_POINTS  = "§7Поінти для покупок в магазині\n§8при вході на локацію";
    public static final String LOBBY_TIMER      = "§7Скидається при кожному новому гравцеві.\n§8Після закінчення — гра стартує";
    public static final String SPAWN_COORDS     = "§7X Y Z точки спавну.\n§8Порожнє = ваша позиція.\n§8📌 — вставити поточне місце";
    public static final String ENFORCE_GAMEMODE = "§7Увімкнено: Creative → Survival/Adventure\n§8при вході і кожну секунду під час гри";

    // ── Зона авто-активації ────────────────────────────────────────────────
    public static final String ZONE_ACTIVATE    = "§7Гравець входить у зону → таймер → старт\n§8Вихід зі зони скасовує таймер";
    public static final String ZONE_RADIUS      = "§7Радіус зони авто-активації (блоки)";

    // ── Кордон локації ─────────────────────────────────────────────────────
    public static final String BOUNDARY_TIMER   = "§7Гравець отримує таймер (title)\n§8Якщо не повернувся — здається";
    public static final String BOUNDARY_DAMAGE  = "§7Гравець отримує шкоду кожну секунду\n§8поки знаходиться поза кордоном";
    public static final String BOUNDARY_TELEPORT= "§7Миттєва телепортація назад\n§8до точки спавну локації";
    public static final String BOUNDARY_INSTANT = "§7Миттєва здача без попередження";
    public static final String BOUNDARY_PARTICLES="§7Візуалізація кордону частинками.\n§8Тип: registry id (напр. minecraft:flame)\n§8Висота: скільки блоків угору";

    // ── PvP загальне ───────────────────────────────────────────────────────
    public static final String PVP_FF           = "§7Friendly Fire: союзники б'ють союзників";
    public static final String PVP_ROUNDS       = "§7Кількість раундів у матчі\n§8Мін. 1, рекомендовано 10-30";
    public static final String PVP_BUY_TIME     = "§7Час на покупки між раундами (сек)\n§8Мін. 5 сек";
    public static final String PVP_WAIT_EFFECT  = "§7Slowness 127 + Blindness замість spectator\n§8під час очікування старту.\n§8Гравці не можуть рухатись і нічого не бачать";
    public static final String PVP_AUTO_BALANCE = "§7Нові гравці → менша команда.\n§8При виході в WAITING → перебаланс";
    public static final String PVP_ROUND_DELAY  = "§7Відлік перед ACTIVE після BUY-фази\n§8(0 = старт одразу)";
    public static final String PVP_ROUND_POINTS = "§7Поінти кожному гравцю на початку раунду\n§8(додаються до стартових)";
    public static final String PVP_WIN_POINTS   = "§7Поінти команді-переможцю за раунд";
    public static final String PVP_LOSE_POINTS  = "§7Поінти команді що програла раунд\n§8(заохочення за участь)";
    public static final String PVP_SPAWN_RADIUS = "§7Радіус розкиду гравців навколо точки\n§80 = точно на блоці";

    // ── Deathmatch ─────────────────────────────────────────────────────────
    public static final String DM_KILLS_TO_WIN  = "§7Скільки вбивств команді потрібно\n§8для перемоги у раунді.\n§8Після смерті гравець одразу відроджується";

    // ── Battle Royale ──────────────────────────────────────────────────────
    public static final String BR_BORDER_RADIUS = "§7Початковий радіус зони безпеки (блоки)\n§8Центр = точка спавну гравця";
    public static final String BR_SHRINK        = "§7Кожні N секунд кордон зменшується на 1 блок";
    public static final String BR_PARTICLE      = "§7Registry id частинок кордону\n§8Приклади: minecraft:flame, minecraft:portal,\n§8minecraft:end_rod, minecraft:snowflake";
    public static final String BR_DAMAGE        = "§7Шкода за вихід за кордон (HP/сек)\n§80 = тільки частинки без шкоди";
    public static final String BR_RANDOM_SPAWN  = "§7У Battle Royale точка спавну\n§8обирається випадково автоматично";

    // ── Моби ───────────────────────────────────────────────────────────────
    public static final String MOB_ARMOR        = "§7Броня для мобів цього типу\n§8Вибір через меню предметів";
    public static final String MOB_WEAPON       = "§7Зброя для мобів (права рука)\n§8Вибір через меню предметів";
    public static final String MOB_EFFECT       = "§7Ефекти для мобів\n§8Формат: effectId:рівень:тіків";
    public static final String MOB_SPAWN_RADIUS = "§7Радіус розкиду мобів навколо точки спавну\n§80 = точно на блоці\n§8Цикл: 0→3→5→10→15→20";
    public static final String MOB_SELECT       = "§7Вибрати тип моба\n§8Підтримує всі модові моби";
    public static final String ITEM_SELECT      = "§7Вибрати предмет зі списку\n§8Підтримує всі модові предмети";

    // ── Магазин ────────────────────────────────────────────────────────────
    public static final String SHOP_CATEGORY    = "§7Фільтрувати товари за категорією";
    public static final String SHOP_IMPORT      = "§7Завантажити конфігурацію магазину з .nbt файлу\n§8Файли у: world/wavedefense/shop_export/";
    public static final String SHOP_EXPORT      = "§7Зберегти конфігурацію магазину у .nbt файл\n§8Для використання в інших локаціях";

    // ── Хвилі ──────────────────────────────────────────────────────────────
    public static final String WAVE_TIMER_BOX   = "§7Час між хвилями (секунди)\n§8Хвиля №1: затримка перед першою хвилею після лоббі";
    public static final String WAVE_IMPORT      = "§7Завантажити хвилі з .nbt файлу\n§8Файли у: world/wavedefense/wave_export/";
    public static final String WAVE_EXPORT      = "§7Зберегти хвилі у .nbt файл\n§8Для використання в інших локаціях";
    public static final String WAVE_DISCARD     = "§7Повернутись до списку хвиль\n§cЗміни мобів НЕ будуть збережені";

    // ─────────────────────────────────────────────────────────────────────

    /**
     * Відображає tooltip якщо функція увімкнена у конфігурації.
     */
    public static void renderIfEnabled(GuiGraphics g, Font font,
                                        String text, int mouseX, int mouseY) {
        if (!WaveDefenseConfig.ENABLE_UI_TOOLTIPS.get()) return;
        render(g, font, text, mouseX, mouseY);
    }

    public static void render(GuiGraphics g, Font font,
                               String text, int mouseX, int mouseY) {
        if (text == null || text.isBlank()) return;
        String stripped = text.replaceAll("§.", "").trim();
        if (stripped.isEmpty()) return;
        String[] lines = text.split("\\\\n|\n");
        List<Component> comps = new ArrayList<>();
        for (String line : lines) comps.add(Component.literal(line));
        g.renderComponentTooltip(font, comps, mouseX, mouseY);
    }
}
