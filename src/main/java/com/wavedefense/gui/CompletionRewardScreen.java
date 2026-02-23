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

    public CompletionRewardScreen(Location location, Screen parent) {
        super(Component.literal("Нагороди за проходження: " + location.getName()));
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

        // Пояснення
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети видаються гравцю якщо його поінти ≥ мінімуму"), button -> {}
        ).bounds(cx - 160, y, 340, 16).build()).active = false;
        y += 22;

        // Кнопка додати
        this.addRenderableWidget(Button.builder(
                Component.literal("§e➕ Додати предмет-нагороду"), button -> startAddItem()
        ).bounds(cx - 160, y, 210, 20).build());
        y += 28;

        // Список нагород
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

            // Іконки — рендеряться у render()
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
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети нагороди (візьміть у руку → «Встановити»):"), button -> {}
        ).bounds(cx - 160, y, 320, 18).build()).active = false;
        y += 20;

        // 4 слоти: іконка рендериться у render(), кнопки — тут
        int slotW = 75;
        for (int i = 0; i < 4; i++) {
            int xPos = cx - 160 + i * slotW;
            final int si = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Слот " + (i + 1)), button -> {}
            ).bounds(xPos, y, slotW - 4, 12).build()).active = false;
            this.addRenderableWidget(Button.builder(
                    Component.literal("Встановити"), button -> setItem(si)
            ).bounds(xPos, y + 22, slotW - 4, 16).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("Очистити"), button -> clearItem(si)
            ).bounds(xPos, y + 41, slotW - 4, 16).build());
        }
        y += 66;

        // Тільки мінімум поінтів — ніяких "нараховується поінтів"
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Мінімум поінтів у гравця для отримання нагороди:"), button -> {}
        ).bounds(cx - 160, y, 310, 18).build()).active = false;
        y += 20;
        minPointsInput = new EditBox(this.font, cx - 160, y, 80, 20, Component.literal("Поінти"));
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
            // buyPrice = мінімум поінтів, sellPrice = 0
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
            int slotW = 75;
            for (int i = 0; i < 4; i++) {
                int xPos = cx - 160 + i * slotW;
                ItemStack item = editItems.get(i);
                graphics.renderItem(item, xPos + 27, 64);
                graphics.renderItemDecorations(this.font, item, xPos + 27, 64);
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
