package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;

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

    // View mode toggle — list (default) or tile (icon grid).
    // Persists per screen instance; defaults to list for backward compat with
    // existing admin habits. Tile mode matches PlayerShopScreen visuals.
    private boolean tileMode = false;
    private static final int TILE_W   = 112;
    private static final int TILE_H   = 84;
    private static final int TILE_GAP = 8;

    public ShopEditorScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.title.shop_editor")
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
        this.addButton(new Button(cx - 160, 24, 50, 14, new TranslationTextComponent("wavedefense.label.shop_mode"), b -> {})).active = false;

        this.addButton(new Button(cx - 106, 24, 100, 14, isPoint ? new TranslationTextComponent("wavedefense.button.shop_mode_global_off")
                    : new TranslationTextComponent("wavedefense.button.shop_mode_global_on"), b -> {
                // С6: reset scroll and pending state when switching modes
                scrollOffsetGlobal = 0; scrollOffsetPoints = 0;
                pendingDeleteShopIndex = -1; pendingDeletePointIndex = -1;
                location.setShopMode(Location.ShopMode.GLOBAL); init();
            }));

        this.addButton(new Button(cx - 2, 24, 100, 14, isPoint ? new TranslationTextComponent("wavedefense.button.shop_mode_point_on")
                    : new TranslationTextComponent("wavedefense.button.shop_mode_point_off"), b -> {
                // С6: reset scroll and pending state when switching modes
                scrollOffsetGlobal = 0; scrollOffsetPoints = 0;
                pendingDeleteShopIndex = -1; pendingDeletePointIndex = -1;
                location.setShopMode(Location.ShopMode.POINT); init();
            }));

        int y = 42;
        if (!isPoint) {
            buildGlobalView(cx, y);
        } else {
            buildPointView(cx, y);
        }

        this.addButton(new Button(cx - 160, this.height - 28, 150, 20, new TranslationTextComponent("wavedefense.button.save_back"), b -> saveChanges()));
        this.addButton(new Button(cx - 5, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> this.minecraft.setScreen(parent)));

        // Кнопки імпорту/експорту магазину
        this.addButton(new Button(cx + 110, this.height - 28, 42, 20, new TranslationTextComponent("wavedefense.auto.exp_648cf132"), b -> exportShop(isPoint)))
        /* setTooltip omitted on 1.16.5 */;

        this.addButton(new Button(cx + 156, this.height - 28, 42, 20, new TranslationTextComponent("wavedefense.auto.imp_3d6db024"), b -> minecraft.setScreen(new ShopImportScreen(location, isPoint, this))))
        /* setTooltip omitted on 1.16.5 */;
    }

    // ─────────────────────────────────────────────────────────────────
    //  GLOBAL
    // ─────────────────────────────────────────────────────────────────
    private void buildGlobalView(int cx, int startY) {
        // C4: Tacz bulk-add button — visible only when Tacz is loaded.
        // Layout: "Add item" shrinks to make room for the Tacz button on the right.
        boolean taczOn = false /* TaczCompat not ported on 1.16.5 */;
        int addW = taczOn ? 100 : 160;
        this.addButton(new Button(cx - 100, startY, addW, 18, new TranslationTextComponent("wavedefense.button.add_shop_item"), b -> minecraft.setScreen(new ShopItemEditorScreen(location, -1, this))));
        if (taczOn) {
            this.addButton(new Button(cx + 6, startY, 50, 18, new TranslationTextComponent("wavedefense.tacz.bulk.open_button"), b -> minecraft.setScreen(/* not ported */ null)));
        }
        // View-mode toggle — matches PlayerShopScreen UX
        this.addButton(new Button(cx + 60, startY, 40, 18, new TranslationTextComponent(tileMode ? "wavedefense.shop.view_tiles" : "wavedefense.shop.view_list"), b -> { tileMode = !tileMode; scrollOffsetGlobal = 0; init(); }));
        startY += 22;

        List<ShopItem> items = location.getShopItems();
        if (tileMode) {
            buildGlobalViewTiles(cx, startY, items);
            return;
        }
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, items.size()); i++) {
            int idx = i + scrollOffsetGlobal;
            if (idx >= items.size()) break;
            ShopItem si = items.get(idx);
            int yPos = startY + i * 66;
            String nm = si.getItems().isEmpty()
                ? net.minecraft.client.resources.I18n.get("wavedefense.label.empty")
                : si.getItems().get(0).getHoverName().getString();
            if (nm.length() > 25) nm = nm.substring(0, 22) + "...";
            if (si.getItems().size() > 1) nm += " (+" + (si.getItems().size()-1) + ")";
            this.addButton(new Button(cx - 140, yPos + 4, 150, 18, new StringTextComponent("§e" + nm), b -> {})).active = false;
            final int fi = idx;
            boolean isPendingDelShop = (pendingDeleteShopIndex == fi);
            this.addButton(new Button(cx + 14, yPos, 70, 18, new TranslationTextComponent("wavedefense.button.edit"), b -> { pendingDeleteShopIndex = -1; minecraft.setScreen(new ShopItemEditorScreen(location, fi, this)); }));
            this.addButton(new Button(cx + 88, yPos, 70, 18, isPendingDelShop
                    ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                    : new TranslationTextComponent("wavedefense.button.delete"), b -> {
                    if (isPendingDelShop) {
                        pendingDeleteShopIndex = -1;
                        location.removeShopItem(fi);
                        scrollOffsetGlobal = Math.max(0, Math.min(scrollOffsetGlobal, Math.max(0, location.getShopItems().size() - ITEMS_PER_PAGE)));
                        init();
                    } else {
                        pendingDeleteShopIndex = fi;
                        init();
                    }
                }));
            this.addButton(new Button(cx - 140, yPos + 48, 280, 12, new TranslationTextComponent("wavedefense.auto.купити_d_продати_d_05ac8a91", si.getBuyPrice(), si.getSellPrice()), b -> {})).active = false;
            for (int j = 0; j < Math.min(4, si.getItems().size()); j++)
                this.addButton(new Button(cx - 140 + j*20, yPos+24, 18, 18, new StringTextComponent(""), b -> {})).active = false;
        }

        if (items.size() > ITEMS_PER_PAGE) {
            this.addButton(new Button(cx + 145, startY, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffsetGlobal>0){scrollOffsetGlobal--;init();} }));
            this.addButton(new Button(cx + 145, this.height - 52, 18, 18, new StringTextComponent("▼"), b -> { if (scrollOffsetGlobal+ITEMS_PER_PAGE<items.size()){scrollOffsetGlobal++;init();} }));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  GLOBAL — tile (grid) view
    // ─────────────────────────────────────────────────────────────────
    private void buildGlobalViewTiles(int cx, int startY, List<ShopItem> items) {
        int cols = getTileCols();
        int gridW = cols * TILE_W + (cols - 1) * TILE_GAP;
        int gridX = cx - gridW / 2;
        int rows = Math.max(1, (this.height - startY - 60) / TILE_H);
        int perPage = cols * rows;

        for (int i = 0; i < Math.min(perPage, items.size()); i++) {
            int idx = i + scrollOffsetGlobal;
            if (idx >= items.size()) break;
            int col = i % cols;
            int row = i / cols;
            int x = gridX + col * (TILE_W + TILE_GAP);
            int y = startY + row * TILE_H;
            final int fi = idx;
            boolean isPendingDelShop = (pendingDeleteShopIndex == fi);

            // Edit button — bottom-left of the tile
            this.addButton(new Button(x + 4, y + TILE_H - 22, (TILE_W - 12) / 2, 16, new TranslationTextComponent("wavedefense.button.edit"), b -> { pendingDeleteShopIndex = -1; minecraft.setScreen(new ShopItemEditorScreen(location, fi, this)); }));

            // Delete button — bottom-right of the tile, with confirm
            this.addButton(new Button(x + 4 + (TILE_W - 12) / 2 + 4, y + TILE_H - 22, (TILE_W - 12) / 2, 16, isPendingDelShop
                    ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                    : new TranslationTextComponent("wavedefense.button.delete"), b -> {
                    if (isPendingDelShop) {
                        pendingDeleteShopIndex = -1;
                        location.removeShopItem(fi);
                        int maxOff = Math.max(0, location.getShopItems().size() - perPage);
                        scrollOffsetGlobal = Math.max(0, Math.min(scrollOffsetGlobal, maxOff));
                        init();
                    } else {
                        pendingDeleteShopIndex = fi;
                        init();
                    }
                }));
        }

        if (items.size() > perPage) {
            this.addButton(new Button(gridX + gridW + 4, startY, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffsetGlobal > 0) { scrollOffsetGlobal--; init(); } }));
            this.addButton(new Button(gridX + gridW + 4, this.height - 52, 18, 18, new StringTextComponent("▼"), b -> { if (scrollOffsetGlobal + perPage < items.size()) { scrollOffsetGlobal++; init(); } }));
        }
    }

    private int getTileCols() {
        int usableW = Math.max(TILE_W, this.width - 60);
        return Math.max(1, Math.min(5, (usableW + TILE_GAP) / (TILE_W + TILE_GAP)));
    }

    // ─────────────────────────────────────────────────────────────────
    //  POINT
    // ─────────────────────────────────────────────────────────────────
    private void buildPointView(int cx, int startY) {
        this.addButton(new Button(cx - 160, startY, 320, 12, new TranslationTextComponent("wavedefense.label.shop_point_hint"), b -> {})).active = false;
        startY += 14;

        this.addButton(new Button(cx - 120, startY, 240, 18, new TranslationTextComponent("wavedefense.button.add_shop_point"), b -> minecraft.setScreen(new ShopPointEditorScreen(location, -1, this))));
        startY += 22;

        List<ShopPoint> points = location.getShopPoints();

        if (points.isEmpty()) {
            this.addButton(new Button(cx - 160, startY, 320, 18, new TranslationTextComponent("wavedefense.auto.немає_точок_натисніть_нова_точка_6f5d4a78"), b -> {})).active = false;
        }

        for (int i = 0; i < Math.min(POINTS_PER_PAGE, points.size()); i++) {
            int idx = i + scrollOffsetPoints;
            if (idx >= points.size()) break;
            ShopPoint sp = points.get(idx);
            int yPos = startY + i * 58;

            // Рядок 1: назва + координати
            String posStr = sp.getPos() != null
                ? String.format("§8 X:%d Y:%d Z:%d (r=%d)", sp.getPos().getX(), sp.getPos().getY(), sp.getPos().getZ(), sp.getRadius())
                : I18n.get("wavedefense.location.shop_no_pos");
            String titleLine = "§6" + sp.getName() + posStr;
            if (titleLine.length() > 60) titleLine = titleLine.substring(0, 58) + "…";
            this.addButton(new Button(cx - 160, yPos, 280, 14, new StringTextComponent(titleLine), b -> {})).active = false;

            // Рядок 2: кількість товарів
            this.addButton(new Button(cx - 160, yPos + 16, 120, 12, new TranslationTextComponent("wavedefense.auto.товарів_d_0ca6f904", sp.getItems().size()), b -> {})).active = false;

            final int fi = idx;
            boolean isPendingDelPoint = (pendingDeletePointIndex == fi);
            this.addButton(new Button(cx + 40, yPos + 2, 60, 20, new TranslationTextComponent("wavedefense.button.edit"), b -> { pendingDeletePointIndex = -1; minecraft.setScreen(new ShopPointEditorScreen(location, fi, this)); }));
            // В3: two-step confirmation before deleting a shop point
            this.addButton(new Button(cx + 104, yPos + 2, 60, 20, isPendingDelPoint
                    ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                    : new TranslationTextComponent("wavedefense.button.delete"), b -> {
                    if (isPendingDelPoint) {
                        pendingDeletePointIndex = -1;
                        location.removeShopPoint(fi);
                        scrollOffsetPoints = Math.max(0, Math.min(scrollOffsetPoints, Math.max(0, location.getShopPoints().size() - POINTS_PER_PAGE)));
                        init();
                    } else {
                        pendingDeletePointIndex = fi;
                        init();
                    }
                }));

            // Іконки першого товару точки (попередній перегляд)
            if (!sp.getItems().isEmpty()) {
                List<ItemStack> preview = sp.getItems().get(0).getItems();
                for (int j = 0; j < Math.min(4, preview.size()); j++)
                    this.addButton(new Button(cx - 160 + j*20, yPos + 32, 18, 18, new StringTextComponent(""), b -> {})).active = false;
            }
        }

        if (points.size() > POINTS_PER_PAGE) {
            this.addButton(new Button(cx + 145, startY, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffsetPoints>0){scrollOffsetPoints--;init();} }));
            this.addButton(new Button(cx + 145, this.height - 52, 18, 18, new StringTextComponent("▼"), b -> { if (scrollOffsetPoints+POINTS_PER_PAGE<points.size()){scrollOffsetPoints++;init();} }));
        }
    }

    /** v0.2.64: chunked-save threshold. Shops with more than this many items
     *  go through {@link com.wavedefense.network.packets.ReplaceShopItemsPacket}
     *  instead of a single oversized UpdateLocationPacket. 50 chosen so that
     *  one chunk × ~25 items × ~300 bytes/item ≈ 7-8 KB, well under the
     *  Forge channel limit. */
    private static final int CHUNK_THRESHOLD = 50;
    private static final int CHUNK_SIZE      = 25;

    private void saveChanges() {
        int itemCount = location.getShopItems().size();
        if (itemCount > CHUNK_THRESHOLD) {
            // Chunked path: temporarily strip shopItems from the location, send
            // the rest via UpdateLocationPacket, then send shopItems in chunks
            // via ReplaceShopItemsPacket. Server reassembles and broadcasts.
            sendShopChunked();
        } else {
            PacketHandler.sendToServer(new UpdateLocationPacket(location));
        }
        if (minecraft.player != null) {
            if (itemCount > CHUNK_THRESHOLD) {
                int chunks = (itemCount + CHUNK_SIZE - 1) / CHUNK_SIZE;
                minecraft.player.displayClientMessage(
                    new TranslationTextComponent("wavedefense.msg.shop_chunked", itemCount, chunks), false);
            }
            minecraft.player.displayClientMessage(
                new TranslationTextComponent("wavedefense.auto.магазин_збережено_2ae52f5a"), true);
        }
        minecraft.setScreen(parent);
    }

    /** v0.2.64: send the shop in {@value #CHUNK_SIZE}-item chunks via
     *  ReplaceShopItemsPacket. We still send a metadata-only UpdateLocationPacket
     *  first so the rest of the Location (waves, loot, etc.) gets sync'd —
     *  but with shopItems emptied so it stays under the channel limit. */
    private void sendShopChunked() {
        java.util.List<com.wavedefense.data.ShopItem> all = new java.util.ArrayList<>(location.getShopItems());
        // 1) Send metadata (no shop items) by temporarily stripping the list
        java.util.List<com.wavedefense.data.ShopItem> backup = new java.util.ArrayList<>(location.getShopItems());
        location.getShopItems().clear();
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        // Restore client-side so the editor still shows them after Save
        location.getShopItems().addAll(backup);
        // 2) Stream items in chunks
        int totalChunks = (all.size() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int i = 0; i < totalChunks; i++) {
            int from = i * CHUNK_SIZE;
            int to   = Math.min(all.size(), from + CHUNK_SIZE);
            java.util.List<com.wavedefense.data.ShopItem> chunk =
                new java.util.ArrayList<>(all.subList(from, to));
            PacketHandler.sendToServer(
                new com.wavedefense.network.packets.ReplaceShopItemsPacket(
                    location.getName(), i, totalChunks, chunk));
        }
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
                    new TranslationTextComponent("wavedefense.auto.відправлено_запит_на_збереження_9fd133ca"), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!location.isPointShopMode()) {
            int max = Math.max(0, location.getShopItems().size() - ITEMS_PER_PAGE);
            if (delta > 0 && scrollOffsetGlobal > 0) { scrollOffsetGlobal--; init(); }
            else if (delta < 0 && scrollOffsetGlobal < max) { scrollOffsetGlobal++; init(); }
        } else {
            int max = Math.max(0, location.getShopPoints().size() - POINTS_PER_PAGE);
            if (delta > 0 && scrollOffsetPoints > 0) { scrollOffsetPoints--; init(); }
            else if (delta < 0 && scrollOffsetPoints < max) { scrollOffsetPoints++; init(); }
        }
        return true;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partial) {
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
        com.wavedefense.gui.GuiCompat.fill(g, activeTabX, 37, activeTabX + 100, 38, GuiTheme.ACCENT);

        // ── Прохід 1: прокручений контент зі scissor ─────────────────
        ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) { net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y + w.getHeight() > MODE_BOT && w.y < BOTTOM_TOP)
                    w.render(g, mouseX, mouseY, partial);
            }
        }

        // Іконки предметів (поверх кнопок-заглушок, всередині scissor)
        if (!location.isPointShopMode() && tileMode) {
            // ── Tile mode: render icon grid with prices ──────────────────
            int startY = 64;
            List<ShopItem> items = location.getShopItems();
            int cols = getTileCols();
            int gridW = cols * TILE_W + (cols - 1) * TILE_GAP;
            int gridX = cx - gridW / 2;
            int rows = Math.max(1, (this.height - startY - 60) / TILE_H);
            int perPage = cols * rows;
            ItemStack tooltipItem = null;
            int tooltipMx = 0, tooltipMy = 0;
            for (int i = 0; i < Math.min(perPage, items.size()); i++) {
                int idx = i + scrollOffsetGlobal;
                if (idx >= items.size()) break;
                ShopItem si = items.get(idx);
                int col = i % cols, row = i / cols;
                int x = gridX + col * (TILE_W + TILE_GAP);
                int y = startY + row * TILE_H;
                boolean isPendingDel = pendingDeleteShopIndex == idx;

                // Tile background — red tint when pending delete
                int bg     = isPendingDel ? 0x44440000 : 0x22335533;
                int border = isPendingDel ? 0x88AA5555 : 0x8855AA55;
                com.wavedefense.gui.GuiCompat.fill(g, x, y, x + TILE_W, y + TILE_H - 2, bg);
                com.wavedefense.gui.GuiCompat.fill(g, x, y, x + TILE_W, y + 1, border);

                // Primary icon (first ItemStack in slot)
                List<ItemStack> stacks = si.getItems();
                ItemStack first = stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
                int iconX = x + TILE_W / 2 - 8;
                int iconY = y + 6;
                com.wavedefense.gui.GuiCompat.fill(g, iconX - 1, iconY - 1, iconX + 17, iconY + 17, GuiTheme.BORDER);
                com.wavedefense.gui.GuiCompat.fill(g, iconX, iconY, iconX + 16, iconY + 16, GuiTheme.PANEL_DARK);
                com.wavedefense.gui.GuiCompat.renderItem(g, first, iconX, iconY);
                com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, first, iconX, iconY);
                if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= iconY && mouseY < iconY + 16) {
                    tooltipItem = first; tooltipMx = mouseX; tooltipMy = mouseY;
                }

                // Item count badge (+N other stacks)
                if (stacks.size() > 1) {
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "+" + (stacks.size() - 1),
                        x + TILE_W - 14, y + 6, 0xFFE680);
                }

                // Name + price line
                String nm = first.isEmpty()
                    ? I18n.get("wavedefense.label.empty")
                    : first.getHoverName().getString();
                if (nm.length() > 15) nm = nm.substring(0, 13) + "…";
                com.wavedefense.gui.GuiCompat.drawString(g, this.font, nm, x + 6, y + 26, 0xFFFFFF);
                com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§e" + si.getBuyPrice() + " §7/ §a" + si.getSellPrice(),
                    x + 6, y + 38, 0xFFFFFF);
                if (si.hasAvailabilityTrigger()) {
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font, "§6[§e" + I18n.get(si.getAvailabilityTrigger().label) + "§6]",
                        x + 6, y + 50, 0xFFFFAA00);
                }
            }
            if (tooltipItem != null) {
                com.wavedefense.gui.GuiCompat.flush(g);
                ScissorHelper.disable();
                com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, tooltipItem, tooltipMx, tooltipMy);
                ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
            }
        } else if (!location.isPointShopMode()) {
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
                    com.wavedefense.gui.GuiCompat.fill(g, ix-1,iy-1,ix+17,iy+17,GuiTheme.BORDER);
                    com.wavedefense.gui.GuiCompat.fill(g, ix,iy,ix+16,iy+16,GuiTheme.PANEL_DARK);
                    com.wavedefense.gui.GuiCompat.renderItem(g, st, ix, iy);
                    com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, st, ix, iy);
                    if (mouseX >= ix && mouseX < ix+16 && mouseY >= iy && mouseY < iy+16) {
                        com.wavedefense.gui.GuiCompat.flush(g); // flush renderItemDecorations text before disabling scissor
                        ScissorHelper.disable();
                        com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, st, mouseX, mouseY);
                        ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
                    }
                }
                if (items.get(idx).hasAvailabilityTrigger())
                    com.wavedefense.gui.GuiCompat.drawString(g, this.font,
                        "§6[§e" + I18n.get(items.get(idx).getAvailabilityTrigger().label) + "§6]",
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
                    com.wavedefense.gui.GuiCompat.fill(g, ix-1,iy-1,ix+17,iy+17,GuiTheme.BORDER);
                    com.wavedefense.gui.GuiCompat.fill(g, ix,iy,ix+16,iy+16,GuiTheme.PANEL_DARK);
                    com.wavedefense.gui.GuiCompat.renderItem(g, st, ix, iy);
                    com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, st, ix, iy);
                    if (mouseX >= ix && mouseX < ix+16 && mouseY >= iy && mouseY < iy+16) {
                        com.wavedefense.gui.GuiCompat.flush(g); // flush renderItemDecorations text before disabling scissor
                        ScissorHelper.disable();
                        com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, st, mouseX, mouseY);
                        ScissorHelper.enable(0, MODE_BOT, this.width, Math.max(1, BOTTOM_TOP - MODE_BOT));
                    }
                }
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
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
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y < MODE_BOT) w.render(g, mouseX, mouseY, partial);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // ── Прохід 3: нижня статична зона (Зберегти / Exp / Imp) ─────
        ScissorHelper.enable(0, BOTTOM_TOP, this.width, this.height - BOTTOM_TOP);
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y >= BOTTOM_TOP) w.render(g, mouseX, mouseY, partial);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
