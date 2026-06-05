package com.wavedefense.gui.widgets;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Composite widget combining all the coordinate-entry idioms scattered across
 * the mod: manual XYZ EditBoxes, "📌 use my position" button, optional
 * scatter radius TextFieldWidget, and "🗑" clear.
 *
 * <h3>Layout</h3>
 * <pre>
 *  [X: __][Y: __][Z: __] [📌] [r: __] [🗑]
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * coordPicker = new CoordinatePickerWidget(this.font, x, y, currentPos,
 *         currentRadius, /*withRadius=&#42;/true, (pos, radius) -> { ... });
 * coordPicker.addToScreen(this::addButton);
 * }</pre>
 */
public class CoordinatePickerWidget {

    private static final int LABEL_W = 10;   // "X:" / "Y:" / "Z:" / "r:"
    private static final int FIELD_W = 36;   // 7-char integer
    private static final int BTN_W   = 18;   // 📌 / 🗑
    private static final int GAP     = 2;

    private final FontRenderer font;
    private final int x, y, height;
    private final boolean withRadius;
    private final Consumer<Result> onChange;

    private TextFieldWidget xBox, yBox, zBox, rBox;

    /** Combined (pos, radius) result returned by {@link #getValue()}. */
    public static final class Result {
        public final BlockPos pos;     // null if empty / partial / invalid
        public final int radius;       // 0 if not used
        /** True when all 3 coord fields are empty — distinguishes "clear" from
         *  "mid-typing". When true, consumer should clear the underlying value;
         *  when false AND pos is null, consumer should leave the value alone. */
        public final boolean cleared;
        public Result(BlockPos p, int r) { this(p, r, false); }
        public Result(BlockPos p, int r, boolean cleared) {
            this.pos = p; this.radius = r; this.cleared = cleared;
        }
    }

    public CoordinatePickerWidget(FontRenderer font, int x, int y,
                                    @Nullable BlockPos initialPos, int initialRadius,
                                    boolean withRadius,
                                    Consumer<Result> onChange) {
        this.font       = font;
        this.x          = x;
        this.y          = y;
        this.height     = 16;
        this.withRadius = withRadius;
        this.onChange   = onChange;
        // EditBoxes created in addToScreen so position is fresh on rebuilds
        // Stash initial values via temporary capture:
        this._initialPos = initialPos;
        this._initialRadius = initialRadius;
    }

    @Nullable private final BlockPos _initialPos;
    private final int _initialRadius;

    /** Registers all sub-widgets onto the parent screen. */
    public void addToScreen(Consumer<Widget> adder) {
        int curX = x;

        xBox = field(curX + LABEL_W, y, FIELD_W, "X", _initialPos == null ? "" : String.valueOf(_initialPos.getX()));
        adder.accept(label(curX, "§7X:"));
        adder.accept(xBox);
        curX += LABEL_W + FIELD_W + GAP;

        yBox = field(curX + LABEL_W, y, FIELD_W, "Y", _initialPos == null ? "" : String.valueOf(_initialPos.getY()));
        adder.accept(label(curX, "§7Y:"));
        adder.accept(yBox);
        curX += LABEL_W + FIELD_W + GAP;

        zBox = field(curX + LABEL_W, y, FIELD_W, "Z", _initialPos == null ? "" : String.valueOf(_initialPos.getZ()));
        adder.accept(label(curX, "§7Z:"));
        adder.accept(zBox);
        curX += LABEL_W + FIELD_W + GAP;

        // 📌 Use my position
        Button posBtn = new Button(curX, y, BTN_W, height, new StringTextComponent("§e📌"), b -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    BlockPos p = mc.player.blockPosition();
                    xBox.setValue(String.valueOf(p.getX()));
                    yBox.setValue(String.valueOf(p.getY()));
                    zBox.setValue(String.valueOf(p.getZ()));
                    fire();
                }
            });
        /* setTooltip omitted on 1.16.5: posBtn */
        adder.accept(posBtn);
        curX += BTN_W + GAP;

        if (withRadius) {
            rBox = field(curX + LABEL_W, y, FIELD_W - 8, "r", String.valueOf(_initialRadius));
            rBox.setMaxLength(4);
            adder.accept(label(curX, "§7r:"));
            adder.accept(rBox);
            curX += LABEL_W + FIELD_W - 8 + GAP;
        }

        Button clearBtn = new Button(curX, y, BTN_W, height, new StringTextComponent("§c🗑"), b -> {
                xBox.setValue(""); yBox.setValue(""); zBox.setValue("");
                if (rBox != null) rBox.setValue("0");
                fire();
            });
        /* setTooltip omitted on 1.16.5: clearBtn */
        adder.accept(clearBtn);
    }

    /** Creates an TextFieldWidget already positioned at (x, y) — 1.20.1's TextFieldWidget has
     *  no public setBounds, so passing coords via constructor is the cleanest
     *  way to avoid relying on Widget's setX/setY/setWidth trio. */
    private TextFieldWidget field(int fx, int fy, int fw, String hint, String initial) {
        TextFieldWidget b = new TextFieldWidget(font, fx, fy, fw, height, new StringTextComponent(hint));
        b.setMaxLength(7);
        b.setValue(initial);
        b.setResponder(s -> fire());
        return b;
    }

    private Button label(int lx, String text) {
        Button lbl = new Button(lx, y, LABEL_W, height, new StringTextComponent(text), b -> {});
        lbl.active = false;
        return lbl;
    }

    private void fire() {
        if (onChange == null) return;
        Result r = getValue();
        // Suppress the keystroke-by-keystroke partial-state firings: only fire
        // when we have a complete BlockPos OR the user has fully cleared all
        // three fields. Mid-typing produces neither and is silently swallowed.
        if (r.pos == null && !r.cleared) return;
        onChange.accept(r);
    }

    /**
     * Parses fields. Position is non-null ONLY when all 3 components parse
     * cleanly — partially-filled state (e.g. typing "-" or having only X
     * filled while Y/Z are empty) returns {@code null} so the consumer doesn't
     * receive fabricated zero coords for the unfilled axes.
     */
    public Result getValue() {
        BlockPos pos = null;
        String sx = xBox.getValue().trim();
        String sy = yBox.getValue().trim();
        String sz = zBox.getValue().trim();
        boolean allEmpty = sx.isEmpty() && sy.isEmpty() && sz.isEmpty();
        boolean allFilled = !sx.isEmpty() && !sy.isEmpty() && !sz.isEmpty();
        if (allFilled) {
            try {
                pos = new BlockPos(
                    Integer.parseInt(sx), Integer.parseInt(sy), Integer.parseInt(sz));
            } catch (NumberFormatException ignored) { pos = null; }
        }
        // allEmpty → pos stays null (no value); partial → pos stays null (do
        // NOT report a half-valid BlockPos to onChange).
        int r = 0;
        if (rBox != null) {
            try { r = Math.max(0, Integer.parseInt(rBox.getValue().trim())); }
            catch (NumberFormatException ignored) {}
        }
        // Sentinel: distinguish "all empty (clear)" from "partial (do nothing)"
        return new Result(pos, r, allEmpty);
    }
}
