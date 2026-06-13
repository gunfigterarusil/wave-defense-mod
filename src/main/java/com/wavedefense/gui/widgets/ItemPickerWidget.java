package com.wavedefense.gui.widgets;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.gui.GuiTheme;
import com.wavedefense.gui.ItemSelectionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;

/**
 * Reusable composite widget for picking a single {@link ItemStack} that should
 * appear identically everywhere admin needs to set an item — wave mob equipment,
 * starting items, completion rewards, shop items, loot drops, etc.
 *
 * <h3>Layout</h3>
 * <pre>
 *  [📦icon][  Select item   ][✋][×][×N]
 * </pre>
 * - <b>Icon</b> (16×16, leftmost) shows the current stack with vanilla decoration.
 * - <b>Select item</b> button opens {@link ItemSelectionScreen} (creative-tab picker).
 * - <b>Hand</b> button copies the player's main-hand item.
 * - <b>Clear</b> button removes the item.
 * - <b>Count box</b> TextFieldWidget (1-64) sets stack count.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ItemPickerWidget picker = new ItemPickerWidget(this, this.font, x, y, totalWidth,
 *         currentStack, newStack -> { items[i] = newStack; rebuild(); });
 * picker.addToScreen(this::addButton);
 * // In render(): picker.renderIcon(g, mouseX, mouseY);
 * }</pre>
 */
public class ItemPickerWidget {

    private static final int ICON_W = 18;
    private static final int HAND_W = 18;
    private static final int CLEAR_W = 16;
    private static final int COUNT_W = 28;
    private static final int GAP = 2;

    private final Screen parent;
    private final Minecraft mc;
    private final FontRenderer font;
    private final int x, y, totalWidth, height;
    private ItemStack current;
    private int count;
    private final Consumer<ItemStack> onChange;

    private Button selectBtn;
    private Button handBtn;
    private Button clearBtn;
    private TextFieldWidget countBox;

    /**
     * @param parent     screen this picker lives on (used to push the picker overlay)
     * @param font       screen font
     * @param x,y        top-left corner of the widget row
     * @param totalWidth total horizontal width to fit the whole composite
     * @param current    initial stack (may be empty)
     * @param onChange   callback whenever stack OR count changes
     */
    public ItemPickerWidget(Screen parent, FontRenderer font, int x, int y, int totalWidth,
                             ItemStack current, Consumer<ItemStack> onChange) {
        this.parent     = parent;
        this.mc         = Minecraft.getInstance();
        this.font       = font;
        this.x          = x;
        this.y          = y;
        this.totalWidth = totalWidth;
        this.height     = 16;
        this.current    = current == null ? ItemStack.EMPTY : current.copy();
        this.count      = this.current.isEmpty() ? 1 : Math.max(1, this.current.getCount());
        this.onChange   = onChange;
    }

    /** Registers all sub-widgets onto the parent screen. */
    public void addToScreen(Consumer<Widget> adder) {
        // Reserve space for icon (rendered manually via renderIcon, not a widget)
        int curX = x + ICON_W + GAP;
        int selectW = Math.max(40, totalWidth - ICON_W - HAND_W - CLEAR_W - COUNT_W - GAP * 5);

        // Select item — opens the creative-tab picker
        selectBtn = new Button(curX, y, selectW, height, current.isEmpty()
                ? new TranslationTextComponent("wavedefense.picker.select")
                : new StringTextComponent("§a✓ " + trunc(current.getHoverName().getString(), Math.max(3, selectW / 6))), b -> mc.setScreen(new ItemSelectionScreen(parent, picked -> {
                current = picked == null ? ItemStack.EMPTY : picked.copy();
                count = current.isEmpty() ? 1 : Math.max(1, Math.min(64, current.getCount()));
                if (countBox != null) countBox.setValue(String.valueOf(count));
                fire();
            }, current)));
        adder.accept(selectBtn);
        curX += selectW + GAP;

        // Hand — copy mainhand item
        handBtn = new Button(curX, y, HAND_W, height, new StringTextComponent("§7✋"), b -> {
                if (mc.player != null) {
                    ItemStack held = mc.player.getMainHandItem();
                    if (!held.isEmpty()) {
                        current = held.copy();
                        count = Math.max(1, Math.min(64, current.getCount()));
                        if (countBox != null) countBox.setValue(String.valueOf(count));
                        fire();
                    }
                }
            });
        /* setTooltip omitted on 1.16.5: handBtn */
        adder.accept(handBtn);
        curX += HAND_W + GAP;

        // Clear — remove item
        clearBtn = new Button(curX, y, CLEAR_W, height, new StringTextComponent("§c×"), b -> { current = ItemStack.EMPTY; count = 1;
                   if (countBox != null) countBox.setValue("1");
                   fire(); });
        /* setTooltip omitted on 1.16.5: clearBtn */
        adder.accept(clearBtn);
        curX += CLEAR_W + GAP;

        // Count box (1-64)
        countBox = new TextFieldWidget(font, curX, y, COUNT_W, height,
            new TranslationTextComponent("wavedefense.picker.count"));
        countBox.setMaxLength(2);
        countBox.setValue(String.valueOf(count));
        countBox.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                count = Math.max(1, Math.min(64, v));
                fire();
            } catch (NumberFormatException ignored) {}
        });
        /* setTooltip omitted on 1.16.5: countBox */
        adder.accept(countBox);
    }

    /** Renders the 16×16 icon at the left of the widget row. Call from screen render(). */
    public void renderIcon(MatrixStack g, int mouseX, int mouseY) {
        int iconX = x + 1;
        int iconY = y;
        com.wavedefense.gui.GuiCompat.fill(g, iconX - 1, iconY - 1, iconX + 17, iconY + 17, GuiTheme.BORDER);
        com.wavedefense.gui.GuiCompat.fill(g, iconX, iconY, iconX + 16, iconY + 16, GuiTheme.PANEL_DARK);
        if (!current.isEmpty()) {
            ItemStack visual = current.copy();
            visual.setCount(Math.max(1, count));
            com.wavedefense.gui.GuiCompat.renderItem(g, visual, iconX, iconY);
            com.wavedefense.gui.GuiCompat.renderItemDecorations(g, font, visual, iconX, iconY);
            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                /* item tooltip on widget not directly supported on 1.16.5: visual */;
            }
        }
    }

    /** Current stack (already includes count). */
    public ItemStack getValue() {
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = current.copy();
        out.setCount(Math.max(1, Math.min(64, count)));
        return out;
    }

    private void fire() {
        if (onChange != null) onChange.accept(getValue());
    }

    private static String trunc(String s, int max) {
        if (s == null) return "";
        if (max < 1) max = 1;
        return s.length() > max ? s.substring(0, Math.max(0, max - 1)) + "…" : s;
    }
}
