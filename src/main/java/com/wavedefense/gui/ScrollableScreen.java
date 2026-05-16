package com.wavedefense.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Базовий клас для GUI екранів зі скролом.
 * Забезпечує: scissor-кліпінг, три-прохідний рендеринг (header/content/footer),
 * mouseScrolled, фільтрацію кліків на прихованих віджетах.
 *
 * Підклас реалізує 4 абстрактних методи:
 * - getClipTop() / getClipBot() — межі scissor-зони
 * - getListSize() — кількість елементів
 * - getItemsPerPage() — скільки поміщається на екрані
 *
 * Віджети header/footer додаються через addStatic() — вони рендеряться поверх контенту
 * і мають пріоритет при обробці кліків.
 */
public abstract class ScrollableScreen extends Screen {

    protected int scrollOffset = 0;

    /** Статичні віджети (header/footer) — не скроляться, мають пріоритет кліків. */
    protected final Set<AbstractWidget> staticWidgets =
            Collections.newSetFromMap(new IdentityHashMap<>());

    protected ScrollableScreen(Component title) {
        super(title);
    }

    // ─── Абстрактні методи ─────────────────────────────────────────────

    /** Верхня межа scissor-зони (Y координата GUI). */
    protected abstract int getClipTop();

    /** Нижня межа scissor-зони (Y координата GUI). */
    protected abstract int getClipBot();

    /** Загальна кількість елементів у списку. */
    protected abstract int getListSize();

    /** Кількість елементів що поміщаються на екрані. */
    protected abstract int getItemsPerPage();

    // ─── Хелпер для додавання статичних віджетів ───────────────────────

    /** Додає віджет як статичний (header/footer) — не обрізається scissor. */
    protected <T extends AbstractWidget> T addStatic(T widget) {
        addRenderableWidget(widget);
        staticWidgets.add(widget);
        return widget;
    }

    // ─── init() ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        staticWidgets.clear();
        clampScroll();
    }

    // ─── Скрол ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0) {
            scrollOffset--;
            rebuildWidgets();
            return true;
        }
        if (delta < 0 && scrollOffset + getItemsPerPage() < getListSize()) {
            scrollOffset++;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /** Clamping scrollOffset після зміни списку. */
    protected void clampScroll() {
        int max = Math.max(0, getListSize() - getItemsPerPage());
        if (scrollOffset > max) scrollOffset = max;
        if (scrollOffset < 0) scrollOffset = 0;
    }

    /** Додає кнопки ▲/▼ якщо список більший за сторінку. */
    protected void addScrollButtons(int x, int topY, int botY, int btnW, int btnH) {
        if (getListSize() > getItemsPerPage()) {
            addStatic(Button.builder(Component.literal("▲"),
                    b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(x, topY, btnW, btnH).build());
            addStatic(Button.builder(Component.literal("▼"),
                    b -> {
                        int max = Math.max(0, getListSize() - getItemsPerPage());
                        if (scrollOffset < max) { scrollOffset++; rebuildWidgets(); }
                    }
            ).bounds(x, botY, btnW, btnH).build());
        }
    }

    // ─── Три-прохідний рендеринг ───────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        GuiTheme.renderContentFrame(g, 8, getClipTop() - 4, this.width - 8, getClipBot() + 4);
        renderHeader(g, mx, my, pt);

        int clipTop = getClipTop(), clipBot = getClipBot();

        // Прохід 1: контент у scissor-зоні
        ScissorHelper.enable(0, clipTop, this.width, Math.max(1, clipBot - clipTop));
        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w && !staticWidgets.contains(w)
                    && w.getY() + w.getHeight() > clipTop && w.getY() < clipBot)
                w.render(g, mx, my, pt);
        }
        renderContentExtra(g, mx, my, pt);
        ScissorHelper.disable();

        // Прохід 2: header-віджети
        ScissorHelper.enable(0, 0, this.width, clipTop);
        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w && staticWidgets.contains(w) && w.getY() < clipTop)
                w.render(g, mx, my, pt);
        }
        ScissorHelper.disable();

        // Прохід 3: footer-віджети
        ScissorHelper.enable(0, clipBot, this.width, this.height - clipBot);
        for (var r : this.renderables) {
            if (r instanceof AbstractWidget w && staticWidgets.contains(w) && w.getY() >= clipBot)
                w.render(g, mx, my, pt);
        }
        ScissorHelper.disable();

        renderOverlay(g, mx, my, pt);
        GuiTheme.scrollBar(g, this.width - 8, getClipTop(), getClipBot(),
                scrollOffset, getListSize(), getItemsPerPage());
    }

    // ─── Фільтрація кліків ─────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int clipTop = getClipTop(), clipBot = getClipBot();

        // Статичні віджети — пріоритет
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && staticWidgets.contains(w)) {
                if (child.mouseClicked(mx, my, button)) {
                    this.setFocused(child);
                    if (button == 0) this.setDragging(true);
                    return true;
                }
            }
        }

        // Контентні віджети — тільки у видимій scissor-зоні
        for (var child : this.children()) {
            if (child instanceof AbstractWidget w && !staticWidgets.contains(w)) {
                if (w.getY() + w.getHeight() <= clipTop || w.getY() >= clipBot) continue;
            }
            if (child.mouseClicked(mx, my, button)) {
                this.setFocused(child);
                if (button == 0) this.setDragging(true);
                return true;
            }
        }
        return false;
    }

    // ─── Хуки для підкласів (опціональні) ──────────────────────────────

    /** Рендер заголовку/тексту перед scissor (drawCenteredString тощо). */
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {}

    /** Рендер контенту всередині scissor-зони (іконки предметів, роздільники). */
    protected void renderContentExtra(GuiGraphics g, int mx, int my, float pt) {}

    /** Рендер поверх усього після scissor (тултіпи, повідомлення про помилки). */
    protected void renderOverlay(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean isPauseScreen() { return false; }
}
