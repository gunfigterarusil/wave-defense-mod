package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompletionRewardScreen extends ListEditorScreen<ShopItem> {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

    private final Location location;

    private boolean editingItem = false;
    private int editingIndex = -1;
    private List<ItemStack> editItems = new ArrayList<>();
    private TextFieldWidget minPointsInput;
    private int pendingDeleteRewardIndex = -1;

    private static final int ITEMS_PER_PAGE = 4;
    private static final int ROW_H  = 62;
    private static final int LIST_Y = 85;   // = getStartY()

    private static final int SLOT_W   = 70;
    private static final int SLOT_H   = 16;
    private static final int SLOT_GAP = 6;

    public CompletionRewardScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.title.completion_rewards")
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
        addStatic(new Button(cx - 160, y, 340, 16, new TranslationTextComponent("wavedefense.label.rewards_hint"), button -> {})).active = false;
        y += 22;

        addStatic(new Button(cx - 160, y, 210, 20, new TranslationTextComponent("wavedefense.button.add_wave_reward"), button -> startAddItem()));

        // ── Content (scrollable) ──────────────────────────────────────
        buildVisibleRows();

        addScrollButtons(cx + 162, LIST_Y,
                LIST_Y + (ITEMS_PER_PAGE - 1) * ROW_H, 18, 18);

        // ── Footer (static) ───────────────────────────────────────────
        addStatic(new Button(cx - 160, this.height - 28, 150, 20, new TranslationTextComponent("wavedefense.button.save_back"), button -> saveAndBack()));
        addStatic(new Button(cx - 5, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.button.cancel"), button -> this.minecraft.setScreen(parent)));
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

        this.addButton(new Button(cx - 160, y, 260, 18, new StringTextComponent(rowLabel), button -> {})).active = false;

        final int fIdx = index;
        this.addButton(new Button(cx + 105, y, 24, 18, new StringTextComponent("✎"), button -> startEditItem(fIdx)));
        boolean isPendingDelReward = (pendingDeleteRewardIndex == fIdx);
        int delRewardW = isPendingDelReward ? 50 : 24;
        this.addButton(new Button(cx + 131, y, delRewardW, 18, isPendingDelReward
                    ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                    : new StringTextComponent("§c✕"), button -> {
                    if (isPendingDelReward) {
                        pendingDeleteRewardIndex = -1;
                        location.removeCompletionReward(fIdx);
                        rebuild();
                    } else {
                        pendingDeleteRewardIndex = fIdx;
                        rebuild();
                    }
                }));

        for (int j = 0; j < Math.min(4, items.size()); j++) {
            this.addButton(new Button(cx - 156 + j * 22, y + 20, 20, 20, new StringTextComponent(""), button -> {})).active = false;
        }
        if (!items.isEmpty()) {
            String itemName = "§7" + items.get(0).getHoverName().getString();
            if (items.size() > 1) itemName += " §8(+" + (items.size() - 1) + ")";
            if (itemName.length() > 40) itemName = itemName.substring(0, 38) + "…";
            this.addButton(new Button(cx - 160, y + 42, 260, 12, new StringTextComponent(itemName), button -> {})).active = false;
        }
    }

    // ─── Edit mode ─────────────────────────────────────────────────────────

    private void initEditMode(int cx, int y) {
        this.addButton(new Button(cx - 155, y, 320, 14, new TranslationTextComponent("wavedefense.label.rewards_edit_hint"), button -> {})).active = false;
        y += 16;

        int totalSlotsW = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft   = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos  = slotsLeft + i * (SLOT_W + SLOT_GAP);
            final int si = i;
            this.addButton(new Button(xPos, y + 20, SLOT_W, SLOT_H, new TranslationTextComponent("wavedefense.button.set_item"), button -> setItem(si)));
            this.addButton(new Button(xPos, y + 20 + SLOT_H + 2, SLOT_W, SLOT_H, new TranslationTextComponent("wavedefense.button.clear_item"), button -> clearItem(si)));
        }
        y += 20 + SLOT_H * 2 + 10;

        this.addButton(new Button(cx - 155, y, 310, 18, new TranslationTextComponent("wavedefense.label.min_points_reward"), button -> {})).active = false;
        y += 20;
        minPointsInput = new TextFieldWidget(this.font, cx - 155, y, 80, 20,
                new TranslationTextComponent("wavedefense.label.points"));
        minPointsInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getCompletionRewards().get(editingIndex).getBuyPrice()) : "0");
        minPointsInput.setMaxLength(7);
        this.addButton(minPointsInput);
        y += 30;

        this.addButton(new Button(cx - 110, y, 100, 20, new TranslationTextComponent("wavedefense.button.save"), button -> saveItem()));
        this.addButton(new Button(cx + 10, y, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), button -> { editingItem = false; rebuild(); }));
    }

    // ─── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(MatrixStack g, int mx, int my, float pt) {
        if (editingItem) {
            renderEditMode(g, mx, my, pt);
            return;
        }
        super.render(g, mx, my, pt);
    }

    @Override
    protected void renderHeader(MatrixStack g, int mx, int my, float pt) {
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);
    }

    @Override
    protected void renderContentExtra(MatrixStack g, int mx, int my, float pt) {
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
                com.wavedefense.gui.GuiCompat.fill(g, ix - 1, iy - 1, ix + 17, iy + 17, GuiTheme.BORDER);
                com.wavedefense.gui.GuiCompat.fill(g, ix, iy, ix + 16, iy + 16, GuiTheme.PANEL_DARK);
                com.wavedefense.gui.GuiCompat.renderItem(g, items.get(j), ix, iy);
                com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, items.get(j), ix, iy);
                if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
                    tooltipItem = items.get(j);
                }
            }
        }
        if (tooltipItem != null) {
            ScissorHelper.disable();
            com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, tooltipItem, mx, my);
        }
    }

    /** Режим редагування — повністю окремий рендер без scissor. */
    private void renderEditMode(MatrixStack g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx   = this.width / 2;
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, cx, 10, GuiTheme.TEXT);

        int iconY        = 51;
        int totalSlotsW  = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft    = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos  = slotsLeft + i * (SLOT_W + SLOT_GAP);
            ItemStack item = editItems.get(i);
            com.wavedefense.gui.GuiCompat.fill(g, xPos + (SLOT_W - 18) / 2 - 1, iconY - 1,
                   xPos + (SLOT_W - 18) / 2 + 19, iconY + 17, GuiTheme.BORDER);
            com.wavedefense.gui.GuiCompat.fill(g, xPos + (SLOT_W - 18) / 2, iconY,
                   xPos + (SLOT_W - 18) / 2 + 18, iconY + 16, GuiTheme.PANEL_DARK);
            int iconX = xPos + (SLOT_W - 16) / 2;
            com.wavedefense.gui.GuiCompat.renderItem(g, item, iconX, iconY);
            com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, item, iconX, iconY);
            if (!item.isEmpty() && mx >= iconX && mx <= iconX + 16
                    && my >= iconY && my <= iconY + 16) {
                com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, item, mx, my);
            }
        }

        for (Object r : this.buttons) {
            if (r instanceof Widget) { Widget w = (Widget) r; w.render(g, mx, my, pt); }
        }
    }

    // ─── Actions ───────────────────────────────────────────────────────────

    private void startAddItem() {
        editingItem  = true;
        editingIndex = -1;
        editItems    = new ArrayList<>();
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuild();
    }

    private void startEditItem(int idx) {
        editingItem  = true;
        editingIndex = idx;
        editItems    = new ArrayList<>(location.getCompletionRewards().get(idx).getItems());
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuild();
    }

    private void setItem(int slot) {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            editItems.set(slot, minecraft.player.getMainHandItem().copy());
            rebuild();
        }
    }

    private void clearItem(int slot) {
        editItems.set(slot, ItemStack.EMPTY);
        rebuild();
    }

    private void saveItem() {
        try {
            int minPts = Integer.parseInt(minPointsInput.getValue());
            List<ItemStack> finalItems = editItems.stream()
                    .filter(i -> !i.isEmpty()).collect(java.util.stream.Collectors.toList());
            if (finalItems.isEmpty()) return;
            ShopItem reward = new ShopItem(finalItems, minPts, 0);
            if (editingIndex >= 0) {
                location.getCompletionRewards().set(editingIndex, reward);
            } else {
                location.addCompletionReward(reward);
            }
            editingItem = false;
            rebuild();
        } catch (NumberFormatException ignored) {}
    }

    private void saveAndBack() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.rewards_saved"), true);
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
