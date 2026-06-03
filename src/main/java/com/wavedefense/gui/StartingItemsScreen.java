package com.wavedefense.gui;

import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.wavedefense.gui.ScissorHelper;
import net.minecraft.world.item.ItemStack;

public class StartingItemsScreen extends Screen {
    private final Screen parentScreen;
    /** v0.2.64: items source is a raw list reference, not necessarily Location.
     *  Backwards-compat constructor still takes Location and delegates. */
    private final java.util.List<ItemStack> items;
    /** Optional title-bar suffix (e.g. team name). Empty for legacy Location usage. */
    private final String titleSuffix;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;

    /** Legacy: edit the location-global starting items. */
    public StartingItemsScreen(Screen parentScreen, Location location) {
        this(parentScreen, location.getStartingItems(), "");
    }

    /** v0.2.64: edit an arbitrary item list (e.g. per-team via PvpSpawnPoint). */
    public StartingItemsScreen(Screen parentScreen, java.util.List<ItemStack> items, String titleSuffix) {
        super(Component.translatable("wavedefense.title.starting_items")
            .append(titleSuffix == null || titleSuffix.isEmpty()
                ? Component.empty() : Component.literal(": " + titleSuffix)));
        this.parentScreen = parentScreen;
        this.items = items;
        this.titleSuffix = titleSuffix == null ? "" : titleSuffix;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.тримайте_предмет_у_руці_та_натис_fe14c625"),
                button -> {}
        ).bounds(centerX - 150, 35, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.додати_предмет_fc5e5016"),
                button -> addItem()
        ).bounds(centerX - 100, startY, 200, 20).build());

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;

            ItemStack item = items.get(index);
            int yPos = startY + 30 + (i * 30);

            String itemName = item.getHoverName().getString();
            if (itemName.length() > 30) {
                itemName = itemName.substring(0, 27) + "...";
            }

            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + itemName + " §7x" + item.getCount()),
                    button -> {}
            ).bounds(centerX - 120, yPos, 200, 20).build()).active = false;

            final int finalIndex = index;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> removeItem(finalIndex)
            ).bounds(centerX + 85, yPos, 20, 20).build());
        }

        if (items.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(centerX + 115, startY + 30, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(centerX + 115, startY + 210, 25, 25).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.done"),
                button -> this.minecraft.setScreen(parentScreen)
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void addItem() {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            items.add(minecraft.player.getMainHandItem().copy());
            this.rebuildWidgets();
        }
    }

    private void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            this.rebuildWidgets();
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < items.size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);

        int cx = this.width / 2;
        int startY = 60;
        // listTop — нижче заголовку, підказки і кнопки "Додати"
        int listTop = 84, listBot = this.height - 34;

        // Іконки предметів — у scissor-зоні
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;
            ItemStack item = items.get(index);
            int yPos = startY + 30 + (i * 30);
            g.renderItem(item, cx - 145, yPos);
            g.renderItemDecorations(this.font, item, cx - 145, yPos);
        }
        // Widgets — теж у scissor
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getY() + w.getHeight() > listTop && w.getY() < listBot)
                w.render(g, mouseX, mouseY, partialTick);
        }
        g.flush();
        ScissorHelper.disable();

        // Статичні widgets (header/footer) поза scissor
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && (w.getY() < listTop || w.getY() >= listBot))
                w.render(g, mouseX, mouseY, partialTick);
        }

        // Tooltip (завжди поза scissor)
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;
            ItemStack item = items.get(index);
            int yPos = startY + 30 + (i * 30);
            if (mouseX >= cx - 145 && mouseX <= cx - 145 + 16 && mouseY >= yPos && mouseY <= yPos + 16)
                g.renderTooltip(this.font, item, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
