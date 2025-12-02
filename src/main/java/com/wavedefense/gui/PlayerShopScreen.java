package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.PurchaseItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerShopScreen extends Screen {
    private final Location location;
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 6;
    private int playerPoints;

    private Map<Integer, Integer> itemCountCache = new HashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 500;

    public PlayerShopScreen(Location location) {
        super(Component.literal("Магазин"));
        this.location = location;
        updatePlayerPoints();
        updateItemCountCache();
    }

    private void updatePlayerPoints() {
        if (Minecraft.getInstance().player != null) {
            this.playerPoints = location.getPlayerPoints(Minecraft.getInstance().player.getUUID());
        }
    }

    private void updateItemCountCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate < CACHE_DURATION) {
            return;
        }

        itemCountCache.clear();
        List<ShopItem> shopItems = location.getShopItems();

        for (int i = 0; i < shopItems.size(); i++) {
            ShopItem shopItem = shopItems.get(i);
            int count = countPlayerItems(shopItem.getItem());
            itemCountCache.put(i, count);
        }

        lastCacheUpdate = currentTime;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 80;

        List<ShopItem> shopItems = location.getShopItems();

        if (shopItems.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Магазин порожній"),
                    button -> {}
            ).bounds(centerX - 100, startY, 200, 20).build()).active = false;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Закрити"),
                    button -> this.onClose()
            ).bounds(centerX - 50, this.height - 30, 100, 20).build());

            return;
        }

        updateItemCountCache();

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            ItemStack item = shopItem.getItem();
            int yPos = startY + (i * 70);

            String itemName = item.getHoverName().getString();
            if (itemName.length() > 22) {
                itemName = itemName.substring(0, 19) + "...";
            }

            this.addRenderableWidget(Button.builder(
                    Component.literal("§e" + itemName),
                    button -> {}
            ).bounds(centerX - 140, yPos + 3, 160, 20).build()).active = false;

            if (item.getCount() > 1) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("§7x" + item.getCount()),
                        button -> {}
                ).bounds(centerX - 140, yPos + 25, 60, 15).build()).active = false;
            }

            if (item.hasTag()) {
                this.addRenderableWidget(Button.builder(
                        Component.literal("§7✓ З NBT"),
                        button -> {}
                ).bounds(centerX - 75, yPos + 25, 55, 15).build()).active = false;
            }

            int buyPrice = shopItem.getBuyPrice();
            boolean canBuy = playerPoints >= buyPrice;

            this.addRenderableWidget(Button.builder(
                    Component.literal("§6Купити: " + buyPrice),
                    button -> {}
            ).bounds(centerX - 140, yPos + 43, 90, 18).build()).active = false;

            final int finalIndex = index;
            Button buyButton = Button.builder(
                    Component.literal(canBuy ? "§a✓ Купити" : "§c✗ Купити"),
                    button -> buyItem(finalIndex)
            ).bounds(centerX - 45, yPos + 43, 70, 18).build();
            buyButton.active = canBuy;
            this.addRenderableWidget(buyButton);

            if (shopItem.canSell()) {
                int sellPrice = shopItem.getSellPrice();
                int playerHas = itemCountCache.getOrDefault(index, 0);
                boolean canSell = playerHas > 0;

                this.addRenderableWidget(Button.builder(
                        Component.literal("§aПродати: " + sellPrice),
                        button -> {}
                ).bounds(centerX + 30, yPos + 43, 90, 18).build()).active = false;

                Button sellButton = Button.builder(
                        Component.literal(canSell ? "§a✓ Продати" : "§c✗ Продати"),
                        button -> sellItem(finalIndex)
                ).bounds(centerX + 125, yPos + 43, 75, 18).build();
                sellButton.active = canSell;
                this.addRenderableWidget(sellButton);

                if (canSell) {
                    this.addRenderableWidget(Button.builder(
                            Component.literal("§7У вас: " + playerHas),
                            button -> {}
                    ).bounds(centerX + 30, yPos + 25, 80, 15).build()).active = false;
                }
            }
        }

        if (shopItems.size() > ITEMS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollUp()
            ).bounds(this.width - 35, startY, 30, 30).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollDown()
            ).bounds(this.width - 35, this.height - 80, 30, 30).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("🔄 Оновити"),
                button -> forceRefresh()
        ).bounds(10, this.height - 30, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());
    }

    private void buyItem(int index) {
        ShopItem shopItem = location.getShopItems().get(index);
        if (playerPoints >= shopItem.getBuyPrice()) {
            PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), index, true));
        }
    }

    private void sellItem(int index) {
        ShopItem shopItem = location.getShopItems().get(index);
        int playerHas = itemCountCache.getOrDefault(index, 0);
        if (playerHas > 0) {
            PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), index, false));
        }
    }

    private int countPlayerItems(ItemStack templateItem) {
        if (minecraft.player == null) return 0;

        int count = 0;
        for (int i = 0; i < minecraft.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (itemsMatch(stack, templateItem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean itemsMatch(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() || stack2.isEmpty()) return false;
        if (!ItemStack.isSameItem(stack1, stack2)) return false;
        if (stack1.hasTag() != stack2.hasTag()) return false;
        if (stack1.hasTag() && stack2.hasTag()) {
            return stack1.getTag().equals(stack2.getTag());
        }
        return true;
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollDown() {
        List<ShopItem> shopItems = location.getShopItems();
        if (scrollOffset + ITEMS_PER_PAGE < shopItems.size()) {
            scrollOffset++;
            this.rebuildWidgets();
        }
    }

    public void forceRefresh() {
        lastCacheUpdate = 0;
        updatePlayerPoints();
        updateItemCountCache();
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int centerX = this.width / 2;

        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), centerX, 15, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§7" + location.getName(), centerX, 30, 0xFFFFFF);

        graphics.fill(centerX - 100, 50, centerX + 100, 70, 0xFF2d2d2d);
        graphics.fill(centerX - 101, 49, centerX + 101, 50, 0xFF666666);
        graphics.fill(centerX - 101, 70, centerX + 101, 71, 0xFF666666);
        graphics.fill(centerX - 101, 50, centerX - 100, 70, 0xFF666666);
        graphics.fill(centerX + 100, 50, centerX + 101, 70, 0xFF666666);

        graphics.drawCenteredString(this.font, "§6Ваші поінти: §e" + playerPoints, centerX, 57, 0xFFFFFF);

        List<ShopItem> shopItems = location.getShopItems();
        int startY = 80;

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            ItemStack item = shopItem.getItem();
            int yPos = startY + (i * 70);

            graphics.fill(centerX - 165, yPos - 2, centerX + 205, yPos + 65, 0xFF1a1a1a);

            int color = playerPoints >= shopItem.getBuyPrice() ? 0xFF4CAF50 : 0xFF666666;
            graphics.fill(centerX - 166, yPos - 3, centerX + 206, yPos - 2, color);
            graphics.fill(centerX - 166, yPos + 65, centerX + 206, yPos + 66, color);
            graphics.fill(centerX - 166, yPos - 2, centerX - 165, yPos + 65, color);
            graphics.fill(centerX + 205, yPos - 2, centerX + 206, yPos + 65, color);

            graphics.renderItem(item, centerX - 160, yPos + 3);
            graphics.renderItemDecorations(this.font, item, centerX - 160, yPos + 3);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, shopItems.size()); i++) {
            int index = i + scrollOffset;
            if (index >= shopItems.size()) break;

            ShopItem shopItem = shopItems.get(index);
            ItemStack item = shopItem.getItem();
            int yPos = startY + (i * 70);

            if (mouseX >= centerX - 160 && mouseX <= centerX - 160 + 16 &&
                    mouseY >= yPos + 3 && mouseY <= yPos + 3 + 16) {
                graphics.renderTooltip(this.font, item, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
