package com.wavedefense.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Меню вибору предмета.
 * ✓ Підсвічення вже-вибраного предмета золотою рамкою
 * ✓ Пошук по назві і registry ID (виправлено — responder тепер коректний)
 * ✓ Адаптивна сітка під будь-яке розширення
 * ✓ Категорії + кількість відповідних предметів
 */
public class ItemSelectionScreen extends Screen {

    public enum Category {
        ALL("Всі"), WEAPON("⚔ Зброя"), ARMOR("🛡 Броня"),
        POTION("🧪 Зілля"), FOOD("🍖 Їжа"), OTHER("📦 Інше");
        public final String label;
        Category(String l) { this.label = l; }
    }

    private final Screen       parent;
    private final Consumer<ItemStack> onSelect;
    private final ItemStack    currentItem; // вже вибраний — підсвічується

    private List<Item> allItems;
    private List<Item> filteredItems;
    private Category   currentCategory = Category.ALL;
    private String     searchQuery     = "";
    private int        scrollOffset    = 0;

    private ItemStack  hovered = ItemStack.EMPTY;
    private float      previewAngle = 0f;

    // Адаптивні константи (перераховуються в init)
    private int COLS      = 8;
    private int SLOT_SIZE = 18;
    private int PREVIEW_W = 60;

    private EditBox searchBox;

    /** Без поточного предмета */
    public ItemSelectionScreen(Screen parent, Consumer<ItemStack> onSelect) {
        this(parent, onSelect, ItemStack.EMPTY);
    }

    /** З поточним предметом для підсвічення */
    public ItemSelectionScreen(Screen parent, Consumer<ItemStack> onSelect, ItemStack currentItem) {
        super(Component.literal("Вибір предмета"));
        this.parent      = parent;
        this.onSelect    = onSelect;
        this.currentItem = currentItem == null ? ItemStack.EMPTY : currentItem;

        allItems = ForgeRegistries.ITEMS.getValues().stream()
                .filter(i -> i != Items.AIR)
                .sorted(Comparator.comparing(i -> i.getDescription().getString()))
                .collect(Collectors.toList());
        filteredItems = new ArrayList<>(allItems);
    }

    @Override
    protected void init() {
        super.init();
        // ── Адаптивні розміри ────────────────────────────────────────
        SLOT_SIZE = this.width < 320 ? 16 : 18;
        PREVIEW_W = Math.max(50, Math.min(80, this.width / 10));
        COLS      = Math.max(4, (this.width - PREVIEW_W - 20) / SLOT_SIZE);

        int cx = this.width / 2;

        // Пошук — зберігаємо значення між rebuildWidgets
        searchBox = new EditBox(this.font, cx - 80, 24, 200, 16, Component.literal("Пошук..."));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        // ВИПРАВЛЕНО: responder одразу оновлює searchQuery і фільтр
        searchBox.setResponder(s -> {
            searchQuery = s;
            scrollOffset = 0;
            applyFilter();
            // не викликаємо rebuildWidgets тут — applyFilter вже це робить
        });
        this.addRenderableWidget(searchBox);
        // Фокус на пошук при відкритті
        this.setInitialFocus(searchBox);

        // Категорії з кількістю
        Category[] cats = Category.values();
        int catW  = Math.max(44, (this.width - PREVIEW_W - 20) / cats.length - 2);
        int catX  = PREVIEW_W + 8;
        for (Category cat : cats) {
            final Category c = cat;
            boolean active = (cat == currentCategory);
            long cnt = (cat == Category.ALL) ? allItems.size()
                    : allItems.stream().filter(i -> matchesCategoryStatic(i, cat)).count();
            String lbl = (active ? "§e§l" : "§7") + cat.label + " §8(" + cnt + ")";
            this.addRenderableWidget(Button.builder(
                    Component.literal(lbl),
                    b -> { currentCategory = c; scrollOffset = 0; applyFilter(); }
            ).bounds(catX, 44, catW, 14).build());
            catX += catW + 2;
        }

        // Закрити
        this.addRenderableWidget(Button.builder(
                Component.literal("✕ Закрити"),
                b -> this.minecraft.setScreen(parent)
        ).bounds(this.width - 72, 24, 68, 16).build());
    }

    // applyFilter НЕ викликає rebuildWidgets щоб уникнути скидання searchBox
    private void applyFilter() {
        filteredItems = allItems.stream()
                .filter(i -> matchesCategoryStatic(i, currentCategory))
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
        // Перебудовуємо лише кнопки категорій та скрол, але НЕ searchBox
        rebuildWidgets();
    }

    private boolean matchesCategoryStatic(Item item, Category cat) {
        return switch (cat) {
            case ALL    -> true;
            case WEAPON -> item instanceof SwordItem || item instanceof AxeItem ||
                           item instanceof BowItem   || item instanceof CrossbowItem ||
                           item instanceof TridentItem || item instanceof ShieldItem ||
                           item instanceof PickaxeItem || item instanceof ShovelItem ||
                           item instanceof HoeItem;
            case ARMOR  -> item instanceof ArmorItem;
            case POTION -> item instanceof PotionItem || item instanceof SplashPotionItem ||
                           item instanceof LingeringPotionItem || item instanceof TippedArrowItem;
            case FOOD   -> item.isEdible();
            case OTHER  -> !(item instanceof SwordItem) && !(item instanceof AxeItem) &&
                           !(item instanceof ArmorItem) && !item.isEdible() &&
                           !(item instanceof PotionItem) && !(item instanceof SplashPotionItem);
        };
    }

