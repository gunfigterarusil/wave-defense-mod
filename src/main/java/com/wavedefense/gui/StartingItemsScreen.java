package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class StartingItemsScreen extends Screen {
    private final Screen parentScreen;
    private final Location location;
    private static final int SLOTS_COUNT = 12;
    private static final int SLOT_SIZE = 18;
    private static final int SLOTS_PER_ROW = 6;

    public StartingItemsScreen(Screen parentScreen, Location location) {
        super(Component.literal("Стартове спорядження"));
        this.parentScreen = parentScreen;
        this.location = location;
        
        // Ініціалізуємо порожні слоти якщо їх немає
        while (location.getStartingItems().size() < SLOTS_COUNT) {
            location.addStartingItem(ItemStack.EMPTY);
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 80;

        // Інструкція
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Тримайте предмет у руці та клікніть по слоту"),
                button -> {}
        ).bounds(centerX - 150, 40, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("§7ПКМ по слоту для очищення"),
                button -> {}
        ).bounds(centerX - 150, 60, 300, 20).build()).active = false;

        // Кнопки керування
        this.addRenderableWidget(Button.builder(
                Component.literal("Очистити все"),
                button -> clearAllItems()
        ).bounds(centerX - 110, this.height - 60, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Готово"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void setItemInSlot(int slotIndex) {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            ItemStack handItem = minecraft.player.getMainHandItem().copy();
            
            // Оновлюємо або додаємо предмет
            if (slotIndex < location.getStartingItems().size()) {
                location.getStartingItems().set(slotIndex, handItem);
            } else {
                while (location.getStartingItems().size() < slotIndex) {
                    location.addStartingItem(ItemStack.EMPTY);
                }
                location.addStartingItem(handItem);
            }
        }
    }

    private void clearSlot(int slotIndex) {
        if (slotIndex < location.getStartingItems().size()) {
            location.getStartingItems().set(slotIndex, ItemStack.EMPTY);
        }
    }

    private void clearAllItems() {
        for (int i = 0; i < location.getStartingItems().size(); i++) {
            location.getStartingItems().set(i, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = this.width / 2;
        int startY = 100;
        
        for (int i = 0; i < SLOTS_COUNT; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;
            int slotX = centerX - (SLOTS_PER_ROW * SLOT_SIZE / 2) + (col * SLOT_SIZE);
            int slotY = startY + (row * SLOT_SIZE);
            
            if (mouseX >= slotX && mouseX <= slotX + 16 &&
                mouseY >= slotY && mouseY <= slotY + 16) {
                
                if (button == 0) { // ЛКМ - встановити предмет
                    setItemInSlot(i);
                } else if (button == 1) { // ПКМ - очистити слот
                    clearSlot(i);
                }
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), 
            this.width / 2, 15, 0xFFFFFF);

        int centerX = this.width / 2;
        int startY = 100;

        // Малюємо слоти та предмети
        for (int i = 0; i < SLOTS_COUNT; i++) {
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;
            int slotX = centerX - (SLOTS_PER_ROW * SLOT_SIZE / 2) + (col * SLOT_SIZE);
            int slotY = startY + (row * SLOT_SIZE);

            // Фон слоту
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
            graphics.fill(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0xFF373737);

            // Підсвітка при наведенні
            if (mouseX >= slotX && mouseX <= slotX + 16 &&
                mouseY >= slotY && mouseY <= slotY + 16) {
                graphics.fill(slotX + 1, slotY + 1, slotX + 15, slotY + 15, 0x80FFFFFF);
            }

            // Малюємо предмет якщо є
            if (i < location.getStartingItems().size()) {
                ItemStack item = location.getStartingItems().get(i);
                if (!item.isEmpty()) {
                    graphics.renderItem(item, slotX, slotY);
                    graphics.renderItemDecorations(this.font, item, slotX, slotY);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        // Tooltip для предметів
        for (int i = 0; i < SLOTS_COUNT; i++) {
            if (i >= location.getStartingItems().size()) continue;
            
            int row = i / SLOTS_PER_ROW;
            int col = i % SLOTS_PER_ROW;
            int slotX = centerX - (SLOTS_PER_ROW * SLOT_SIZE / 2) + (col * SLOT_SIZE);
            int slotY = startY + (row * SLOT_SIZE);

            ItemStack item = location.getStartingItems().get(i);
            if (!item.isEmpty() && mouseX >= slotX && mouseX <= slotX + 16 &&
                mouseY >= slotY && mouseY <= slotY + 16) {
                graphics.renderTooltip(this.font, item, (int)mouseX, (int)mouseY);
            }
        }

        // Інформація про заповнені слоти
        int filledSlots = (int) location.getStartingItems().stream()
            .filter(item -> !item.isEmpty()).count();
        graphics.drawCenteredString(this.font, 
            String.format("§7Заповнено: §e%d§7/§e%d", filledSlots, SLOTS_COUNT),
            this.width / 2, startY + (SLOT_SIZE * 2) + 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
    }
