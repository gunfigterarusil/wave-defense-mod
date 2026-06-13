package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import com.wavedefense.gui.ScissorHelper;
import net.minecraft.item.ItemStack;

public class StartingItemsScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }

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
        super(new TranslationTextComponent("wavedefense.title.starting_items")
            .append(titleSuffix == null || titleSuffix.isEmpty()
                ? StringTextComponent.EMPTY : new StringTextComponent(": " + titleSuffix)));
        this.parentScreen = parentScreen;
        this.items = items;
        this.titleSuffix = titleSuffix == null ? "" : titleSuffix;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 60;

        this.addButton(new Button(centerX - 150, 35, 300, 20, new TranslationTextComponent("wavedefense.auto.тримайте_предмет_у_руці_та_натис_fe14c625"), button -> {})).active = false;

        this.addButton(new Button(centerX - 100, startY, 200, 20, new TranslationTextComponent("wavedefense.auto.додати_предмет_fc5e5016"), button -> addItem()));

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;

            ItemStack item = items.get(index);
            int yPos = startY + 30 + (i * 30);

            String itemName = item.getHoverName().getString();
            if (itemName.length() > 30) {
                itemName = itemName.substring(0, 27) + "...";
            }

            this.addButton(new Button(centerX - 120, yPos, 200, 20, new StringTextComponent("§e" + itemName + " §7x" + item.getCount()), button -> {})).active = false;

            final int finalIndex = index;
            this.addButton(new Button(centerX + 85, yPos, 20, 20, new StringTextComponent("✕"), button -> removeItem(finalIndex)));
        }

        if (items.size() > ITEMS_PER_PAGE) {
            this.addButton(new Button(centerX + 115, startY + 30, 25, 25, new StringTextComponent("▲"), button -> scrollUp()));

            this.addButton(new Button(centerX + 115, startY + 210, 25, 25, new StringTextComponent("▼"), button -> scrollDown()));
        }

        this.addButton(new Button(centerX - 50, this.height - 30, 100, 20, new TranslationTextComponent("wavedefense.button.done"), button -> this.minecraft.setScreen(parentScreen)));
    }

    private void addItem() {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            items.add(minecraft.player.getMainHandItem().copy());
            this.init();
        }
    }

    private void removeItem(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
            this.init();
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.init();
        }
    }

    private void scrollDown() {
        if (scrollOffset + ITEMS_PER_PAGE < items.size()) {
            scrollOffset++;
            this.init();
        }
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, this.title, this.width / 2, 10, GuiTheme.TEXT);

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
            com.wavedefense.gui.GuiCompat.renderItem(g, item, cx - 145, yPos);
            com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, item, cx - 145, yPos);
        }
        // Widgets — теж у scissor
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y + w.getHeight() > listTop && w.y < listBot) w.render(g, mouseX, mouseY, partialTick);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Статичні widgets (header/footer) поза scissor
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if ((w.y < listTop || w.y >= listBot)) w.render(g, mouseX, mouseY, partialTick);
            }
        }

        // Tooltip (завжди поза scissor)
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;
            ItemStack item = items.get(index);
            int yPos = startY + 30 + (i * 30);
            if (mouseX >= cx - 145 && mouseX <= cx - 145 + 16 && mouseY >= yPos && mouseY <= yPos + 16)
                com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, item, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
