package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.PurchaseItemPacket;
import com.wavedefense.network.packets.SellItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerShopScreen extends ScrollableScreen {
    private final Location location;
    private final com.wavedefense.data.ShopPoint shopPoint;

    private static final int ROW_H = 52;
    private static final int TILE_W = 112;
    private static final int TILE_H = 76;
    private static final int TILE_GAP = 8;

    private int playerPoints;
    private int currentWave = 0;
    private boolean tileMode = true;

    private ShopItem.ShopCategory activeCategory = ShopItem.ShopCategory.ALL;
    private final List<Integer> filteredIndices = new ArrayList<>();

    public PlayerShopScreen(Location location) {
        this(location, null);
    }

    public PlayerShopScreen(Location location, com.wavedefense.data.ShopPoint shopPoint) {
        super(Component.translatable("wavedefense.label.shop_title",
                shopPoint != null ? shopPoint.getName() : location.getName()));
        this.location = location;
        this.shopPoint = shopPoint;
        updatePlayerPoints();
        rebuildFilter();
    }

    @Override protected int getClipTop() { return 64; }
    @Override protected int getClipBot() { return this.height - 30; }
    @Override protected int getListSize() { return filteredIndices.size(); }

    @Override
    protected int getItemsPerPage() {
        if (!tileMode) return Math.max(3, (this.height - 90) / ROW_H);
        int rows = Math.max(1, (getClipBot() - getClipTop()) / TILE_H);
        return Math.max(1, getTileCols() * rows);
    }

    public com.wavedefense.data.ShopPoint getShopPoint() { return shopPoint; }
    public String getLocationName() { return location.getName(); }

    private List<ShopItem> getShopItemList() {
        return shopPoint != null ? shopPoint.getItems() : location.getShopItems();
    }

    private int getTileCols() {
        int usableW = Math.max(TILE_W, this.width - 34);
        return Math.max(1, Math.min(5, (usableW + TILE_GAP) / (TILE_W + TILE_GAP)));
    }

    private void updatePlayerPoints() {
        com.wavedefense.wave.PlayerWaveData cpd = ClientPlayerDataManager.getPlayerData();
        if (cpd != null) {
            this.playerPoints = cpd.getPlayerPoints();
            this.currentWave = cpd.getCurrentWave();
        }
    }

    private boolean isItemAvailable(ShopItem s) {
        if (!s.hasAvailabilityTrigger()) return true;
        com.wavedefense.data.WaveTrigger at = s.getAvailabilityTrigger();
        switch (at) {
            case SHOP_LOCATION_START:
            case SHOP_WAVE_START:
                return currentWave >= 1;
            case SHOP_WAVE_N:
                return currentWave == s.getAvailabilityWave();
            case SHOP_PLAYER_HAS_ITEM:
                if (s.getAvailabilityItemId() != null && !s.getAvailabilityItemId().isEmpty()
                        && Minecraft.getInstance().player != null) {
                    try {
                        net.minecraft.resources.ResourceLocation rl =
                                new net.minecraft.resources.ResourceLocation(s.getAvailabilityItemId());
                        net.minecraft.world.item.Item reqItem =
                                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                        if (reqItem != null) {
                            return Minecraft.getInstance().player.getInventory().hasAnyMatching(
                                    st -> st.getItem() == reqItem);
                        }
                    } catch (Exception ignored) {}
                }
                return false;
            default:
                return true;
        }
    }

    private void rebuildFilter() {
        filteredIndices.clear();
        List<ShopItem> all = getShopItemList();
        for (int i = 0; i < all.size(); i++) {
            ShopItem s = all.get(i);
            if (activeCategory != ShopItem.ShopCategory.ALL && s.getCategory() != activeCategory) continue;
            if (!isItemAvailable(s)) continue;
            filteredIndices.add(i);
        }
        scrollOffset = 0;
    }

    private boolean canPlayerSell(ShopItem shopItem) {
        if (minecraft.player == null) return false;
        Map<net.minecraft.world.item.Item, Integer> req = new HashMap<>();
        for (ItemStack s : shopItem.getItems()) req.merge(s.getItem(), s.getCount(), Integer::sum);
        for (Map.Entry<net.minecraft.world.item.Item, Integer> e : req.entrySet()) {
            if (minecraft.player.getInventory().countItem(e.getKey()) < e.getValue()) return false;
        }
        return true;
    }

    @Override
    protected void init() {
        super.init();
        updatePlayerPoints();

        int cx = this.width / 2;
        int top = 42;
        int itemsPerPage = getItemsPerPage();

        addStatic(Button.builder(
                Component.translatable(tileMode ? "wavedefense.shop.view_tiles" : "wavedefense.shop.view_list"),
                b -> { tileMode = !tileMode; scrollOffset = 0; rebuildWidgets(); }
        ).bounds(Math.max(8, this.width - 86), 18, 76, 16).build());

        ShopItem.ShopCategory[] cats = ShopItem.ShopCategory.values();
        int gap = 2;
        int availW = Math.min(440, this.width - 16);
        int catW = Math.max(46, (availW - gap * (cats.length - 1)) / cats.length);
        int startCatX = cx - (catW * cats.length + gap * (cats.length - 1)) / 2;
        for (int i = 0; i < cats.length; i++) {
            final ShopItem.ShopCategory cat = cats[i];
            int bx = startCatX + i * (catW + gap);
            Component label = ((cat == activeCategory) ? Component.translatable("wavedefense.auto.u00a7e_u00a7l_99162931") : Component.translatable("wavedefense.auto.u00a77_d0a4b613"))
                    .append(Component.translatable(categoryKey(cat)));
            addStatic(Button.builder(
                    label,
                    b -> { activeCategory = cat; rebuildFilter(); rebuildWidgets(); }
            ).bounds(bx, top, catW, 16).build());
        }

        int startY = top + 22;
        if (tileMode) buildTileButtons(startY, itemsPerPage);
        else buildListButtons(cx, startY, itemsPerPage);

        int scrollRight = tileMode ? this.width - 24 : cx + 225;
        if (scrollOffset > 0) {
            addStatic(Button.builder(Component.literal("▲"),
                    b -> { scrollOffset = Math.max(0, scrollOffset - 1); rebuildWidgets(); }
            ).bounds(scrollRight, startY, 18, 18).build());
        }
        if (filteredIndices.size() > scrollOffset + itemsPerPage) {
            int stepH = tileMode ? TILE_H : ROW_H;
            int visibleRows = tileMode
                    ? Math.max(1, (itemsPerPage + getTileCols() - 1) / getTileCols())
                    : itemsPerPage;
            addStatic(Button.builder(Component.literal("▼"),
                    b -> { scrollOffset = Math.min(filteredIndices.size() - itemsPerPage, scrollOffset + 1); rebuildWidgets(); }
            ).bounds(scrollRight, startY + (visibleRows - 1) * stepH, 18, 18).build());
        }

        addStatic(Button.builder(Component.translatable("wavedefense.button.close"),
                b -> this.onClose()
        ).bounds(cx - 40, this.height - 26, 80, 18).build());
    }

    private void buildListButtons(int cx, int startY, int itemsPerPage) {
        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = getShopItemList().get(realIndex);
            int yPos = startY + i * ROW_H;
            addBuyButton(shopItem, realIndex, cx + 60, yPos + 6, 80, 16);
            if (shopItem.canSell()) addSellButton(shopItem, realIndex, cx + 145, yPos + 6, 72, 16);
        }
    }

    private void buildTileButtons(int startY, int itemsPerPage) {
        int cols = getTileCols();
        int gridW = cols * TILE_W + (cols - 1) * TILE_GAP;
        int gridX = this.width / 2 - gridW / 2;
        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = getShopItemList().get(realIndex);
            int col = i % cols;
            int row = i / cols;
            int x = gridX + col * (TILE_W + TILE_GAP);
            int y = startY + row * TILE_H;
            addBuyButton(shopItem, realIndex, x + 6, y + TILE_H - 22, shopItem.canSell() ? 62 : TILE_W - 12, 16);
            if (shopItem.canSell()) addSellButton(shopItem, realIndex, x + 72, y + TILE_H - 22, 34, 16);
        }
    }

    private void addBuyButton(ShopItem shopItem, int realIndex, int x, int y, int w, int h) {
        Button buyBtn = Button.builder(
                Component.translatable("wavedefense.shop.buy_price", shopItem.getBuyPrice()),
                b -> {
                    int spIdx = -1;
                    if (shopPoint != null) {
                        List<com.wavedefense.data.ShopPoint> pts = location.getShopPoints();
                        for (int si = 0; si < pts.size(); si++) {
                            if (pts.get(si) == shopPoint) { spIdx = si; break; }
                        }
                    }
                    PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), spIdx, realIndex));
                    updatePlayerPoints();
                    rebuildFilter();
                    rebuildWidgets();
                }
        ).bounds(x, y, w, h).build();
        buyBtn.active = playerPoints >= shopItem.getBuyPrice();
        this.addRenderableWidget(buyBtn);
    }

    private void addSellButton(ShopItem shopItem, int realIndex, int x, int y, int w, int h) {
        // Resolve shopPointIndex so SellItemPacket mirrors PurchaseItemPacket behaviour
        int spIdx = -1;
        if (shopPoint != null) {
            java.util.List<com.wavedefense.data.ShopPoint> pts = location.getShopPoints();
            for (int si = 0; si < pts.size(); si++) {
                if (pts.get(si) == shopPoint) { spIdx = si; break; }
            }
        }
        final int resolvedSpIdx = spIdx;
        Button sellBtn = Button.builder(
                Component.translatable("wavedefense.shop.sell_price", shopItem.getSellPrice()),
                b -> {
                    PacketHandler.sendToServer(new SellItemPacket(location.getName(), resolvedSpIdx, realIndex));
                    updatePlayerPoints();
                    rebuildFilter();
                    rebuildWidgets();
                }
        ).bounds(x, y, w, h).build();
        sellBtn.active = canPlayerSell(shopItem);
        this.addRenderableWidget(sellBtn);
    }

    @Override
    protected void renderHeader(GuiGraphics g, int mx, int my, float pt) {
        int cx = this.width / 2;
        g.drawCenteredString(this.font,
                Component.translatable("wavedefense.auto.u00a76_u00a7l_008813a3").append(Component.translatable("wavedefense.shop.header", location.getName())),
                cx, 8, 0xFFFFFF);
        Component summary = currentWave > 0
                ? Component.translatable("wavedefense.shop.summary_wave", playerPoints, filteredIndices.size(), currentWave)
                : Component.translatable("wavedefense.shop.summary", playerPoints, filteredIndices.size());
        g.drawCenteredString(this.font, Component.translatable("wavedefense.auto.u00a7e_60ffae0a").append(summary), cx, 20, 0xFFFFFF);
    }

    @Override
    protected void renderContentExtra(GuiGraphics g, int mx, int my, float pt) {
        if (tileMode) renderTiles(g, mx, my);
        else renderList(g, mx, my);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int cx = this.width / 2;
        int startY = 64;
        int itemsPerPage = getItemsPerPage();

        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = getShopItemList().get(realIndex);
            int yPos = startY + i * ROW_H;

            g.fill(cx - 230, yPos, cx + 228, yPos + ROW_H - 2, i % 2 == 0 ? 0x22FFFFFF : 0x11FFFFFF);

            List<ItemStack> items = shopItem.getItems();
            int iconBaseX = cx - 225;
            int iconBaseY = yPos + 6;
            ItemStack tooltipItem = null;
            for (int j = 0; j < Math.min(4, items.size()); j++) {
                ItemStack stack = items.get(j);
                int ix = iconBaseX + j * 20;
                int iy = iconBaseY;
                renderSlot(g, stack, ix, iy);
                if (mx >= ix && mx <= ix + 16 && my >= iy && my <= iy + 16) tooltipItem = stack;
            }

            int textX = iconBaseX + 86;
            String name = getDisplayName(shopItem, 16);
            g.drawString(this.font, "§f§l" + name, textX, yPos + 4, 0xFFFFFF);
            g.drawString(this.font,
                    Component.translatable("wavedefense.auto.u00a78_415f64f9").append(Component.translatable(categoryKey(shopItem.getCategory()))).append("\u00A78]"),
                    textX, yPos + 14, 0xAAAAAA);
            if (shopItem.hasAvailabilityTrigger()) {
                g.drawString(this.font, "§8" + shopItem.getAvailabilityTrigger().label, textX, yPos + 24, 0xAAAAAA);
            }
            renderTooltipIfNeeded(g, tooltipItem, mx, my);
        }
    }

    private void renderTiles(GuiGraphics g, int mx, int my) {
        int startY = 64;
        int itemsPerPage = getItemsPerPage();
        int cols = getTileCols();
        int gridW = cols * TILE_W + (cols - 1) * TILE_GAP;
        int gridX = this.width / 2 - gridW / 2;
        ItemStack tooltipItem = null;

        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = getShopItemList().get(realIndex);
            int col = i % cols;
            int row = i / cols;
            int x = gridX + col * (TILE_W + TILE_GAP);
            int y = startY + row * TILE_H;

            boolean affordable = playerPoints >= shopItem.getBuyPrice();
            g.fill(x, y, x + TILE_W, y + TILE_H - 2, affordable ? 0x22335533 : 0x22222222);
            g.fill(x, y, x + TILE_W, y + 1, affordable ? 0x8855AA55 : 0x88666666);

            List<ItemStack> stacks = shopItem.getItems();
            ItemStack first = stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
            int iconX = x + TILE_W / 2 - 8;
            int iconY = y + 7;
            renderSlot(g, first, iconX, iconY);
            if (!first.isEmpty() && mx >= iconX && mx <= iconX + 16 && my >= iconY && my <= iconY + 16) {
                tooltipItem = first;
            }

            g.drawString(this.font, getDisplayName(shopItem, 15), x + 6, y + 28, 0xFFFFFF);
            g.drawString(this.font, Component.translatable(categoryKey(shopItem.getCategory())), x + 6, y + 38, 0xAAAAAA);
            if (stacks.size() > 1) g.drawString(this.font, "x" + stacks.size(), x + TILE_W - 18, y + 8, 0xFFE680);
        }

        renderTooltipIfNeeded(g, tooltipItem, mx, my);
    }

    private String getDisplayName(ShopItem shopItem, int maxLen) {
        List<ItemStack> stacks = shopItem.getItems();
        String name = stacks.isEmpty()
                ? Component.translatable("wavedefense.shop.default_item").getString()
                : stacks.get(0).getHoverName().getString();
        if (name.length() > maxLen) name = name.substring(0, Math.max(1, maxLen - 3)) + "...";
        if (stacks.size() > 1) name += " x" + stacks.size();
        return name;
    }

    private void renderSlot(GuiGraphics g, ItemStack stack, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF555555);
        g.fill(x, y, x + 16, y + 16, 0xFF2A2A2A);
        g.renderItem(stack, x, y);
        g.renderItemDecorations(this.font, stack, x, y);
    }

    private void renderTooltipIfNeeded(GuiGraphics g, ItemStack stack, int mx, int my) {
        if (stack == null || stack.isEmpty()) return;
        ScissorHelper.disable();
        g.renderTooltip(this.font, stack, mx, my);
        ScissorHelper.enable(0, getClipTop(), this.width, Math.max(1, getClipBot() - getClipTop()));
    }

    private String categoryKey(ShopItem.ShopCategory category) {
        return switch (category) {
            case ALL -> "wavedefense.shop.category.all";
            case WEAPON -> "wavedefense.shop.category.weapon";
            case ARMOR -> "wavedefense.shop.category.armor";
            case CONSUMABLE -> "wavedefense.shop.category.consumable";
            case OTHER -> "wavedefense.shop.category.other";
        };
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return super.keyPressed(keyCode, scanCode, modifiers); }
    @Override public boolean charTyped(char ch, int modifiers) { return super.charTyped(ch, modifiers); }
}
