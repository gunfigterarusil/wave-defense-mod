package com.wavedefense.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Base class for list-based CRUD admin screens.
 *
 * Extends {@link ScrollableScreen} and provides:
 * <ul>
 *   <li>{@link #getListSize()} — derived from {@link #getItems()}</li>
 *   <li>{@link #getItemsPerPage()} — derived from clip bounds and row height</li>
 *   <li>{@link #renderContentExtra} — iterates visible items and calls {@link #renderRow}</li>
 *   <li>{@link #buildVisibleRows()} — helper for subclass {@code init()} to add row widgets</li>
 * </ul>
 *
 * Subclasses are responsible for their own header/footer buttons and scroll-button placement
 * (call {@link #addScrollButtons} from {@code init()} with the desired position).
 */
public abstract class ListEditorScreen<T> extends ScrollableScreen {

    /** Parent screen to return to on close; may be null. */
    protected final Screen parent;

    protected ListEditorScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected ListEditorScreen(Component title) {
        this(title, null);
    }

    // ─── Abstract API ──────────────────────────────────────────────────────

    /** The full list of items (not paginated). */
    protected abstract List<T> getItems();

    /** Pixel height of one row (including any spacing). */
    protected abstract int getRowHeight();

    /**
     * Absolute Y coordinate of the first visible row.
     * Must equal or exceed {@link #getClipTop()}.
     */
    protected abstract int getStartY();

    /**
     * Add widgets for one row. Called once per visible item from {@link #buildVisibleRows()}.
     *
     * @param cx    horizontal centre of the screen
     * @param y     top-left Y of this row
     * @param item  the item for this row
     * @param index absolute index in {@link #getItems()}
     */
    protected abstract void buildRowWidgets(int cx, int y, T item, int index);

    /**
     * Optional: draw non-widget content for one row (text, icons, etc.).
     * Default implementation is a no-op — override only when needed.
     */
    protected void renderRow(GuiGraphics g, int cx, int y, T item, int index, int mx, int my) {}

    // ─── ScrollableScreen overrides ────────────────────────────────────────

    @Override
    protected int getListSize() {
        return getItems().size();
    }

    @Override
    protected int getItemsPerPage() {
        int rowH = Math.max(1, getRowHeight());
        return Math.max(1, (getClipBot() - getStartY()) / rowH);
    }

    // ─── Helpers for subclass init() ───────────────────────────────────────

    /**
     * Adds row widgets for every currently visible item.
     * Call this from {@code init()} after the header is built.
     */
    protected final void buildVisibleRows() {
        int cx = this.width / 2;
        List<T> items = getItems();
        int perPage = getItemsPerPage();
        for (int i = 0; i < perPage && (i + scrollOffset) < items.size(); i++) {
            int index = i + scrollOffset;
            int y = getStartY() + i * getRowHeight();
            buildRowWidgets(cx, y, items.get(index), index);
        }
    }

    // ─── Rendering ────────────────────────────────────────────────────────

    @Override
    protected void renderContentExtra(GuiGraphics g, int mx, int my, float pt) {
        int cx = this.width / 2;
        List<T> items = getItems();
        int perPage = getItemsPerPage();
        for (int i = 0; i < perPage && (i + scrollOffset) < items.size(); i++) {
            int index = i + scrollOffset;
            int y = getStartY() + i * getRowHeight();
            int rowW = Math.min(520, this.width - 36);
            int rowX = cx - rowW / 2;
            boolean hovered = mx >= rowX && mx <= rowX + rowW
                    && my >= y - 2 && my <= y + getRowHeight() - 2;
            GuiTheme.card(g, rowX, y - 2, rowX + rowW, y + getRowHeight() - 3, hovered);
            renderRow(g, cx, y, items.get(index), index, mx, my);
        }
    }
}
