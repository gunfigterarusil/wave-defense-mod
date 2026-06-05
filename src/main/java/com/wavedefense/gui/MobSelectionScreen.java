package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.WaveConfig;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EntityClassification;
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
public class MobSelectionScreen extends ScrollableScreen {

    public enum MobCategory2 {
        ALL("wavedefense.mob.category.all"), FLYING("wavedefense.mob.category.flying"),
        UNDEAD("wavedefense.mob.category.undead"), CREATURE("wavedefense.mob.category.creature"),
        OTHER("wavedefense.mob.category.other");
        public final String label;
        MobCategory2(String l) { this.label = l; }
    }

    private final Screen parentScreen;
    private final Consumer<ResourceLocation> onSelect;
    private WaveConfig waveConfig;
    private int mobIndex;

    private final ResourceLocation currentMobId;

    private List<EntityType<?>> allMobs;
    private List<EntityType<?>> filteredMobs;
    private MobCategory2 currentCategory = MobCategory2.ALL;
    private String searchQuery = "";
    private TextFieldWidget searchBox;

    private EntityType<?> hoveredType = null;
    private float previewAngle = 0f;

    // Adaptive (recalculated in init)
    private int PREVIEW_W = 68;
    private int ROW_H     = 22;
    private int LIST_Y    = 62;

    /** Конструктор з поточним мобом для підсвічення */
    public MobSelectionScreen(Screen parent, Consumer<ResourceLocation> onSelect, ResourceLocation currentMobId) {
        super(new TranslationTextComponent("wavedefense.title.mob_selection"));
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
        super(new TranslationTextComponent("wavedefense.title.mob_selection"));
        this.parentScreen = parentScreen;
        this.waveConfig   = waveConfig;
        this.mobIndex     = mobIndex;
        this.onSelect     = null;
        this.currentMobId = (waveConfig != null && mobIndex >= 0 && mobIndex < waveConfig.getMobs().size())
                ? waveConfig.getMobs().get(mobIndex).getMobType() : null;
        loadMobs();
    }

    private void loadMobs() {
        allMobs = ForgeRegistries.ENTITIES.getValues().stream()
                .filter(t -> !t.getCategory().isFriendly() || t.getCategory() == EntityClassification.CREATURE)
                .sorted(Comparator.comparing(t -> ((net.minecraft.entity.EntityType<?>) t).getDescription().getString()))
                .collect(java.util.stream.Collectors.toList());
        filteredMobs = new ArrayList<>(allMobs);
    }

    // ─── ScrollableScreen API ──────────────────────────────────────────

    @Override protected int getClipTop() { return LIST_Y; }
    @Override protected int getClipBot() { return this.height - 20; }
    @Override protected int getListSize() { return filteredMobs.size(); }
    @Override protected int getItemsPerPage() { return Math.max(3, (this.height - LIST_Y - 30) / ROW_H); }

    // ─── init() ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Adaptive params
        PREVIEW_W = Math.max(55, Math.min(90, this.width / 8));
        ROW_H     = this.height < 200 ? 18 : 22;
        LIST_Y    = 62;

        super.init();

        int cx = this.width / 2;

        // ── Header (static) — search box, back button, categories ───
        searchBox = new TextFieldWidget(this.font, cx - 80, 25, 160, 16, new TranslationTextComponent("wavedefense.label.search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(s -> {
            searchQuery = s;
            scrollOffset = 0;
            applyFilter();
        });
        addStatic(searchBox);
        this.setInitialFocus(searchBox);

        addStatic(new Button(this.width - 72, 25, 68, 16, new TranslationTextComponent("wavedefense.button.back"), b -> this.minecraft.setScreen(parentScreen)));

        buildCategoryButtons();

