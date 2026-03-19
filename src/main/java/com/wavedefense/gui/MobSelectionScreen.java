package com.wavedefense.gui;

import com.wavedefense.data.WaveConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Меню вибору моба.
 * ✓ Підсвічення вже-вибраного моба золотою рамкою
 * ✓ Пошук виправлено — зберігає стан між rebuildWidgets
 * ✓ Адаптивний розмір під будь-яке розширення
 * ✓ 3D-превʼю з InventoryScreen.renderEntityInInventory
 */
public class MobSelectionScreen extends Screen {

    public enum MobCategory2 {
        ALL("Всі"), FLYING("🦅 Летючі"), UNDEAD("💀 Мерці"),
        CREATURE("🐾 Живі"), OTHER("❓ Інші");
        public final String label;
        MobCategory2(String l) { this.label = l; }
    }

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onSelect;
    private WaveConfig waveConfig;
    private int mobIndex;

    // Поточний вибраний моб (для підсвічення)
    private final ResourceLocation currentMobId;

    private List<EntityType<?>> allMobs;
    private List<EntityType<?>> filteredMobs;
    private MobCategory2 currentCategory = MobCategory2.ALL;
    private String searchQuery = "";
    private int scrollOffset  = 0;
    private EditBox searchBox;

    private EntityType<?> hoveredType = null;
    private float previewAngle = 0f;

    // Адаптивні (перераховуються в init)
    private int PREVIEW_W = 68;
    private int ROW_H     = 22;
    private int LIST_Y    = 62;

    /** Конструктор з поточним мобом для підсвічення */
    public MobSelectionScreen(Screen parent, Consumer<ResourceLocation> onSelect, ResourceLocation currentMobId) {
        super(Component.literal("Вибір моба"));
        this.parentScreen = parent;
        this.onSelect     = onSelect;
        this.currentMobId = currentMobId;
        loadMobs();
    }

    /** Без поточного вибору */
    public MobSelectionScreen(Screen parent, Consumer<ResourceLocation> onSelect) {
        this(parent, onSelect, null);
    }

    /** Legacy WaveConfig compat */
    public MobSelectionScreen(Screen parentScreen, WaveConfig waveConfig, int mobIndex) {
        super(Component.literal("Вибір моба"));
        this.parentScreen = parentScreen;
        this.waveConfig   = waveConfig;
        this.mobIndex     = mobIndex;
        this.onSelect     = null;
        this.currentMobId = (waveConfig != null && mobIndex >= 0 && mobIndex < waveConfig.getMobs().size())
                ? waveConfig.getMobs().get(mobIndex).getMobType() : null;
        loadMobs();
    }

    private void loadMobs() {
        allMobs = ForgeRegistries.ENTITY_TYPES.getValues().stream()
                .filter(t -> !t.getCategory().isFriendly() || t.getCategory() == MobCategory.CREATURE)
                .sorted(Comparator.comparing(t -> t.getDescription().getString()))
                .collect(Collectors.toList());
        filteredMobs = new ArrayList<>(allMobs);
    }

    @Override
    protected void init() {
        super.init();
        // ── Адаптивні параметри ──────────────────────────────────────
        PREVIEW_W = Math.max(55, Math.min(90, this.width / 8));
        ROW_H     = this.height < 200 ? 18 : 22;
        LIST_Y    = 62;

        int cx = this.width / 2;

        // Пошук — зберігаємо значення
        searchBox = new EditBox(this.font, cx - 80, 25, 160, 16, Component.literal("Пошук..."));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(s -> {
            searchQuery = s;
            scrollOffset = 0;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("← Назад"), b -> this.minecraft.setScreen(parentScreen)
        ).bounds(this.width - 72, 25, 68, 16).build());

        buildCategoryButtons();
        buildMobList();
    }

    private void buildCategoryButtons() {
        MobCategory2[] cats = MobCategory2.values();
        int catW  = Math.max(50, (this.width - PREVIEW_W - 20) / cats.length - 2);
        int catX  = PREVIEW_W + 4;
        for (MobCategory2 cat : cats) {
            final MobCategory2 c = cat;
            boolean active = (cat == currentCategory);
            long cnt = (cat == MobCategory2.ALL) ? allMobs.size()
                    : allMobs.stream().filter(t -> matchesCategoryFor(t, cat)).count();
            String lbl = (active ? "§e§l" : "§7") + cat.label + " §8(" + cnt + ")";
            this.addRenderableWidget(Button.builder(
                    Component.literal(lbl),
                    b -> { currentCategory = c; scrollOffset = 0; applyFilter(); }
            ).bounds(catX, 44, catW, 14).build());
            catX += catW + 2;
        }
    }

