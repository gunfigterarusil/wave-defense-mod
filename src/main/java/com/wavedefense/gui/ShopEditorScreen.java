package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Редактор магазину локації.
 *
 * Режим GLOBAL (звичайний) — один загальний список товарів (попередня поведінка).
 * Режим POINT  (точковий)  — список точок магазину, кожна має власні товари та
 *                             радіус. Гравець відкриє магазин лише поряд з точкою.
 */
public class ShopEditorScreen extends Screen {

    private final Location location;
    private final Screen   parent;

    private int scrollOffsetGlobal = 0;
    private static final int ITEMS_PER_PAGE = 5;

    private int scrollOffsetPoints = 0;
    private static final int POINTS_PER_PAGE = 5;

    // G6a: Підтвердження видалення товару
    private int pendingDeleteShopIndex = -1;
    // В3: Підтвердження видалення точки магазину
    private int pendingDeletePointIndex = -1;

    public ShopEditorScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.title.shop_editor")
                .append(": ").append(location.getName()));
        this.location = location;
        this.parent   = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        // ── Рядок вибору режиму (y=24) ───────────────────────────────
        boolean isPoint = location.isPointShopMode();
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.label.shop_mode"), b -> {}
        ).bounds(cx - 160, 24, 50, 14).build()).active = false;

        this.addRenderableWidget(Button.builder(
            isPoint ? Component.translatable("wavedefense.button.shop_mode_global_off")
                    : Component.translatable("wavedefense.button.shop_mode_global_on"),
            b -> {
                // С6: reset scroll and pending state when switching modes
                scrollOffsetGlobal = 0; scrollOffsetPoints = 0;
                pendingDeleteShopIndex = -1; pendingDeletePointIndex = -1;
                location.setShopMode(Location.ShopMode.GLOBAL); rebuildWidgets();
            }
        ).bounds(cx - 106, 24, 100, 14).build());

        this.addRenderableWidget(Button.builder(
            isPoint ? Component.translatable("wavedefense.button.shop_mode_point_on")
                    : Component.translatable("wavedefense.button.shop_mode_point_off"),
            b -> {
                // С6: reset scroll and pending state when switching modes
                scrollOffsetGlobal = 0; scrollOffsetPoints = 0;
                pendingDeleteShopIndex = -1; pendingDeletePointIndex = -1;
                location.setShopMode(Location.ShopMode.POINT); rebuildWidgets();
            }
        ).bounds(cx - 2, 24, 100, 14).build());

        int y = 42;
        if (!isPoint) {
            buildGlobalView(cx, y);
        } else {
            buildPointView(cx, y);
        }

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.save_back"),
            b -> saveChanges()
        ).bounds(cx - 160, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.cancel"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx - 5, this.height - 28, 110, 20).build());

        // Кнопки імпорту/експорту магазину
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.exp_648cf132"),
            b -> exportShop(isPoint)
        ).bounds(cx + 110, this.height - 28, 42, 20).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.auto.зберегти_магазин_у_файл_server_w_7b502093")));

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.imp_3d6db024"),
            b -> minecraft.setScreen(new ShopImportScreen(location, isPoint, this))
        ).bounds(cx + 156, this.height - 28, 42, 20).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("wavedefense.auto.завантажити_магазин_з_файлу_075366bf")));
    }

    // ─────────────────────────────────────────────────────────────────
    //  GLOBAL
    // ─────────────────────────────────────────────────────────────────
    private void buildGlobalView(int cx, int startY) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.add_shop_item"),
            b -> minecraft.setScreen(new ShopItemEditorScreen(location, -1, this))
        ).bounds(cx - 100, startY, 200, 18).build());
        startY += 22;

        List<ShopItem> items = location.getShopItems();
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int idx = i + scrollOffsetGlobal;
            if (idx >= items.size()) break;
            ShopItem si = items.get(idx);
            int yPos = startY + i * 66;
            String nm = si.getItems().isEmpty()
                ? net.minecraft.client.resources.language.I18n.get("wavedefense.label.empty")
                : si.getItems().get(0).getHoverName().getString();
            if (nm.length() > 25) nm = nm.substring(0, 22) + "...";
            if (si.getItems().size() > 1) nm += " (+" + (si.getItems().size()-1) + ")";
            this.addRenderableWidget(Button.builder(
                Component.literal("§e" + nm), b -> {}
            ).bounds(cx - 140, yPos + 4, 150, 18).build()).active = false;
            final int fi = idx;
            boolean isPendingDelShop = (pendingDeleteShopIndex == fi);
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.edit"),
                b -> { pendingDeleteShopIndex = -1; minecraft.setScreen(new ShopItemEditorScreen(location, fi, this)); }
            ).bounds(cx + 14, yPos, 70, 18).build());
            this.addRenderableWidget(Button.builder(
                isPendingDelShop
                    ? Component.translatable("wavedefense.button.confirm_delete")
                    : Component.translatable("wavedefense.button.delete"),
                b -> {
                    if (isPendingDelShop) {
                        pendingDeleteShopIndex = -1;
                        location.removeShopItem(fi);
                        scrollOffsetGlobal = Math.max(0, Math.min(scrollOffsetGlobal, location.getShopItems().size() - 1));
                        rebuildWidgets();
                    } else {
                        pendingDeleteShopIndex = fi;
                        rebuildWidgets();
                    }
                }
            ).bounds(cx + 88, yPos, 70, 18).build());
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.купити_d_продати_d_05ac8a91", si.getBuyPrice(), si.getSellPrice()),
                b -> {}
            ).bounds(cx - 140, yPos + 48, 280, 12).build()).active = false;
            for (int j = 0; j < Math.min(4, si.getItems().size()); j++)
                this.addRenderableWidget(Button.builder(Component.literal(""), b -> {})
                    .bounds(cx - 140 + j*20, yPos+24, 18, 18).build()).active = false;
        }

        if (items.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (scrollOffsetGlobal>0){scrollOffsetGlobal--;rebuildWidgets();} }
            ).bounds(cx + 145, startY, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (scrollOffsetGlobal+ITEMS_PER_PAGE<items.size()){scrollOffsetGlobal++;rebuildWidgets();} }
            ).bounds(cx + 145, this.height - 52, 18, 18).build());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  POINT
    // ─────────────────────────────────────────────────────────────────
    private void buildPointView(int cx, int startY) {
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.label.shop_point_hint"),
            b -> {}
        ).bounds(cx - 160, startY, 320, 12).build()).active = false;
        startY += 14;

        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.add_shop_point"),
            b -> minecraft.setScreen(new ShopPointEditorScreen(location, -1, this))
        ).bounds(cx - 120, startY, 240, 18).build());
        startY += 22;

        List<ShopPoint> points = location.getShopPoints();

        if (points.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.немає_точок_натисніть_нова_точка_6f5d4a78"), b -> {}
            ).bounds(cx - 160, startY, 320, 18).build()).active = false;
        }

        for (int i = 0; i < Math.min(POINTS_PER_PAGE, points.size()); i++) {
            int idx = i + scrollOffsetPoints;
            if (idx >= points.size()) break;
            ShopPoint sp = points.get(idx);
            int yPos = startY + i * 58;

            // Рядок 1: назва + координати
            String posStr = sp.getPos() != null
                ? String.format("§8 X:%d Y:%d Z:%d (r=%d)", sp.getPos().getX(), sp.getPos().getY(), sp.getPos().getZ(), sp.getRadius())
                : "§c ⚠ без позиції";
            String titleLine = "§6" + sp.getName() + posStr;
            if (titleLine.length() > 60) titleLine = titleLine.substring(0, 58) + "…";
            this.addRenderableWidget(Button.builder(
                Component.literal(titleLine), b -> {}
            ).bounds(cx - 160, yPos, 280, 14).build()).active = false;

            // Рядок 2: кількість товарів
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.товарів_d_0ca6f904", sp.getItems().size()), b -> {}
            ).bounds(cx - 160, yPos + 16, 120, 12).build()).active = false;

            final int fi = idx;
            boolean isPendingDelPoint = (pendingDeletePointIndex == fi);
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.edit"),
                b -> { pendingDeletePointIndex = -1; minecraft.setScreen(new ShopPointEditorScreen(location, fi, this)); }
            ).bounds(cx + 40, yPos + 2, 60, 20).build());
            // В3: two-step confirmation before deleting a shop point
            this.addRenderableWidget(Button.builder(
                isPendingDelPoint
                    ? Component.translatable("wavedefense.button.confirm_delete")
                    : Component.translatable("wavedefense.button.delete"),
                b -> {
                    if (isPendingDelPoint) {
                        pendingDeletePointIndex = -1;
                        location.removeShopPoint(fi);
                        scrollOffsetPoints = Math.max(0, Math.min(scrollOffsetPoints, location.getShopPoints().size() - 1));
                        rebuildWidgets();
                    } else {
                        pendingDeletePointIndex = fi;
                        rebuildWidgets();
                    }
                }
            ).bounds(cx + 104, yPos + 2, 60, 20).build());

            // Іконки першого товару точки (попередній перегляд)
            if (!sp.getItems().isEmpty()) {
                List<ItemStack> preview = sp.getItems().get(0).getItems();
                for (int j = 0; j < Math.min(4, preview.size()); j++)
                    this.addRenderableWidget(Button.builder(Component.literal(""), b -> {})
                        .bounds(cx - 160 + j*20, yPos + 32, 18, 18).build()).active = false;
            }
        }

        if (points.size() > POINTS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                b -> { if (scrollOffsetPoints>0){scrollOffsetPoints--;rebuildWidgets();} }
            ).bounds(cx + 145, startY, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                b -> { if (scrollOffsetPoints+POINTS_PER_PAGE<points.size()){scrollOffsetPoints++;rebuildWidgets();} }
            ).bounds(cx + 145, this.height - 52, 18, 18).build());
        }
    }

    private void saveChanges() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(Component.translatable("wavedefense.auto.магазин_збережено_2ae52f5a"), true);
        minecraft.setScreen(parent);
    }

    private void exportShop(boolean isPoint) {
        if (isPoint) {
            // Точковий режим — пропонуємо вибрати точку
            minecraft.setScreen(new ShopPointSelectExportScreen(location, this));
        } else {
            // Глобальний — відразу експортуємо
            PacketHandler.sendToServer(new com.wavedefense.network.packets.ExportShopPacket(
                location.getName(), "global"));
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(
                    Component.translatable("wavedefense.auto.відправлено_запит_на_збереження_9fd133ca"), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!location.isPointShopMode()) {
            int max = Math.max(0, location.getShopItems().size() - ITEMS_PER_PAGE);
            if (delta > 0 && scrollOffsetGlobal > 0) { scrollOffsetGlobal--; rebuildWidgets(); }
            else if (delta < 0 && scrollOffsetGlobal < max) { scrollOffsetGlobal++; rebuildWidgets(); }
        } else {
            int max = Math.max(0, location.getShopPoints().size() - POINTS_PER_PAGE);
            if (delta > 0 && scrollOffsetPoints > 0) { scrollOffsetPoints--; rebuildWidgets(); }
            else if (delta < 0 && scrollOffsetPoints < max) { scrollOffsetPoints++; rebuildWidgets(); }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        int cx = this.width / 2;

        // Зони: рядок режиму Y=24..38, нижні кнопки Y=height-28..height
        final int MODE_BOT   = 40;
        final int BOTTOM_TOP = this.height - 32;
        GuiTheme.renderContentFrame(g, 8, MODE_BOT - 4, this.width - 8, BOTTOM_TOP + 4);

        // Accent underline shows which mode tab is active
        boolean isPointTab = location.isPointShopMode();
        int activeTabX = isPointTab ? cx - 2 : cx - 106;
        g.fill(activeTabX, 37, activeTabX + 100, 38, GuiTheme.ACCENT);

        // ── Прохід 1: прокручений контент зі scissor ─────────────────
        ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() + w.getHeight() > MODE_BOT && w.getY() < BOTTOM_TOP)
                    w.render(g, mouseX, mouseY, partial);
            }
        }

        // Іконки предметів (поверх кнопок-заглушок, всередині scissor)
        if (!location.isPointShopMode()) {
            int startY = 64;
            List<ShopItem> items = location.getShopItems();
            for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
                int idx = i + scrollOffsetGlobal;
                if (idx >= items.size()) break;
                List<ItemStack> stacks = items.get(idx).getItems();
                int yPos = startY + i * 66;
                for (int j = 0; j < Math.min(4, stacks.size()); j++) {
                    ItemStack st = stacks.get(j);
                    int ix = cx - 140 + j * 20, iy = yPos + 24;
                    g.fill(ix-1,iy-1,ix+17,iy+17,GuiTheme.BORDER);
                    g.fill(ix,iy,ix+16,iy+16,GuiTheme.PANEL_DARK);
                    g.renderItem(st, ix, iy);
                    g.renderItemDecorations(this.font, st, ix, iy);
                    if (mouseX >= ix && mouseX < ix+16 && mouseY >= iy && mouseY < iy+16) {
                        g.flush(); // flush renderItemDecorations text before disabling scissor
                        ScissorHelper.disable();
                        g.renderTooltip(this.font, st, mouseX, mouseY);
                        ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
                    }
                }
                if (items.get(idx).hasAvailabilityTrigger())
                    g.drawString(this.font,
                        "§6[§e" + items.get(idx).getAvailabilityTrigger().label + "§6]",
                        cx - 140 + 84, yPos + 27, 0xFFFFAA00);
            }
        } else {
            int startY = 80;
            List<ShopPoint> points = location.getShopPoints();
            for (int i = 0; i < Math.min(POINTS_PER_PAGE, points.size()); i++) {
                int idx = i + scrollOffsetPoints;
                if (idx >= points.size()) break;
                ShopPoint sp = points.get(idx);
                int yPos = startY + i * 58;
                if (sp.getItems().isEmpty()) continue;
                List<ItemStack> firstItems = sp.getItems().get(0).getItems();
                for (int j = 0; j < Math.min(4, firstItems.size()); j++) {
                    ItemStack st = firstItems.get(j);
                    int ix = cx - 160 + j * 20, iy = yPos + 32;
                    g.fill(ix-1,iy-1,ix+17,iy+17,GuiTheme.BORDER);
                    g.fill(ix,iy,ix+16,iy+16,GuiTheme.PANEL_DARK);
                    g.renderItem(st, ix, iy);
                    g.renderItemDecorations(this.font, st, ix, iy);
                    if (mouseX >= ix && mouseX < ix+16 && mouseY >= iy && mouseY < iy+16) {
                        g.flush(); // flush renderItemDecorations text before disabling scissor
                        ScissorHelper.disable();
                        g.renderTooltip(this.font, st, mouseX, mouseY);
                        ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
                    }
                }
            }
        }
        g.flush();
        ScissorHelper.disable();

        // V3: Scrollbar indicator
        if (!location.isPointShopMode()) {
            GuiTheme.scrollBar(g, this.width - 8, MODE_BOT, BOTTOM_TOP,
                scrollOffsetGlobal, location.getShopItems().size(), ITEMS_PER_PAGE);
        } else {
            GuiTheme.scrollBar(g, this.width - 8, MODE_BOT, BOTTOM_TOP,
                scrollOffsetPoints, location.getShopPoints().size(), POINTS_PER_PAGE);
        }

        // ── Прохід 2: заголовок + рядок вибору режиму (верхня статична зона) ─
        ScissorHelper.enable(0, 0, this.width, MODE_BOT);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() < MODE_BOT)
                w.render(g, mouseX, mouseY, partial);
        }
        g.flush();
        ScissorHelper.disable();

        // ── Прохід 3: нижня статична зона (Зберегти / Exp / Imp) ─────
        ScissorHelper.enable(0, BOTTOM_TOP, this.width, this.height - BOTTOM_TOP);
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getY() >= BOTTOM_TOP)
                w.render(g, mouseX, mouseY, partial);
        }
        g.flush();
        ScissorHelper.disable();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