        // ── Content (scrollable) — mob list ─────────────────────────
        buildMobListContent();
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
            String lbl = (active ? "§e§l" : "§7") + I18n.get(cat.label) + " §8(" + cnt + ")";
            addStatic(new Button(catX, 44, catW, 14, new StringTextComponent(lbl), b -> { currentCategory = c; scrollOffset = 0; applyFilter(); }));
            catX += catW + 2;
        }
    }

    private void buildMobListContent() {
        int listX = PREVIEW_W + 4;
        int btnW  = this.width - listX - 28;
        int ipp   = getItemsPerPage();

        for (int i = 0; i < Math.min(ipp, filteredMobs.size()); i++) {
            int index = i + scrollOffset;
            if (index >= filteredMobs.size()) break;
            EntityType<?> et = filteredMobs.get(index);
            String mobName   = et.getDescription().getString();
            ResourceLocation mobId = ForgeRegistries.ENTITIES.getKey(et);

            boolean isSelected = currentMobId != null && currentMobId.equals(mobId);
            String modPrefix   = (mobId != null && !mobId.getNamespace().equals("minecraft"))
                    ? "§8[" + mobId.getNamespace() + "] §r" : "";
            String lbl = isSelected
                    ? "§6§l▶ " + modPrefix + mobName
                    : modPrefix + mobName;

            int yPos = LIST_Y + i * ROW_H;
            final EntityType<?> fet = et;

            this.addButton(new Button(listX, yPos, btnW, ROW_H - 2, new StringTextComponent(lbl), button -> selectMob(fet)));
        }

        // Scroll buttons + counter
        if (filteredMobs.size() > ipp) {
            int sbX = this.width - 24;
            addStatic(new Button(sbX, LIST_Y, 20, 20, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); } }));
            addStatic(new Button(sbX, LIST_Y + (ipp - 1) * ROW_H, 20, 20, new StringTextComponent("▼"), b -> { if (scrollOffset + ipp < filteredMobs.size()) { scrollOffset++; init(); } }));

            String counter = (scrollOffset + 1) + "-"
                    + Math.min(scrollOffset + ipp, filteredMobs.size())
                    + "/" + filteredMobs.size();
            this.addButton(new Button(sbX - 20, LIST_Y + (ipp / 2) * ROW_H, 44, ROW_H - 2, new StringTextComponent("§7" + counter), b -> {})).active = false;
        }
    }

    private void applyFilter() {
        String q = searchQuery.toLowerCase();
        filteredMobs = allMobs.stream()
                .filter(t -> matchesCategoryFor(t, currentCategory))
                .filter(t -> {
                    if (q.isEmpty()) return true;
                    ResourceLocation key = ForgeRegistries.ENTITIES.getKey(t);
                    String regId = key != null ? key.toString() : "";
                    return t.getDescription().getString().toLowerCase().contains(q)
                        || regId.toLowerCase().contains(q);
                })
                .collect(java.util.stream.Collectors.toList());
        init();
    }

    private boolean matchesCategoryFor(EntityType<?> t, MobCategory2 cat) {
        EntityClassification mc = t.getCategory();
        switch (cat) {
            case ALL:
                return true;
            case FLYING:
                return mc == EntityClassification.AMBIENT || isFlying(t);
            case UNDEAD:
                return isUndead(t);
            case CREATURE:
                return mc == EntityClassification.CREATURE;
            case OTHER:
                return !isFlying(t) && !isUndead(t) && mc != EntityClassification.CREATURE;
        }
        return false;
    }

    private boolean isFlying(EntityType<?> t) {
        ResourceLocation key = ForgeRegistries.ENTITIES.getKey(t);
        if (key == null) return false;
        String p = key.getPath();
        return p.contains("bat") || p.contains("bee") || p.contains("blaze") ||
               p.contains("ghast") || p.contains("phantom") || p.contains("parrot") ||
               p.contains("vex") || p.contains("allay") || p.contains("dragon");
    }

    private boolean isUndead(EntityType<?> t) {
        ResourceLocation key = ForgeRegistries.ENTITIES.getKey(t);
        if (key == null) return false;
        String p = key.getPath();
        return p.contains("zombie") || p.contains("skeleton") || p.contains("wither") ||
               p.contains("phantom") || p.contains("drowned") || p.contains("husk") ||
               p.contains("stray") || p.contains("zoglin") || p.contains("zombie_villager");
    }

    private void selectMob(EntityType<?> entityType) {
        ResourceLocation mobId = ForgeRegistries.ENTITIES.getKey(entityType);
        if (mobId == null) return;
        if (onSelect != null) {
            onSelect.accept(mobId);
        } else if (waveConfig != null) {
            waveConfig.getMobs().get(mobIndex).setMobType(mobId);
        }
        this.minecraft.setScreen(parentScreen);
    }

    // ─── Render ────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        previewAngle = (previewAngle + 0.5f) % 360f;
        renderMobPreview(g, mx, my);

        // Use ScrollableScreen's three-pass rendering for the list
        renderHeader(g, mx, my, pt);

        int clipTop = getClipTop(), clipBot = getClipBot();

        // Pass 1: content in scissor zone (only widgets right of preview panel)
        ScissorHelper.enable(PREVIEW_W, clipTop, this.width - PREVIEW_W, Math.max(1, clipBot - clipTop));
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (!staticWidgets.contains(w)
                    && w.y + w.getHeight() > clipTop && w.y < clipBot
                    && w.x >= PREVIEW_W) w.render(g, mx, my, pt);
            }
        }
        renderContentExtra(g, mx, my, pt);
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Pass 2: header static widgets
        ScissorHelper.enable(0, 0, this.width, clipTop);
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (staticWidgets.contains(w) && w.y < clipTop) w.render(g, mx, my, pt);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Pass 3: footer static widgets
        ScissorHelper.enable(0, clipBot, this.width, this.height - clipBot);
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (staticWidgets.contains(w) && w.y >= clipBot) w.render(g, mx, my, pt);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        renderOverlay(g, mx, my, pt);
    }

    @Override
    protected void renderHeader(MatrixStack g, int mx, int my, float pt) {
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 8, GuiTheme.TEXT);
    }

    @Override
    protected void renderOverlay(MatrixStack g, int mx, int my, float pt) {
        // Counter at bottom
        String counter = "§7" + filteredMobs.size() + " " + I18n.get("wavedefense.mob.count_suffix");
        if (!searchQuery.isEmpty()) counter += " §8(\"" + searchQuery + "\")";
        com.wavedefense.gui.GuiCompat.drawString(g, this.font, counter, PREVIEW_W + 4, this.height - 12, 0xAAAAAA);
    }

    private void renderMobPreview(MatrixStack g, int mouseX, int mouseY) {
        int px = 2, py = 44;
        int pw = PREVIEW_W - 4;
        int ph = this.height - py - 24;

        com.wavedefense.gui.GuiCompat.fill(g, px, py, px + pw, py + ph, 0xFF1E1E1E);
        com.wavedefense.gui.GuiCompat.fill(g, px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF111111);

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
                        .filter(t -> currentMobId.equals(ForgeRegistries.ENTITIES.getKey(t)))
                        .findFirst().orElse(filteredMobs.isEmpty() ? null : filteredMobs.get(0))
                    : (filteredMobs.isEmpty() ? null
                        : filteredMobs.get(Math.min(scrollOffset, filteredMobs.size() - 1))));

        if (display == null) return;

        ResourceLocation key = ForgeRegistries.ENTITIES.getKey(display);
        boolean isCurrent    = currentMobId != null && currentMobId.equals(key);

        String fullName  = display.getDescription().getString();
        String shortName = fullName.length() > 10 ? fullName.substring(0, 9) + "…" : fullName;
        String nameColor = isCurrent ? "§6§l" : "§e";
        String modId     = (key != null && !key.getNamespace().equals("minecraft"))
                ? key.getNamespace() : "minecraft";

        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, nameColor + shortName, px + pw / 2, py + 4, 0xFFFFFF);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§8" + modId, px + pw / 2, py + 13, 0x888888);

        if (isCurrent) {
            com.wavedefense.gui.GuiCompat.fill(g, px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFFFFAA00);
            com.wavedefense.gui.GuiCompat.fill(g, px, py, px + pw, py + ph, 0xFF1E1E1E);
            com.wavedefense.gui.GuiCompat.fill(g, px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF111111);
        }

        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                net.minecraft.entity.Entity entity = display.create(mc.level);
                if (entity instanceof net.minecraft.entity.LivingEntity) { net.minecraft.entity.LivingEntity living = (net.minecraft.entity.LivingEntity) entity;
                    int cx = px + pw / 2;
                    int cy = py + ph / 2 + 15;
                    float scale = Math.min(pw * 0.8f, (ph - 30) * 0.8f)
                            / Math.max(1f, (float) living.getBbHeight());
                    scale = Math.min(scale, 50f);

                    // 1.16.5: Quaternion has no rotateX/Y/Z chainable methods — use Vector3f.X/Y/Z.rotation(angle)
                    // The InventoryScreen.renderEntityInInventory(cx, cy, scale, mouseX, mouseY, living)
                    // takes mouse coords for yaw/pitch — pass simulated rotation values instead.
                    float yaw = previewAngle; // degrees
                    int entityH = (int)(living.getBbHeight() * scale);
                    int renderY = cy + (int)(entityH * 0.4f);
                    net.minecraft.client.gui.screen.inventory.InventoryScreen.renderEntityInInventory(cx, renderY, (int) scale, yaw, 0f, living);
                }
            }
        } catch (Exception ignored) {
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§7[no preview]", px + pw / 2, py + ph / 2, 0x666666);
        }

        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§8" + display.getCategory().name().toLowerCase(),
                px + pw / 2, py + ph - 14, 0x666666);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }
}
