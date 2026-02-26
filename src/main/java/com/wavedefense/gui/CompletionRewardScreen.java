package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CompletionRewardScreen extends Screen {
    private final Location location;
    private final Screen parent;

    private boolean editingItem = false;
    private int editingIndex = -1;
    private List<ItemStack> editItems = new ArrayList<>();
    private EditBox minPointsInput;

    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 4;

    // Шаблон слотів як у ShopItemEditorScreen
    private static final int SLOT_W = 70;
    private static final int SLOT_H = 16;
    private static final int SLOT_GAP = 6;

    public CompletionRewardScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.completion_rewards").append(": ").append(location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 35;

        if (editingItem) {
            initEditMode(cx, y);
            return;
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети видаються гравцю якщо його поінти ≥ мінімуму"), button -> {}
        ).bounds(cx - 160, y, 340, 16).build()).active = false;
        y += 22;

        this.addRenderableWidget(Button.builder(
                Component.literal("§e➕ Додати предмет-нагороду"), button -> startAddItem()
        ).bounds(cx - 160, y, 210, 20).build());
        y += 28;

        List<ShopItem> rewards = location.getCompletionRewards();
        int rowH = 44;
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, rewards.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= rewards.size()) break;
            ShopItem reward = rewards.get(idx);
            int yPos = y + i * rowH;

            List<ItemStack> items = reward.getItems();
            String firstName = items.isEmpty() ? "???" : items.get(0).getHoverName().getString();
            if (firstName.length() > 20) firstName = firstName.substring(0, 17) + "...";
            if (items.size() > 1) firstName += " (+" + (items.size() - 1) + ")";

            String rowLabel = String.format("§e%s §7| Мін. поінтів: §6%d", firstName, reward.getBuyPrice());

            this.addRenderableWidget(Button.builder(
                    Component.literal(rowLabel), button -> {}
            ).bounds(cx - 160, yPos, 260, 18).build()).active = false;

            final int fIdx = idx;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"), button -> startEditItem(fIdx)
            ).bounds(cx + 105, yPos, 24, 18).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"), button -> { location.removeCompletionReward(fIdx); rebuildWidgets(); }
            ).bounds(cx + 132, yPos, 24, 18).build());

            for (int j = 0; j < Math.min(4, items.size()); j++) {
                this.addRenderableWidget(Button.builder(
                        Component.literal(""), button -> {}
                ).bounds(cx - 160 + j * 22, yPos + 20, 20, 20).build()).active = false;
            }
        }

        if (rewards.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    button -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 162, y, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    button -> { if (scrollOffset + ITEMS_PER_PAGE < rewards.size()) { scrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + 162, y + (ITEMS_PER_PAGE - 1) * rowH, 18, 18).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти і назад"), button -> saveAndBack()
        ).bounds(cx - 110, this.height - 28, 220, 20).build());
    }

    private void initEditMode(int cx, int y) {
        // Підпис
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети нагороди (візьміть у руку → «Встановити»):"), button -> {}
        ).bounds(cx - 155, y, 320, 14).build()).active = false;
        y += 16;

        // --- Слоти у стилі ShopItemEditorScreen, без написів "Слот N" ---
        int totalSlotsW = 4 * SLOT_W + 3 * SLOT_GAP;
        int slotsLeft = cx - totalSlotsW / 2;

        for (int i = 0; i < 4; i++) {
            int xPos = slotsLeft + i * (SLOT_W + SLOT_GAP);
            final int si = i;

            // Іконка рендериться у render() на y + 0

            this.addRenderableWidget(Button.builder(
                    Component.literal("Встановити"),
                    button -> setItem(si)
            ).bounds(xPos, y + 20, SLOT_W, SLOT_H).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("Очистити"),
                    button -> clearItem(si)
            ).bounds(xPos, y + 20 + SLOT_H + 2, SLOT_W, SLOT_H).build());
        }

        y += 20 + SLOT_H * 2 + 10;

        // Мінімум поінтів
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Мінімум поінтів у гравця для отримання нагороди:"), button -> {}
        ).bounds(cx - 155, y, 310, 18).build()).active = false;
        y += 20;
        minPointsInput = new EditBox(this.font, cx - 155, y, 80, 20, Component.literal("Поінти"));
        minPointsInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getCompletionRewards().get(editingIndex).getBuyPrice()) : "0");
        minPointsInput.setMaxLength(7);
        this.addRenderableWidget(minPointsInput);
        y += 30;

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), button -> saveItem()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"), button -> { editingItem = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    private void startAddItem() {
        editingItem = true;
        editingIndex = -1;
        editItems = new ArrayList<>();
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void startEditItem(int idx) {
        editingItem = true;
        editingIndex = idx;
        editItems = new ArrayList<>(location.getCompletionRewards().get(idx).getItems());
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
            List<ItemStack> finalItems = editItems.stream().filter(i -> !i.isEmpty()).collect(Collectors.toList());
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
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§a✓ Нагороди збережено!"), true);
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int cx = this.width / 2;

        if (editingItem) {
            // Слоти у стилі ShopItemEditorScreen
            // iconY = y(35) + підпис(16) + відступ(2) = 53
            int iconY = 53;
            int totalSlotsW = 4 * SLOT_W + 3 * SLOT_GAP;
            int slotsLeft = cx - totalSlotsW / 2;

            for (int i = 0; i < 4; i++) {
                int xPos = slotsLeft + i * (SLOT_W + SLOT_GAP);
                ItemStack item = editItems.get(i);

                // Рамка точно як у ShopItemEditorScreen
                graphics.fill(xPos + (SLOT_W - 18) / 2 - 1, iconY - 1,
                        xPos + (SLOT_W - 18) / 2 + 19, iconY + 17, 0xFF555555);
                graphics.fill(xPos + (SLOT_W - 18) / 2, iconY,
                        xPos + (SLOT_W - 18) / 2 + 18, iconY + 16, 0xFF222222);

                int iconX = xPos + (SLOT_W - 16) / 2;
                graphics.renderItem(item, iconX, iconY);
                graphics.renderItemDecorations(this.font, item, iconX, iconY);

                if (!item.isEmpty() && mouseX >= iconX && mouseX <= iconX + 16
                        && mouseY >= iconY && mouseY <= iconY + 16) {
                    graphics.renderTooltip(this.font, item, mouseX, mouseY);
                }
            }
        } else {
            int listY = 85;
            int rowH = 44;
            List<ShopItem> rewards = location.getCompletionRewards();
            for (int i = 0; i < Math.min(ITEMS_PER_PAGE, rewards.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= rewards.size()) break;
                List<ItemStack> items = rewards.get(idx).getItems();
                int yPos = listY + i * rowH;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    graphics.renderItem(items.get(j), cx - 160 + j * 22, yPos + 20);
                    graphics.renderItemDecorations(this.font, items.get(j), cx - 160 + j * 22, yPos + 20);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
