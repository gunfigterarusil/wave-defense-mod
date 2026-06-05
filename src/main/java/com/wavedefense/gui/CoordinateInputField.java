package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.PlayerEntity;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Compound X/Y/Z coordinate input — bundles three {@link TextFieldWidget} fields and
 * their "§7X:" / "§7Y:" / "§7Z:" label buttons into a single reusable component.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In init():
 * coordField = new CoordinateInputField(this.font, startX, y, labelW, fieldW, height, stride);
 * coordField.setValue(pos);                          // pre-fill with existing BlockPos (or null to clear)
 * coordField.addToScreen(this::addButton); // registers all 6 inner widgets
 *
 * // "📌 My position" button right after the fields:
 * addWidget(Button.builder(new StringTextComponent("📌"), b -> coordField.setFromPlayer(minecraft.player))
 *         .bounds(coordField.getEndX() + gap, y, btnW, height).build());
 *
 * // In save():
 * BlockPos pos = coordField.getValue();  // null if empty/invalid
 * }</pre>
 *
 * <h3>Layout</h3>
 * <pre>
 *  startX
 *   │←labelW→│←── fieldW ──→│← stride - labelW - fieldW →│←labelW→│←── fieldW ──→│ ...
 *  [  §7X:  ][  TextFieldWidget X   ]                              [  §7Y:  ][  TextFieldWidget Y   ] ...
 * </pre>
 * {@code stride} = distance from the start of the X-label to the start of the Y-label.
 * Set {@code stride = labelW + fieldW} for zero gap, or add padding as needed.
 */
public class CoordinateInputField {

    private final TextFieldWidget xInput, yInput, zInput;

    // Stored for addToScreen() label positioning
    private final int startX, y, labelW, h, stride;

    // ─── Constructor ──────────────────────────────────────────────────────

    /**
     * @param font    screen font
     * @param startX  left edge of the "§7X:" label
     * @param y       top Y of all widgets
     * @param labelW  width of each "X:"/"Y:"/"Z:" label button
     * @param fieldW  width of each TextFieldWidget
     * @param height  height of all widgets
     * @param stride  distance from the start of one label to the start of the next
     *                (stride = labelW + fieldW + gap)
     */
    public CoordinateInputField(FontRenderer font, int startX, int y,
                                 int labelW, int fieldW, int height, int stride) {
        this.startX = startX;
        this.y      = y;
        this.labelW = labelW;
        this.h      = height;
        this.stride = stride;

        xInput = box(font, startX + labelW,              y, fieldW, height, "X");
        yInput = box(font, startX + stride + labelW,      y, fieldW, height, "Y");
        zInput = box(font, startX + stride * 2 + labelW,  y, fieldW, height, "Z");
    }

    /**
     * Convenience constructor — stride = labelW + fieldW (zero gap between pairs).
     */
    public CoordinateInputField(FontRenderer font, int startX, int y,
                                 int labelW, int fieldW, int height) {
        this(font, startX, y, labelW, fieldW, height, labelW + fieldW);
    }

    private static TextFieldWidget box(FontRenderer font, int x, int y, int w, int h, String hint) {
        TextFieldWidget b = new TextFieldWidget(font, x, y, w, h, new StringTextComponent(hint));
        b.setMaxLength(7);
        return b;
    }

    // ─── Layout helpers ───────────────────────────────────────────────────

    /**
     * X coordinate of the right edge of the Z-input.
     * Useful for positioning the "📌" button: {@code getEndX() + gap}.
     */
    public int getEndX() {
        return startX + stride * 2 + labelW + (zInput.getWidth());
    }

    // ─── Value API ────────────────────────────────────────────────────────

    /**
     * Pre-fills all three fields from {@code pos}.
     * Passing {@code null} clears the fields (empty strings).
     */
    public void setValue(@Nullable BlockPos pos) {
        if (pos != null) {
            xInput.setValue(String.valueOf(pos.getX()));
            yInput.setValue(String.valueOf(pos.getY()));
            zInput.setValue(String.valueOf(pos.getZ()));
        } else {
            xInput.setValue("");
            yInput.setValue("");
            zInput.setValue("");
        }
    }

    /** {@code true} if all three text fields are blank. */
    public boolean isEmpty() {
        return xInput.getValue().trim().isEmpty()
            && yInput.getValue().trim().isEmpty()
            && zInput.getValue().trim().isEmpty();
    }

    /**
     * Reads the current field values.
     * @return parsed {@link BlockPos}, or {@code null} if all fields are blank or any is invalid.
     */
    @Nullable
    public BlockPos getValue() {
        if (isEmpty()) return null;
        try {
            String sx = xInput.getValue().trim();
            String sy = yInput.getValue().trim();
            String sz = zInput.getValue().trim();
            return new BlockPos(
                    sx.isEmpty() ? 0 : Integer.parseInt(sx),
                    sy.isEmpty() ? 0 : Integer.parseInt(sy),
                    sz.isEmpty() ? 0 : Integer.parseInt(sz));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the parsed value, or {@code fallback} if empty / invalid.
     */
    public BlockPos getValueOrDefault(BlockPos fallback) {
        BlockPos v = getValue();
        return v != null ? v : fallback;
    }

    /**
     * Sets all three fields to the player's current block position.
     * No-op if {@code player} is {@code null}.
     */
    public void setFromPlayer(@Nullable PlayerEntity player) {
        if (player != null) setValue(player.blockPosition());
    }

    // ─── Widget registration ──────────────────────────────────────────────

    /**
     * Adds all six inner widgets (3 inactive label buttons + 3 EditBoxes) to the screen.
     * Call this from {@code init()} after creating the component.
     */
    public void addToScreen(Consumer<Widget> adder) {
        addLabel(adder, "§7X:", startX);
        adder.accept(xInput);
        addLabel(adder, "§7Y:", startX + stride);
        adder.accept(yInput);
        addLabel(adder, "§7Z:", startX + stride * 2);
        adder.accept(zInput);
    }

    /**
     * Adds all six widgets as static (non-scrollable) elements.
     * Equivalent to calling {@link #addToScreen} with a static-adding consumer.
     */
    public void addStaticToScreen(Consumer<Widget> adder) {
        // delegate — same implementation, the adder controls static vs scrollable
        addToScreen(adder);
    }

    private void addLabel(Consumer<Widget> adder, String text, int lx) {
        Button lbl = new Button(lx, y, labelW, h, new StringTextComponent(text), b -> {});
        lbl.active = false;
        adder.accept(lbl);
    }
}
