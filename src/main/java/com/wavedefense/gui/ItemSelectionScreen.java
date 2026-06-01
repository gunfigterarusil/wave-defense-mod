package com.wavedefense.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.common.CreativeModeTabRegistry;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Item picker — shows every item available in the creative inventory,
 * organised into the same {@link CreativeModeTab} structure (same labels,
 * same ordering as the creative menu).
 *
 * <p>Tabs are discovered at runtime via {@link CreativeModeTabRegistry}; this
 * covers vanilla tabs, modded tabs from Tacz / Mine and Slash / etc., and any
 * datapack-added categories. A virtual "All" tab appears first and lists every
 * stack from every tab, deduplicated.
 */
public class ItemSelectionScreen extends Screen {

    private final Screen              parent;
    private final Consumer<ItemStack> onSelect;
    private       ItemStack           currentItem;

    private int SLOT_SIZE = 18;
    private int PREVIEW_W = 70;
    private int COLS      = 10;

    /** Sorted list of non-empty creative tabs (vanilla + modded). */
    private List<CreativeModeTab> creativeTabs = new ArrayList<>();
    /** Active tab index; -1 = the virtual "All" aggregate tab. */
    private int     activeTabIndex = -1;
    /** Horizontal scroll within the tab strip (which tab is leftmost on screen). */
    private int     tabRowOffset   = 0;

    private String  searchQuery   = "";
    private EditBox searchBox;
    private int     scrollOffset  = 0;
    private float   previewAngle  = 0;

    /** Aggregate of every stack across every creative tab (deduped) — used by the "All" tab. */
    private List<ItemStack> allStacks;
    /** Currently visible list after tab + search filtering. */
    private List<ItemStack> filteredStacks;

    /** Count selected via click counting (LMB +1, RMB -1, Shift+LMB +10, Shift+RMB reset). */
    private int selectedCount = 0;

    public ItemSelectionScreen(Screen parent, Consumer<ItemStack> onSelect, ItemStack currentItem) {
        super(Component.translatable("wavedefense.title.item_selection"));
        this.parent      = parent;
        this.onSelect    = onSelect;
        this.currentItem = currentItem == null ? ItemStack.EMPTY : currentItem;
        // Pre-populate the click counter from the incoming stack so re-opening a slot
        // shows the existing count and lets the admin tweak it incrementally.
        this.selectedCount = this.currentItem.isEmpty() ? 0 : Math.max(1, this.currentItem.getCount());
        discoverCreativeTabs();
        buildAllStacks();
    }