    private void applyFilter() {
        String q = searchQuery.toLowerCase();
        filteredMobs = allMobs.stream()
                .filter(t -> matchesCategoryFor(t, currentCategory))
                .filter(t -> {
                    if (q.isEmpty()) return true;
                    ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(t);
                    String regId = key != null ? key.toString() : "";
                    return t.getDescription().getString().toLowerCase().contains(q)
                        || regId.toLowerCase().contains(q);
                })
                .collect(Collectors.toList());
        rebuildWidgets();
    }

    private boolean matchesCategoryFor(EntityType<?> t, MobCategory2 cat) {
        MobCategory mc = t.getCategory();
        return switch (cat) {
            case ALL      -> true;
            case FLYING   -> mc == MobCategory.AMBIENT || isFlying(t);
            case UNDEAD   -> isUndead(t);
            case CREATURE -> mc == MobCategory.CREATURE;
            case OTHER    -> !isFlying(t) && !isUndead(t) && mc != MobCategory.CREATURE;
        };
    }

    private boolean isFlying(EntityType<?> t) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(t);
        if (key == null) return false;
        String p = key.getPath();
        return p.contains("bat") || p.contains("bee") || p.contains("blaze") ||
               p.contains("ghast") || p.contains("phantom") || p.contains("parrot") ||
               p.contains("vex") || p.contains("allay") || p.contains("dragon");
    }

    private boolean isUndead(EntityType<?> t) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(t);
        if (key == null) return false;
        String p = key.getPath();
        return p.contains("zombie") || p.contains("skeleton") || p.contains("wither") ||
               p.contains("phantom") || p.contains("drowned") || p.contains("husk") ||
               p.contains("stray") || p.contains("zoglin") || p.contains("zombie_villager");
    }

    private int getItemsPerPage() {
        return Math.max(3, (this.height - LIST_Y - 30) / ROW_H);
    }

    private void buildMobList() {
        // Зберігаємо searchBox
        clearWidgets();
        if (searchBox != null) this.addRenderableWidget(searchBox);

        this.addRenderableWidget(Button.builder(
                Component.literal("← Назад"), b -> this.minecraft.setScreen(parentScreen)
        ).bounds(this.width - 72, 25, 68, 16).build());

        buildCategoryButtons();

        int listX     = PREVIEW_W + 4;
        int btnW      = this.width - listX - 28;
        int ipp       = getItemsPerPage();

        for (int i = 0; i < Math.min(ipp, filteredMobs.size()); i++) {
            int index      = i + scrollOffset;
            if (index >= filteredMobs.size()) break;
            EntityType<?> et = filteredMobs.get(index);
            String mobName   = et.getDescription().getString();
            ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(et);

            // Підсвічення вже-вибраного — золотий префікс
            boolean isSelected = currentMobId != null && currentMobId.equals(mobId);
            String modPrefix   = (mobId != null && !mobId.getNamespace().equals("minecraft"))
                    ? "§8[" + mobId.getNamespace() + "] §r" : "";
            String lbl = isSelected
                    ? "§6§l▶ " + modPrefix + mobName
                    : modPrefix + mobName;

            int yPos = LIST_Y + i * ROW_H;
            final EntityType<?> fet = et;

            Button btn = Button.builder(
                    Component.literal(lbl),
                    button -> selectMob(fet)
            ).bounds(listX, yPos, btnW, ROW_H - 2).build();

            this.addRenderableWidget(btn);
        }

        // Скрол
        if (filteredMobs.size() > ipp) {
            int sbX = this.width - 24;
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (scrollOffset > 0) { scrollOffset--; buildMobList(); } }
            ).bounds(sbX, LIST_Y, 20, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { if (scrollOffset + ipp < filteredMobs.size()) { scrollOffset++; buildMobList(); } }
            ).bounds(sbX, LIST_Y + (ipp - 1) * ROW_H, 20, 20).build());

            String counter = (scrollOffset + 1) + "-"
                    + Math.min(scrollOffset + ipp, filteredMobs.size())
                    + "/" + filteredMobs.size();
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7" + counter), b -> {}
            ).bounds(sbX - 20, LIST_Y + (ipp / 2) * ROW_H, 44, ROW_H - 2).build()).active = false;
        }
    }

    private void selectMob(EntityType<?> entityType) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        if (mobId == null) return;
        if (onSelect != null) {
            onSelect.accept(mobId);
        } else if (waveConfig != null) {
            waveConfig.getMobs().get(mobIndex).setMobType(mobId);
        }
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        previewAngle = (previewAngle + 0.5f) % 360f;
        renderMobPreview(g, mouseX, mouseY);

        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // Лічильник з підказкою про пошук
        String counter = "§7" + filteredMobs.size() + " мобів";
        if (!searchQuery.isEmpty()) counter += " §8(\"" + searchQuery + "\")";
        g.drawString(this.font, counter, PREVIEW_W + 4, this.height - 12, 0xAAAAAA);

        // Scissor: список мобів між категоріями (LIST_Y=62) і лічильником (height-20)
        int listTop = LIST_Y, listBot = this.height - 20;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getY() + w.getHeight() > listTop && w.getY() < listBot
                    && w.getX() >= PREVIEW_W)   // не чіпаємо preview панель
                w.render(g, mouseX, mouseY, partialTick);
        }
        ScissorHelper.disable();
        // Static widgets: пошук, "← Назад", кнопки категорій (вгорі)
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && (w.getY() < listTop || w.getY() >= listBot))
                w.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderMobPreview(GuiGraphics g, int mouseX, int mouseY) {
        int px = 2, py = 44;
        int pw = PREVIEW_W - 4;
        int ph = this.height - py - 24;

        g.fill(px, py, px + pw, py + ph, 0xFF1E1E1E);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF111111);

        // Визначаємо hoveredType
        int listX = PREVIEW_W + 4;
        int ipp   = getItemsPerPage();
        hoveredType = null;
        for (int i = 0; i < Math.min(ipp, filteredMobs.size()); i++) {
            int idx  = i + scrollOffset;
            int yPos = LIST_Y + i * ROW_H;
            if (mouseY >= yPos && mouseY < yPos + ROW_H && mouseX >= listX) {
                hoveredType = filteredMobs.get(idx);
                break;
            }
        }

        EntityType<?> display = hoveredType != null ? hoveredType
                : (currentMobId != null
                    ? allMobs.stream()
                        .filter(t -> currentMobId.equals(ForgeRegistries.ENTITY_TYPES.getKey(t)))
                        .findFirst().orElse(filteredMobs.isEmpty() ? null : filteredMobs.get(0))
                    : (filteredMobs.isEmpty() ? null
                        : filteredMobs.get(Math.min(scrollOffset, filteredMobs.size() - 1))));

        if (display == null) return;

        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(display);
        boolean isCurrent    = currentMobId != null && currentMobId.equals(key);

        // Назва з золотим префіксом якщо це поточний
        String fullName  = display.getDescription().getString();
        String shortName = fullName.length() > 10 ? fullName.substring(0, 9) + "…" : fullName;
        String nameColor = isCurrent ? "§6§l" : "§e";
        String modId     = (key != null && !key.getNamespace().equals("minecraft"))
                ? key.getNamespace() : "minecraft";

        g.drawCenteredString(this.font, nameColor + shortName, px + pw / 2, py + 4, 0xFFFFFF);
        g.drawCenteredString(this.font, "§8" + modId, px + pw / 2, py + 13, 0x888888);

        // Золота рамка навколо превʼю-панелі якщо це поточний вибраний
        if (isCurrent) {
            g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFFFFAA00);
            g.fill(px, py, px + pw, py + ph, 0xFF1E1E1E);
            g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF111111);
        }

        // 3D-превʼю
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                net.minecraft.world.entity.Entity entity = display.create(mc.level);
                if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    int cx = px + pw / 2;
                    int cy = py + ph / 2 + 15; // Підняти точку origin моба
                    float scale = Math.min(pw * 0.8f, (ph - 30) * 0.8f)
                            / Math.max(1f, (float) living.getBbHeight());
                    scale = Math.min(scale, 50f);

                    // Vanilla InventoryScreen використовує:
                    //   mouseAngle = rotateZ(PI)           — перевертає entity правильно
                    //   cameraAngle = rotateX(PI/4)        — нахил камери (45°)
                    // Для spinning preview: додаємо rotateY до першого кватерніона
                    org.joml.Quaternionf rotation = new org.joml.Quaternionf()
                            .rotateZ((float) Math.PI)                     // обов'язковий flip
                            .rotateY((float) Math.toRadians(previewAngle)); // обертання для preview
                    // cameraAngle — точно як у vanilla InventoryScreen
                    org.joml.Quaternionf initial = new org.joml.Quaternionf()
                            .rotateX((float)(Math.PI / 4.0));             // нахил 45° (vanilla)
                    // renderY = де стоять ноги entity (entity малюється ВГОРУ від цієї точки)
                    int entityH = (int)(living.getBbHeight() * scale);
                    int renderY = cy + (int)(entityH * 0.4f); // ноги трохи нижче центру панелі
                    net.minecraft.client.gui.screens.inventory.InventoryScreen
                            .renderEntityInInventory(g, cx, renderY, (int) scale, rotation, initial, living);
                }
            }
        } catch (Exception ignored) {
            g.drawCenteredString(this.font, "§7[no preview]", px + pw / 2, py + ph / 2, 0x666666);
        }

        g.drawCenteredString(this.font, "§8" + display.getCategory().name().toLowerCase(),
                px + pw / 2, py + ph - 14, 0x666666);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int ipp = getItemsPerPage();
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; buildMobList(); }
        else if (delta < 0 && scrollOffset + ipp < filteredMobs.size()) { scrollOffset++; buildMobList(); }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
