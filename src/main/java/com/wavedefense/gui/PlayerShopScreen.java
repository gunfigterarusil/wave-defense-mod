package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.PurchaseItemPacket;
import com.wavedefense.network.packets.SellItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Магазин гравця з категоріями: Всі | ⚔ Зброя | 🛡 Броня | 🧪 Розхідники | 📦 Інше
 * Підтримує пагінацію та tooltips для товарів.
 * Гаряча клавіша B (можна змінити в налаштуваннях).
 */
public class PlayerShopScreen extends Screen {
    private final Location location;
    private int scrollOffset = 0;
    private int itemsPerPage = 6; // динамічно перераховується в init()
    private static final int ROW_H = 48;
    private int playerPoints;

    // Поточна категорія
    private ShopItem.ShopCategory activeCategory = ShopItem.ShopCategory.ALL;
    private List<Integer> filteredIndices = new ArrayList<>();

    public PlayerShopScreen(Location location) {
        super(Component.literal("Магазин: " + location.getName()));
        this.location = location;
        updatePlayerPoints();
        rebuildFilter();
    }

    private void updatePlayerPoints() {
        if (Minecraft.getInstance().player != null) {
            this.playerPoints = location.getPlayerPoints(Minecraft.getInstance().player.getUUID());
        }
    }

    private void rebuildFilter() {
        filteredIndices.clear();
        List<ShopItem> all = location.getShopItems();
        // Отримуємо поточну хвилю для перевірки тригерів доступності
        int curWave = 0;
        com.wavedefense.data.PlayerWaveData cpd = com.wavedefense.gui.ClientPlayerDataManager.getPlayerData();
        if (cpd != null) curWave = cpd.getCurrentWave();

        for (int i = 0; i < all.size(); i++) {
            ShopItem s = all.get(i);
            if (activeCategory != ShopItem.ShopCategory.ALL && s.getCategory() != activeCategory) continue;
            // Тригер доступності (client-side: wave number only, no server player)
            if (s.hasAvailabilityTrigger()) {
                com.wavedefense.data.WaveTrigger at = s.getAvailabilityTrigger();
                if (at == com.wavedefense.data.WaveTrigger.SHOP_WAVE_START && curWave <= 0) continue;
                if (at == com.wavedefense.data.WaveTrigger.SHOP_WAVE_N && curWave != s.getAvailabilityWave()) continue;
                // SHOP_PLAYER_HAS_ITEM — перевіряється на сервері, тут показуємо завжди
            }
            filteredIndices.add(i);
        }
        scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();
        updatePlayerPoints();

        // Адаптивний макет
        itemsPerPage = Math.max(3, (this.height - 100) / (ROW_H + 2));
        int cx = this.width / 2;
        int TOP = 45;

        // ── Кнопки категорій ─────────────────────────────────────────────
        ShopItem.ShopCategory[] cats = ShopItem.ShopCategory.values();
        int catW = 68, gap = 3;
        int totalW = cats.length * catW + (cats.length - 1) * gap;
        int startCatX = cx - totalW / 2;

        for (int i = 0; i < cats.length; i++) {
            final ShopItem.ShopCategory cat = cats[i];
            int bx = startCatX + i * (catW + gap);
            String label = (cat == activeCategory ? "§e" : "§7") + cat.label;
            this.addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> { activeCategory = cat; scrollOffset = 0; rebuildFilter(); clearWidgets(); init(); }
            ).bounds(bx, TOP, catW, 16).build());
        }

        // ── Товари ────────────────────────────────────────────────────────
        int startY = TOP + 22;
        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);

            ShopItem shopItem = location.getShopItems().get(realIndex);
            int yPos = startY + i * ROW_H;
            final int fi = realIndex;

            // Купити
            Button buyBtn = Button.builder(
                    Component.literal("§6Купити §e" + shopItem.getBuyPrice() + " §6pts"),
                    b -> { PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), fi)); updatePlayerPoints(); rebuildFilter(); clearWidgets(); init(); }
            ).bounds(cx - 85, yPos + 25, 100, 18).build();
            buyBtn.active = playerPoints >= shopItem.getBuyPrice();
            this.addRenderableWidget(buyBtn);

            // Продати
            if (shopItem.canSell()) {
                Button sellBtn = Button.builder(
                        Component.literal("§aПродати §2" + shopItem.getSellPrice()),
                        b -> { PacketHandler.sendToServer(new SellItemPacket(location.getName(), fi)); updatePlayerPoints(); rebuildFilter(); clearWidgets(); init(); }
                ).bounds(cx + 20, yPos + 25, 90, 18).build();
                sellBtn.active = canPlayerSell(shopItem);
                this.addRenderableWidget(sellBtn);
            }
        }

        // Прокрутка
        if (scrollOffset > 0) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    b -> { scrollOffset = Math.max(0, scrollOffset - 1); clearWidgets(); init(); }
            ).bounds(cx + 130, TOP + 22, 20, 20).build());
        }
        if (filteredIndices.size() > scrollOffset + itemsPerPage) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    b -> { scrollOffset = Math.min(filteredIndices.size() - itemsPerPage, scrollOffset + 1); clearWidgets(); init(); }
            ).bounds(cx + 130, this.height - 55, 20, 20).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("✕ Закрити"),
                b -> this.onClose()
        ).bounds(cx - 40, this.height - 28, 80, 20).build());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        // Заголовок
        g.drawCenteredString(this.font, "§6§lМагазин§r §7" + location.getName(), cx, 8, 0xFFFFFF);
        g.drawCenteredString(this.font, "§eОчки: §6" + playerPoints +
            "  §7Товарів: §f" + filteredIndices.size(), cx, 20, 0xFFFFFF);

        // Рядки товарів
        int startY = 45 + 22;
        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = location.getShopItems().get(realIndex);
            int yPos = startY + i * ROW_H;

            // Фон рядка
            boolean alt = (i % 2 == 0);
            g.fill(cx - 165, yPos - 1, cx + 160, yPos + ROW_H - 4, alt ? 0x22FFFFFF : 0x11FFFFFF);

            // Назва
            String name = shopItem.getItems().isEmpty() ? "?"
                    : shopItem.getItems().get(0).getHoverName().getString();
            if (name.length() > 22) name = name.substring(0, 19) + "…";
            if (shopItem.getItems().size() > 1) name += " §7(×" + shopItem.getItems().size() + ")";
            g.drawString(this.font, "§f" + name, cx - 160, yPos + 5, 0xFFFFFF);

            // Категорія бейдж
            String catLabel = "§8[" + shopItem.getCategory().label + "§8]";
            g.drawString(this.font, catLabel, cx - 160, yPos + 14, 0xAAAAAA);

            // Іконки
            List<ItemStack> items = shopItem.getItems();
            for (int j = 0; j < Math.min(4, items.size()); j++) {
                ItemStack stack = items.get(j);
                int ix = cx + 80 + j * 20;
                g.renderItem(stack, ix, yPos + 3);
                g.renderItemDecorations(this.font, stack, ix, yPos + 3);
                if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= yPos + 3 && mouseY <= yPos + 19) {
                    g.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; clearWidgets(); init(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < filteredIndices.size()) { scrollOffset++; clearWidgets(); init(); }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