    /**
     * Captures every loaded {@link CreativeModeTab} that actually contains items.
     * Forces a build pass first via {@link CreativeTabHelper#forceBuildAllTabs()}
     * because Forge 1.20.1 only populates {@code getDisplayItems()} after
     * {@code BuildCreativeModeTabContentsEvent} fires — which may not have
     * happened by the time the admin opens the picker for the first time.
     */
    private void discoverCreativeTabs() {
        CreativeTabHelper.forceBuildAllTabs();
        creativeTabs.clear();
        try {
            for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                if (!CreativeTabHelper.safeGetItems(tab).isEmpty()) {
                    creativeTabs.add(tab);
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Aggregates every stack from every discovered tab into a single deduped list
     * for the virtual "All" tab, plus a fallback pass over {@link ForgeRegistries#ITEMS}
     * to catch any item that no tab claimed. Sorted by hover-name.
     */
    private void buildAllStacks() {
        allStacks = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        // 1. Aggregate from every creative tab — keeps NBT-distinguished modded stacks
        //    (Tacz guns w/ GunId, MnS gear w/ stats, etc.).
        for (CreativeModeTab tab : creativeTabs) {
            for (ItemStack st : CreativeTabHelper.safeGetItems(tab)) {
                if (st == null || st.isEmpty() || st.getItem() == Items.AIR) continue;
                String key = stableKey(st);
                if (seen.add(key)) allStacks.add(st.copy());
            }
        }

        // 2. Fallback — every registered item that no tab exposed. Guarantees the
        //    picker is never empty even if Forge tab building somehow stays empty.
        try {
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                if (item == Items.AIR) continue;
                try {
                    ItemStack st = new ItemStack(item);
                    if (seen.add(stableKey(st))) allStacks.add(st);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 3. Sort by display name for predictable browsing
        allStacks.sort(Comparator.comparing(s -> {
            try { return s.getHoverName().getString().toLowerCase(Locale.ROOT); }
            catch (Throwable e) { return ""; }
        }));
        filteredStacks = new ArrayList<>(allStacks);
    }

    /**
     * Stable dedupe key: "modid:itempath[|nbt_without_display]". Fields like
     * {@code display}/{@code Damage}/{@code RepairCost} are stripped so two
     * stacks of the same item with cosmetic differences merge into one entry.
     */
    private static String stableKey(ItemStack st) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(st.getItem());
        String base = rl != null ? rl.toString() : st.getItem().toString();
        if (!st.hasTag()) return base;
        net.minecraft.nbt.CompoundTag tag = st.getTag().copy();
        tag.remove("display");
        tag.remove("Damage");
        tag.remove("RepairCost");
        String tagStr = tag.isEmpty() ? "" : "|" + tag;
        return base + tagStr;
    }

    /** Returns the stacks belonging to the currently active tab (with search applied). */
    private List<ItemStack> stacksForActiveTab() {
        if (activeTabIndex < 0 || activeTabIndex >= creativeTabs.size()) {
            return allStacks;
        }
        CreativeModeTab tab = creativeTabs.get(activeTabIndex);
        List<ItemStack> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ItemStack st : CreativeTabHelper.safeGetItems(tab)) {
            if (st == null || st.isEmpty() || st.getItem() == Items.AIR) continue;
            if (seen.add(stableKey(st))) out.add(st);
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();
        SLOT_SIZE = this.width < 320 ? 16 : 18;
        PREVIEW_W = Math.max(50, Math.min(80, this.width / 10));
        COLS      = Math.max(4, (this.width - PREVIEW_W - 20) / SLOT_SIZE);

        int cx = this.width / 2;

        // ── Search box (top) ─────────────────────────────────────────────
        searchBox = new EditBox(this.font, cx - 80, 24, 200, 16,
            Component.translatable("wavedefense.label.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(s -> {
            searchQuery = s;
            scrollOffset = 0;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        // ── Creative-tab strip (y=44, scrollable horizontally) ───────────
        // Layout: [◄] [All] [Tab1] [Tab2] ... [TabN] [►]
        int stripX = PREVIEW_W + 8;
        int stripW = this.width - stripX - 10;
        int navW = 14;
        int tabW = 70;          // width of one tab button
        int tabGap = 2;
        int stripInnerW = Math.max(1, stripW - 2 * (navW + tabGap));
        int visibleTabs = Math.max(1, stripInnerW / (tabW + tabGap));

        // Clamp tabRowOffset to legal range. Total entries = creativeTabs.size() + 1 (the All tab).
        int totalEntries = creativeTabs.size() + 1;
        int maxOffset = Math.max(0, totalEntries - visibleTabs);
        if (tabRowOffset > maxOffset) tabRowOffset = maxOffset;
        if (tabRowOffset < 0) tabRowOffset = 0;

        // ◄ left scroll
        Button leftBtn = Button.builder(Component.literal("◄"),
            b -> { if (tabRowOffset > 0) { tabRowOffset--; rebuildWidgets(); } }
        ).bounds(stripX, 44, navW, 14).build();
        leftBtn.active = tabRowOffset > 0;
        this.addRenderableWidget(leftBtn);

        int curX = stripX + navW + tabGap;
        for (int i = 0; i < visibleTabs; i++) {
            int entry = tabRowOffset + i;
            if (entry >= totalEntries) break;
            final int idx = entry - 1;        // -1 = All, else creativeTabs index
            boolean active = idx == activeTabIndex;
            String label;
            if (idx < 0) {
                label = I18n.get("wavedefense.tab.all");
            } else {
                try {
                    label = creativeTabs.get(idx).getDisplayName().getString();
                } catch (Throwable t) {
                    label = "Tab " + idx;
                }
            }
            if (label.length() > 10) label = label.substring(0, 9) + "…";
            String coloured = (active ? "§e§l" : "§7") + label;
            this.addRenderableWidget(Button.builder(
                Component.literal(coloured),
                b -> { activeTabIndex = idx; scrollOffset = 0; applyFilter(); }
            ).bounds(curX, 44, tabW, 14).build());
            curX += tabW + tabGap;
        }

        // ► right scroll
        Button rightBtn = Button.builder(Component.literal("►"),
            b -> { if (tabRowOffset < maxOffset) { tabRowOffset++; rebuildWidgets(); } }
        ).bounds(stripX + stripW - navW, 44, navW, 14).build();
        rightBtn.active = tabRowOffset < maxOffset;
        this.addRenderableWidget(rightBtn);

        // ── Confirm + Cancel (top-right) ─────────────────────────────────
        // Confirm: send current selection (with click-counted count) back to the parent.
        Button confirmBtn = Button.builder(
                Component.literal("§a✓ " + I18n.get("wavedefense.button.confirm")),
                b -> {
                    if (!currentItem.isEmpty() && onSelect != null) {
                        ItemStack out = currentItem.copy();
                        out.setCount(Math.max(1, Math.min(64, selectedCount)));
                        onSelect.accept(out);
                    }
                    this.minecraft.setScreen(parent);
                }
        ).bounds(this.width - 144, 24, 68, 16).build();
        confirmBtn.active = !currentItem.isEmpty();
        this.addRenderableWidget(confirmBtn);

        this.addRenderableWidget(Button.builder(
                Component.literal("§c✕ " + I18n.get("wavedefense.button.cancel")),
                b -> this.minecraft.setScreen(parent)
        ).bounds(this.width - 72, 24, 68, 16).build());
    }

    private void applyFilter() {
        List<ItemStack> source = stacksForActiveTab();
        filteredStacks = source.stream()
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
        rebuildWidgets();
    }

    private boolean matchesSearch(ItemStack stack) {
        if (searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase(Locale.ROOT);
        String name = "";
        try { name = stack.getHoverName().getString().toLowerCase(Locale.ROOT); } catch (Exception ignored) {}
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String regId = key != null ? key.toString().toLowerCase(Locale.ROOT) : "";
        return name.contains(q) || regId.contains(q);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    private ItemStack hoveredStack = ItemStack.EMPTY;

    /** Grid Y — fixed below the tab strip. */
    private int computeGridY() { return 64; }

    /** Returns a copy of {@code st} with count clamped to {@code count} (≥1). */
    private static ItemStack withCount(ItemStack st, int count) {
        ItemStack copy = st.copy();
        copy.setCount(Math.max(1, count));
        return copy;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        previewAngle = (previewAngle + 0.5f) % 360f;

        int gridX = PREVIEW_W + 8;
        int gridY = computeGridY();
        int gridW = this.width - gridX - 10;
        int rows  = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;
        int gridBottom = gridY + rows * SLOT_SIZE;

        hoveredStack = ItemStack.EMPTY;

        // Counter + click-hint (footer)
        String counter = filteredStacks.size() + " " + I18n.get("wavedefense.item.count_suffix");
        if (!searchQuery.isEmpty()) counter += " (\"" + searchQuery + "\")";
        g.drawString(this.font, counter, PREVIEW_W + 8, this.height - 12, GuiTheme.TEXT_MUTED, false);

        // Click-hint and current selection count — right-aligned in the footer.
        String hint = I18n.get("wavedefense.item.click_hint");
        int hintW = this.font.width(hint);
        g.drawString(this.font, hint,
            this.width - hintW - 12 - 80, this.height - 12, GuiTheme.TEXT_MUTED, false);
        if (!currentItem.isEmpty() && selectedCount > 0) {
            String sel = I18n.get("wavedefense.item.selected_count", selectedCount);
            int selW = this.font.width(sel);
            g.drawString(this.font, sel,
                this.width - selW - 12, this.height - 12, 0xFFFFD700, false);
        }

        // Scrollbar
        int maxScroll = Math.max(0, filteredStacks.size() - perPage);
        GuiTheme.scrollBar(g, this.width - 7, gridY, gridBottom, scrollOffset, filteredStacks.size(), perPage);

        // ── Item grid (scissor) ──────────────────────────────────────────
        ScissorHelper.enable(gridX, gridY, gridW + 8, Math.max(1, gridBottom - gridY));
        for (int i = 0; i < perPage; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredStacks.size()) break;
            ItemStack stack = filteredStacks.get(idx);
            int col = i % COLS, row = i / COLS;
            int sx = gridX + col * SLOT_SIZE;
            int sy = gridY + row * SLOT_SIZE;

            // Slot background
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, GuiTheme.BORDER);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, GuiTheme.PANEL_DARK);

            boolean isSelected = !currentItem.isEmpty()
                    && currentItem.getItem() == stack.getItem()
                    && ItemStack.isSameItemSameTags(currentItem, stack);

            // Render icon + use a copy with our click-counted count so vanilla decoration
            // draws the proper "×N" number in the corner of the slot.
            int iconX = sx + (SLOT_SIZE - 16) / 2;
            int iconY = sy + (SLOT_SIZE - 16) / 2;
            ItemStack visual = isSelected
                ? withCount(stack, Math.max(1, selectedCount))
                : stack;
            g.renderItem(visual, iconX, iconY);
            g.renderItemDecorations(this.font, visual, iconX, iconY);

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x44FFFFFF);
                hoveredStack = stack;
            }

            // Bright yellow 2-pixel outline around the currently selected slot.
            // Drawn LAST so it sits on top of the hover overlay and the icon.
            if (isSelected) {
                int c = 0xFFFFD700;
                g.fill(sx,             sy,             sx + SLOT_SIZE, sy + 1,             c); // top
                g.fill(sx,             sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, c); // bottom
                g.fill(sx,             sy,             sx + 1,         sy + SLOT_SIZE,     c); // left
                g.fill(sx + SLOT_SIZE - 1, sy,         sx + SLOT_SIZE, sy + SLOT_SIZE,     c); // right
            }
        }
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getX() >= gridX
                    && w.getY() + w.getHeight() > gridY && w.getY() < gridBottom)
                w.render(g, mouseX, mouseY, partial);
        }
        ScissorHelper.disable();

        // Static widgets (search, tabs, close)
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && (w.getY() < gridY || w.getX() < gridX))
                w.render(g, mouseX, mouseY, partial);
        }

        // Tooltip
        if (!hoveredStack.isEmpty())
            g.renderTooltip(this.font, hoveredStack, mouseX, mouseY);

        renderItemPreview(g);
    }

