package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompletionRewardScreen extends ListEditorScreen<ShopItem> {
    private final Location location;

    private boolean editingItem = false;
    private int editingIndex = -1;
    private List<ItemStack> editItems = new ArrayList<>();
    private EditBox minPointsInput;
    private int pendingDeleteRewardIndex = -1;

    private static final int ITEMS_PER_PAGE = 4;
    private static final int ROW_H  = 62;
    private static final int LIST_Y = 85;   // = getStartY()

    private static final int SLOT_W   = 70;
    private static final int SLOT_H   = 16;
    private static final int SLOT_GAP = 6;

    public CompletionRewardScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.completion_rewards")
                .append(": ").append(location.getName()), parent);
        this.location = location;
    }

    // ─── ListEditorScreen / ScrollableScreen API ───────────────────────────

    @Override protected List<ShopItem> getItems()  { return location.getCompletionRewards(); }
    @Override protected int getRowHeight()         { return ROW_H; }
    @Override protected int getStartY()            { return LIST_Y; }
    @Override protected int getClipTop()           { return 83; }
    @Override protected int getClipBot()           { return this.height - 32; }
    @Override protected int getItemsPerPage()      { return ITEMS_PER_PAGE; }

    // ─── init() ────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 35;

        if (editingItem) {
            initEditMode(cx, y);
            return;
        }

        // ── Header (static) ──────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.label.rewards_hint"), button -> {}
        ).bounds(cx - 160, y, 340, 16).build()).active = false;
        y += 22;

        addStatic(Button.builder(
                Component.translatable("wavedefense.button.add_wave_reward"), button -> startAddItem()
        ).bounds(cx - 160, y, 210, 20).build());

        // ── Content (scrollable) ──────────────────────────────────────
        buildVisibleRows();

        addScrollButtons(cx + 162, LIST_Y,
                LIST_Y + (ITEMS_PER_PAGE - 1) * ROW_H, 18, 18);

        // ── Footer (static) ───────────────────────────────────────────
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.save_back"), button -> saveAndBack()
        ).bounds(cx - 160, this.height - 28, 150, 20).build());
        addStatic(Button.builder(
                Component.translatable("wavedefense.button.cancel"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(cx - 5, this.height - 28, 110, 20).build());
    }

    // ─── Row builder ───────────────────────────────────────────────────────

    @Override
    protected void buildRowWidgets(int cx, int y, ShopItem reward, int index) {
        List<ItemStack> items = reward.getItems();

        StringBuilder namesBuilder = new StringBuilder();
        for (int ni = 0; ni < items.size(); ni++) {
            if (ni > 0) namesBuilder.append("§8, §e");
            String nm  = items.get(ni).getHoverName().getString();
            int cnt    = items.get(ni).getCount();
            if (cnt > 1) nm = nm + " §8x" + cnt + "§e";
            if (nm.length() > 22) nm = nm.substring(0, 20) + "…";
            namesBuilder.append(nm);
        }
        String allNames = items.isEmpty() ? "§c???" : ("§e" + namesBuilder);
        if (allNames.length() > 80) allNames = allNames.substring(0, 78) + "…";

        String rowLabel = I18n.get("wavedefense.label.reward_row", reward.getBuyPrice(), allNames);

        this.addRenderableWidget(Button.builder(
                Component.literal(rowLabel), button -> {}
        ).bounds(cx - 160, y, 260, 18).build()).active = false;

        final int fIdx = index;
        this.addRenderableWidget(Button.builder(
                Component.literal("✎"), button -> startEditItem(fIdx)
        ).bounds(cx + 105, y, 24, 18).build());
        boolean isPendingDelReward = (pendingDeleteRewardIndex == fIdx);
        int delRewardW = isPendingDelReward ? 50 : 24;
        this.addRenderableWidget(Button.builder(
                isPendingDelReward
                    ? Component.translatable("wavedefense.button.confirm_delete")
                    : Component.literal("§c✕"),
                button -> {
                    if (isPendingDelReward) {
                        pendingDeleteRewardIndex = -1;
                        location.removeCompletionReward(fIdx);
                        rebuildWidgets();
                    } else {
                        pendingDeleteRewardIndex = fIdx;
                        rebuildWidgets();
                    }
                }
        ).bounds(cx + 131, y, delRewardW, 18).build());

        for (int j = 0; j < Math.min(4, items.size()); j++) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(""), button -> {}
            ).bounds(cx - 156 + j * 22, y + 20, 20, 20).build()).active = false;
        }
        if (!items.isEmpty()) {
            String itemName = "§7" + items.get(0).getHoverName().getString();
            if (items.size() > 1) itemName += " §8(+" + (items.size() - 1) + ")";
            if (itemName.length() > 40) itemName = itemName.substring(0, 38) + "…";
            this.addRenderableWidget(Button.builder(
                    Component.literal(itemName), button -> {}
            ).bounds(cx - 160, y + 42, 260, 12).build()).active = false;
        }
    }

    // ─── Edit mode ─────────────────────────────────────────────────────────

    private void initEditMode(int cx, int y) {
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.label.rewards_edit_hint"), button -> {}
        ).bounds(cx - 155, y, 320, 14).build()).active = false;
        y += 16;

        int totalSlotsW = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft   = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos  = slotsLeft + i * (SLOT_W + SLOT_GAP);
            final int si = i;
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.set_item"),
                    button -> setItem(si)
            ).bounds(xPos, y + 20, SLOT_W, SLOT_H).build());
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.clear_item"),
                    button -> clearItem(si)
            ).bounds(xPos, y + 20 + SLOT_H + 2, SLOT_W, SLOT_H).build());
        }
        y += 20 + SLOT_H * 2 + 10;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.label.min_points_reward"), button -> {}
        ).bounds(cx - 155, y, 310, 18).build()).active = false;
        y += 20;
        minPointsInput = new EditBox(this.font, cx - 155, y, 80, 20,
                Component.translatable("wavedefense.label.points"));
        minPointsInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getCompletionRewards().get(editingIndex).getBuyPrice()) : "0");
        minPointsInput.setMaxLength(7);
        this.addRenderableWidget(minPointsInput);
        y += 30;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.save"), button -> saveItem()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.cancel"),
                button -> { editingItem = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ─── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (editingItem) {
            renderEditMode(g, mx, my, pt);
            return;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
    }

    @Override
    protected void renderContentExtra(GuiGraphics g, int mx, int my, float pt) {
        int cx = this.width / 2;
        List<ShopItem> rewards = location.getCompletionRewards();
        ItemStack tooltipItem = null;
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, rewards.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= rewards.size()) break;
            List<ItemStack> items = rewards.get(idx).getItems();
            int yPos = LIST_Y + i * ROW_H;
            for (int j = 0; j < Math.min(4, items.size()); j++) {
                int ix = cx - 156 + j * 22;
                int iy = yPos + 20;
                g.fill(ix - 1, iy - 1, ix + 17, iy + 17, GuiTheme.BORDER);
                g.fill(ix, iy, ix + 16, iy + 16, GuiTheme.PANEL_DARK);
                g.renderItem(items.get(j), ix, iy);
                g.renderItemDecorations(this.font, items.get(j), ix, iy);
                if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
                    tooltipItem = items.get(j);
                }
            }
        }
        if (tooltipItem != null) {
            ScissorHelper.disable();
            g.renderTooltip(this.font, tooltipItem, mx, my);
        }
    }

    /** Режим редагування — повністю окремий рендер без scissor. */
    private void renderEditMode(GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx   = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, GuiTheme.TEXT);

        int iconY        = 51;
        int totalSlotsW  = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft    = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos  = slotsLeft + i * (SLOT_W + SLOT_GAP);
            ItemStack item = editItems.get(i);
            g.fill(xPos + (SLOT_W - 18) / 2 - 1, iconY - 1,
                   xPos + (SLOT_W - 18) / 2 + 19, iconY + 17, GuiTheme.BORDER);
            g.fill(xPos + (SLOT_W - 18) / 2, iconY,
                   xPos + (SLOT_W - 18) / 2 + 18, iconY + 16, GuiTheme.PANEL_DARK);
            int iconX = xPos + (SLOT_W - 16) / 2;
            g.renderItem(item, iconX, iconY);
            g.renderItemDecorations(this.font, item, iconX, iconY);
            if (!item.isEmpty() && mx >= iconX && mx <= iconX + 16
                    && my >= iconY && my <= iconY + 16) {
                g.renderTooltip(this.font, item, mx, my);
            }
        }

        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w) w.render(g, mx, my, pt);
        }
    }

    // ─── Actions ───────────────────────────────────────────────────────────

    private void startAddItem() {
        editingItem  = true;
        editingIndex = -1;
        editItems    = new ArrayList<>();
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void startEditItem(int idx) {
        editingItem  = true;
        editingIndex = idx;
        editItems    = new ArrayList<>(location.getCompletionRewards().get(idx).getItems());
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void setItem(int slot) {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            editItems.set(slot, minecraft.player.getMainHandItem().copy());
            rebuildWidgets();
        }
    }

    private void clearItem(int slot) {
        editItems.set(slot, ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void saveItem() {
        try {
            int minPts = Integer.parseInt(minPointsInput.getValue());
            List<ItemStack> finalItems = editItems.stream()
                    .filter(i -> !i.isEmpty()).collect(Collectors.toList());
            if (finalItems.isEmpty()) return;
            ShopItem reward = new ShopItem(finalItems, minPts, 0);
            if (editingIndex >= 0) {
                location.getCompletionRewards().set(editingIndex, reward);
            } else {
                location.addCompletionReward(reward);
            }
            editingItem = false;
            rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void saveAndBack() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        // This list is excluded from UpdateLocationPacket (content-sized), so it
        // travels on the generic chunked list transport instead.
        {
            net.minecraft.nbt.ListTag _l = new net.minecraft.nbt.ListTag();
            for (var _e : location.getCompletionRewards()) _l.add(_e.save());
            com.wavedefense.network.packets.ReplaceLocationListPacket.sendList(
                location.getName(), "completionRewards", _l);
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.translatable("wavedefense.msg.rewards_saved"), true);
        }
        this.minecraft.setScreen(parent);
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