    private boolean matchesSearch(Item item) {
        if (searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        String regId = key != null ? key.toString() : "";
        return item.getDescription().getString().toLowerCase().contains(q)
            || regId.toLowerCase().contains(q);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        previewAngle = (previewAngle + 0.5f) % 360f;

        g.drawCenteredString(this.font, "§6Вибір предмета", this.width / 2, 8, 0xFFFFFF);

        // ── Сітка предметів ──────────────────────────────────────────
        int gridX = PREVIEW_W + 8;
        int gridY = 64;
        int gridW = this.width - gridX - 10;
        int rows  = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;

        hovered = ItemStack.EMPTY;

        for (int i = 0; i < perPage; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredItems.size()) break;
            Item item  = filteredItems.get(idx);
            ItemStack stack = new ItemStack(item);

            int col = i % COLS;
            int row = i / COLS;
            int sx  = gridX + col * SLOT_SIZE;
            int sy  = gridY + row * SLOT_SIZE;

            // Рамка слоту
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF333333);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF1A1A1A);

            // Підсвічення вже-вибраного предмета — ЗОЛОТА рамка
            boolean isSelected = !currentItem.isEmpty()
                    && currentItem.getItem() == item;
            if (isSelected) {
                // Зовнішня золота рамка
                g.fill(sx - 1, sy - 1, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1, 0xFFFFAA00);
                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x60FFAA00);
            }

            // Підсвічення ховера
            boolean isHovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                              && mouseY >= sy && mouseY < sy + SLOT_SIZE;
            if (isHovered) {
                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x80FFFFFF);
                hovered = stack;
            }

            g.renderItem(stack, sx + 1, sy + 1);
        }

        // ── Превʼю зліва ────────────────────────────────────────────
        renderItemPreview(g);

        // Tooltip
        if (!hovered.isEmpty()) {
            g.renderTooltip(this.font, hovered, mouseX, mouseY);
        }

        // Лічильник
        String counter = "§7" + filteredItems.size() + " предметів";
        if (!searchQuery.isEmpty()) counter += " §8(фільтр: \"" + searchQuery + "\")";
        g.drawString(this.font, counter, PREVIEW_W + 8, this.height - 12, 0xAAAAAA);

        // Скролбар
        int maxScroll = Math.max(0, filteredItems.size() - perPage);
        if (maxScroll > 0) {
            int sbH   = this.height - gridY - 24;
            int thumbH = Math.max(10, sbH * perPage / filteredItems.size());
            int thumbY = gridY + (sbH - thumbH) * scrollOffset / maxScroll;
            g.fill(this.width - 6, gridY, this.width - 4, gridY + sbH, 0xFF444444);
            g.fill(this.width - 6, thumbY, this.width - 4, thumbY + thumbH, 0xFFAAAAAA);
        }

        super.render(g, mouseX, mouseY, partial);
    }

    private void renderItemPreview(GuiGraphics g) {
        int px = 4, py = 64;
        int pw = PREVIEW_W - 4;
        int ph = Math.min(pw, this.height - py - 40);

        g.fill(px, py, px + pw, py + ph, 0xFF2A2A2A);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF111111);

        ItemStack preview = !hovered.isEmpty() ? hovered
                : (!currentItem.isEmpty() ? currentItem
                : (filteredItems.isEmpty() ? ItemStack.EMPTY : new ItemStack(filteredItems.get(0))));

        if (preview.isEmpty()) return;

        com.mojang.blaze3d.vertex.PoseStack ps = g.pose();
        ps.pushPose();
        int cx = px + pw / 2;
        int cy = py + ph / 2;
        float scale = Math.max(1.5f, Math.min(3f, pw / 12f));
        ps.translate(cx, cy, 100);
        ps.scale(scale, scale, 1f);
        g.renderItem(preview, -8, -8);
        ps.popPose();

        // Назва
        String name = preview.getHoverName().getString();
        if (name.length() > 10) name = name.substring(0, 9) + "…";
        g.drawCenteredString(this.font, "§f" + name, px + pw / 2, py + ph + 2, 0xFFFFFF);

        // Мод-бейдж
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(preview.getItem());
        if (key != null && !key.getNamespace().equals("minecraft")) {
            g.drawCenteredString(this.font, "§7[" + key.getNamespace() + "]",
                    px + pw / 2, py + ph + 12, 0x888888);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gridX   = PREVIEW_W + 8;
        int gridY   = 64;
        int rows    = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;

        for (int i = 0; i < perPage; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredItems.size()) break;
            int col = i % COLS, row = i / COLS;
            int sx = gridX + col * SLOT_SIZE;
            int sy = gridY + row * SLOT_SIZE;
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                ItemStack selected = new ItemStack(filteredItems.get(idx));
                if (onSelect != null) onSelect.accept(selected);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridY   = 64;
        int rows    = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;
        int maxScroll = Math.max(0, filteredItems.size() - perPage);
        if (delta < 0) scrollOffset = Math.min(scrollOffset + COLS, maxScroll);
        else           scrollOffset = Math.max(scrollOffset - COLS, 0);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