    private void renderItemPreview(GuiGraphics g) {
        int px = 4, py = 64;
        int pw = PREVIEW_W - 4;
        int ph = Math.min(pw, this.height - py - 40);

        ItemStack preview = hoveredStack.isEmpty() ? currentItem : hoveredStack;
        if (preview.isEmpty()) return;

        GuiTheme.panel(g, px - 1, py - 1, px + pw + 1, py + ph + 1);

        int iconX = px + (pw - 16) / 2;
        int iconY = py + (ph - 16) / 2;
        g.renderItem(preview, iconX, iconY);
        g.renderItemDecorations(this.font, preview, iconX, iconY);

        String name = preview.getHoverName().getString();
        if (name.length() > 12) name = name.substring(0, 11) + "…";
        g.drawCenteredString(this.font, name, px + pw / 2, py + ph + 2, GuiTheme.TEXT);

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(preview.getItem());
        if (key != null && !key.getNamespace().equals("minecraft")) {
            g.drawCenteredString(this.font, "[" + key.getNamespace() + "]",
                    px + pw / 2, py + ph + 12, GuiTheme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gridX  = PREVIEW_W + 8;
        int gridY  = computeGridY();
        int rows   = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;

        // Modifiers
        long handle = this.minecraft != null ? this.minecraft.getWindow().getWindow() : 0L;
        boolean shift = handle != 0
            && (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS);

        for (int i = 0; i < perPage; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredStacks.size()) break;
            int col = i % COLS, row = i / COLS;
            int sx = gridX + col * SLOT_SIZE;
            int sy = gridY + row * SLOT_SIZE;
            if (mx < sx || mx >= sx + SLOT_SIZE || my < sy || my >= sy + SLOT_SIZE) continue;

            ItemStack clicked = filteredStacks.get(idx);
            boolean same = !currentItem.isEmpty()
                && currentItem.getItem() == clicked.getItem()
                && ItemStack.isSameItemSameTags(currentItem, clicked);

            // Switching to a different item resets the counter to 0 so the first
            // click increments it to 1 below — this avoids surprise "I just clicked
            // a new item and it became 5".
            if (!same) {
                currentItem = clicked.copy();
                selectedCount = 0;
            }

            if (button == 0) {
                // LMB
                int delta = shift ? 10 : 1;
                selectedCount = Math.max(1, Math.min(64, selectedCount + delta));
            } else if (button == 1) {
                // RMB
                if (shift) {
                    selectedCount = 0;
                    currentItem = ItemStack.EMPTY;
                } else {
                    selectedCount = Math.max(0, selectedCount - 1);
                    if (selectedCount == 0) currentItem = ItemStack.EMPTY;
                }
            }
            rebuildWidgets(); // refresh confirm-button active state
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridY   = computeGridY();
        int rows    = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;
        int maxScroll = Math.max(0, filteredStacks.size() - perPage);
        if (delta < 0) scrollOffset = Math.min(scrollOffset + COLS, maxScroll);
        else           scrollOffset = Math.max(scrollOffset - COLS, 0);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
