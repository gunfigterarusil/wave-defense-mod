package com.wavedefense.gui.widgets;

import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.gui.GuiTheme;
import net.minecraft.client.gui.FontRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable list/tile toggle for paginated content (mobs, items, coordinates, waves).
 *
 * <p>Subclass-style usage: construct with a callback for each entry's row/tile render,
 * then call {@link #addToScreen} to register the view-mode toggle button and
 * {@link #renderEntries} from the screen's render() to draw items.
 *
 * <p>Each "entry" is opaque — you provide a {@link Renderer} that knows how to
 * draw one entry in list mode (full-width row) and one in tile mode (compact card).
 *
 * <h3>Layout — list mode</h3>
 * <pre>
 *   ┌──────────────────────────────────┐
 *   │ Entry 1                          │
 *   ├──────────────────────────────────┤
 *   │ Entry 2                          │
 *   └──────────────────────────────────┘
 * </pre>
 *
 * <h3>Layout — tile mode</h3>
 * <pre>
 *   ┌───┬───┬───┬───┐
 *   │ 1 │ 2 │ 3 │ 4 │
 *   ├───┼───┼───┼───┤
 *   │ 5 │ 6 │ 7 │ 8 │
 *   └───┴───┴───┴───┘
 * </pre>
 *
 * <p>The view itself does NOT track the data — pass {@code items} list to
 * {@link #renderEntries}. Pagination state ({@code scrollOffset}, {@code viewMode})
 * is owned by the view.
 *
 * @param <T> entry type — anything with which {@link Renderer} can paint a row/tile
 */
public class ListTileView<T> {

    /** Caller-provided rendering strategy. */
    public interface Renderer<T> {
        /** Paint one entry as a full-width list row at (x, y) of size (w, h). */
        void renderRow(MatrixStack g, T entry, int x, int y, int w, int h, int mouseX, int mouseY);
        /** Paint one entry as a compact tile at (x, y) of size (w, h). */
        void renderTile(MatrixStack g, T entry, int x, int y, int w, int h, int mouseX, int mouseY);
    }

    public enum Mode { LIST, TILE }

    private Mode mode;
    private int scrollOffset = 0;
    private final int rowH;
    private final int tileW;
    private final int tileH;
    private final int gap;
    private final Renderer<T> renderer;

    private final Consumer<Mode> onModeChange;  // null-safe

    public ListTileView(Mode initialMode, int rowH, int tileW, int tileH, int gap,
                          Renderer<T> renderer, Consumer<Mode> onModeChange) {
        this.mode         = initialMode;
        this.rowH         = rowH;
        this.tileW        = tileW;
        this.tileH        = tileH;
        this.gap          = gap;
        this.renderer     = renderer;
        this.onModeChange = onModeChange;
    }

    /** Adds the view-mode toggle button to the screen. */
    public Widget addToggleButton(Consumer<Widget> adder, int x, int y, int w, int h) {
        Button btn = new Button(x, y, w, h, new TranslationTextComponent(mode == Mode.TILE
                ? "wavedefense.shop.view_tiles" : "wavedefense.shop.view_list"), b -> {
                mode = (mode == Mode.TILE) ? Mode.LIST : Mode.TILE;
                scrollOffset = 0;
                if (onModeChange != null) onModeChange.accept(mode);
            });
        adder.accept(btn);
        return btn;
    }

    /** Computes how many entries fit on one page given the available space. */
    public int getItemsPerPage(int areaW, int areaH) {
        if (mode == Mode.LIST) {
            return Math.max(1, areaH / (rowH + gap));
        }
        int cols = Math.max(1, (areaW + gap) / (tileW + gap));
        int rows = Math.max(1, (areaH + gap) / (tileH + gap));
        return cols * rows;
    }

    /** Renders the visible page of entries inside the (x, y, w, h) area. */
    public void renderEntries(MatrixStack g, List<T> entries, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        if (entries == null || entries.isEmpty()) return;
        int perPage = getItemsPerPage(w, h);

        if (mode == Mode.LIST) {
            int curY = y;
            for (int i = 0; i < perPage; i++) {
                int idx = scrollOffset + i;
                if (idx >= entries.size()) break;
                renderer.renderRow(g, entries.get(idx), x, curY, w, rowH, mouseX, mouseY);
                curY += rowH + gap;
            }
        } else {
            int cols = Math.max(1, (w + gap) / (tileW + gap));
            for (int i = 0; i < perPage; i++) {
                int idx = scrollOffset + i;
                if (idx >= entries.size()) break;
                int col = i % cols;
                int row = i / cols;
                int tx = x + col * (tileW + gap);
                int ty = y + row * (tileH + gap);
                renderer.renderTile(g, entries.get(idx), tx, ty, tileW, tileH, mouseX, mouseY);
            }
        }
    }

    /** Background frame for the visible page — matches GuiTheme. */
    public void renderFrame(MatrixStack g, int x, int y, int w, int h) {
        GuiTheme.renderContentFrame(g, x - 2, y - 2, x + w + 2, y + h + 2);
    }

    // ─── State ────────────────────────────────────────────────────────────

    public Mode getMode() { return mode; }
    public void setMode(Mode m) { this.mode = m; scrollOffset = 0; }
    public int  getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int o) { this.scrollOffset = Math.max(0, o); }

    /** Scroll by one row/tile-row. Clamps automatically. */
    public boolean scroll(int delta, int totalItems, int areaW, int areaH) {
        int perPage = getItemsPerPage(areaW, areaH);
        int max = Math.max(0, totalItems - perPage);
        int next = Math.max(0, Math.min(max, scrollOffset + delta));
        if (next != scrollOffset) { scrollOffset = next; return true; }
        return false;
    }
}
