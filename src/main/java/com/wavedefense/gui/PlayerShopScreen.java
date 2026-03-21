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
 * Магазин гравця — виправлений layout без перекриття іконок і кнопок.
 * Іконки зліва, кнопки справа. Тригери доступності фільтрують товари.
 */
public class PlayerShopScreen extends Screen {
    private final Location location;
    // Якщо не null — відображаємо товари конкретної точки магазину
    private final com.wavedefense.data.ShopPoint shopPoint;
    private int scrollOffset = 0;
    private int itemsPerPage = 5;
    private static final int ROW_H = 52;
    private int playerPoints;
    private int currentWave = 0;

    private ShopItem.ShopCategory activeCategory = ShopItem.ShopCategory.ALL;
    private List<Integer> filteredIndices = new ArrayList<>();

    // Звичайний глобальний магазин
    public PlayerShopScreen(Location location) {
        this(location, null);
    }

    // Точковий магазин — товари з конкретної точки
    public PlayerShopScreen(Location location, com.wavedefense.data.ShopPoint shopPoint) {
        super(Component.literal(shopPoint != null
            ? "Магазин: " + shopPoint.getName()
            : "Магазин: " + location.getName()));
        this.location  = location;
        this.shopPoint = shopPoint;
        updatePlayerPoints();
        rebuildFilter();
    }

    /** Повертає поточну точку магазину (або null для глобального режиму). */
    public com.wavedefense.data.ShopPoint getShopPoint() { return shopPoint; }
    public String getLocationName() { return location.getName(); }
    /** Повертає список товарів — з точки або з глобального списку локації. */
    private List<ShopItem> getShopItemList() {
        return shopPoint != null ? shopPoint.getItems() : location.getShopItems();
    }

    private void updatePlayerPoints() {
        // Читаємо поінти з ClientPlayerDataManager (синхронізовані з сервера),
        // а НЕ з location.getPlayerPoints() — клієнтська Location не має поінтів
        com.wavedefense.wave.PlayerWaveData cpd = ClientPlayerDataManager.getPlayerData();
        if (cpd != null) {
            this.playerPoints = cpd.getPlayerPoints();
            this.currentWave  = cpd.getCurrentWave();
        }
    }

    private boolean isItemAvailable(ShopItem s) {
        if (!s.hasAvailabilityTrigger()) return true;
        com.wavedefense.data.WaveTrigger at = s.getAvailabilityTrigger();
        switch (at) {
            case SHOP_LOCATION_START: return currentWave >= 1;
            case SHOP_WAVE_START:     return currentWave >= 1;
            case SHOP_WAVE_N:         return currentWave == s.getAvailabilityWave();
            case SHOP_PLAYER_HAS_ITEM:
                // Перевіряємо інвентар клієнта
                if (s.getAvailabilityItemId() != null && !s.getAvailabilityItemId().isEmpty()
                        && Minecraft.getInstance().player != null) {
                    net.minecraft.resources.ResourceLocation rl =
                        new net.minecraft.resources.ResourceLocation(s.getAvailabilityItemId());
                    net.minecraft.world.item.Item reqItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                    if (reqItem != null) {
                        return Minecraft.getInstance().player.getInventory().hasAnyMatching(
                            st -> st.getItem() == reqItem);
                    }
                }
                return false; // якщо не можемо перевірити — ховаємо
            default: return true;
        }
    }

    private void rebuildFilter() {
        filteredIndices.clear();
        List<ShopItem> all = getShopItemList();
        for (int i = 0; i < all.size(); i++) {
            ShopItem s = all.get(i);
            if (activeCategory != ShopItem.ShopCategory.ALL && s.getCategory() != activeCategory) continue;
            if (!isItemAvailable(s)) continue; // ховаємо недоступні товари
            filteredIndices.add(i);
        }
        scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();
        updatePlayerPoints();
        itemsPerPage = Math.max(3, (this.height - 90) / ROW_H);
        int cx = this.width / 2;
        int TOP = 42;

        // ── Кнопки категорій (адаптивна ширина під розмір екрану) ──────────
        ShopItem.ShopCategory[] cats = ShopItem.ShopCategory.values();
        int gap = 2;
        // Виміряємо доступну ширину (від лівого краю до правого мінус поля)
        int availW = Math.min(440, this.width - 16);
        int catW = (availW - gap * (cats.length - 1)) / cats.length;
        int startCatX = cx - (catW * cats.length + gap * (cats.length - 1)) / 2;
        for (int i = 0; i < cats.length; i++) {
            final ShopItem.ShopCategory cat = cats[i];
            int bx = startCatX + i * (catW + gap);
            String label = (cat == activeCategory ? "§e§l" : "§7") + cat.label;
            this.addRenderableWidget(Button.builder(
                    Component.literal(label),
                    b -> { activeCategory = cat; rebuildFilter(); clearWidgets(); init(); }
            ).bounds(bx, TOP, catW, 16).build());
        }

        // ── Товари — Layout:
        //   [yPos]    назва + категорія (текст — не кнопка, в render)
        //   [yPos+18] [ІКОНКИ 4шт × 18px лівіше]  [Купити кнопка]  [Продати кнопка]
        int startY = TOP + 22;
        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = getShopItemList().get(realIndex);
            int yPos = startY + i * ROW_H;
            final int fi = realIndex;

            // Іконки — рядок кнопок-слотів (ліва частина рядка)
            // Рендеримо як статичні кнопки-заглушки щоб іконки над ними
            // (самі іконки рендеримо в render())

            // Купити кнопка (права частина)
            Button buyBtn = Button.builder(
                    Component.literal("§6✦ §e" + shopItem.getBuyPrice() + " pts"),
                    b -> {
                        int spIdx = -1;
                        if (shopPoint != null) {
                            java.util.List<com.wavedefense.data.ShopPoint> pts = location.getShopPoints();
                            for (int si = 0; si < pts.size(); si++) {
                                if (pts.get(si) == shopPoint) { spIdx = si; break; }
                            }
                        }
                        PacketHandler.sendToServer(new PurchaseItemPacket(location.getName(), spIdx, fi));
                        updatePlayerPoints();
                        rebuildFilter();
                        clearWidgets();
                        init();
                    }
            ).bounds(cx + 60, yPos + 6, 80, 16).build();
            buyBtn.active = playerPoints >= shopItem.getBuyPrice();
            this.addRenderableWidget(buyBtn);

            // Продати кнопка
            if (shopItem.canSell()) {
                Button sellBtn = Button.builder(
                        Component.literal("§2↩ §a" + shopItem.getSellPrice()),
                        b -> {
                            PacketHandler.sendToServer(new SellItemPacket(location.getName(), fi));
                            updatePlayerPoints();
                            rebuildFilter();
                            clearWidgets();
                            init();
                        }
                ).bounds(cx + 145, yPos + 6, 72, 16).build();
                sellBtn.active = canPlayerSell(shopItem);
                this.addRenderableWidget(sellBtn);
            }
        }

        // Прокрутка
        int listRight = cx + 225;
        if (scrollOffset > 0) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { scrollOffset = Math.max(0, scrollOffset-1); clearWidgets(); init(); }
            ).bounds(listRight, startY, 18, 18).build());
        }
        if (filteredIndices.size() > scrollOffset + itemsPerPage) {
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { scrollOffset = Math.min(filteredIndices.size()-itemsPerPage, scrollOffset+1); clearWidgets(); init(); }
            ).bounds(listRight, startY + (itemsPerPage-1)*ROW_H, 18, 18).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("✕ Закрити"),
                b -> this.onClose()
        ).bounds(cx - 40, this.height - 26, 80, 18).build());
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
        g.drawCenteredString(this.font, "§eОчки: §6" + playerPoints
            + "  §7Товарів: §f" + getShopItemList().size()
            + (currentWave > 0 ? "  §7Хвиля: §f" + currentWave : ""), cx, 20, 0xFFFFFF);

        // Scissor: список товарів між header (64) та footer (height-30)
        int listTop = 64, listBot = this.height - 30;
        ScissorHelper.enable(0, listTop, this.width, Math.max(1, listBot - listTop));

        int startY = 42 + 22;

        for (int i = 0; i < Math.min(itemsPerPage, filteredIndices.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredIndices.size()) break;
            int realIndex = filteredIndices.get(idx);
            ShopItem shopItem = getShopItemList().get(realIndex);
            int yPos = startY + i * ROW_H;

            // Фон рядка
            boolean alt = (i % 2 == 0);
            g.fill(cx - 230, yPos, cx + 228, yPos + ROW_H - 2, alt ? 0x22FFFFFF : 0x11FFFFFF);

            // ── Ліва частина: іконки предметів (4 слоти × 18px) ─────────
            List<ItemStack> items = shopItem.getItems();
            int iconBaseX = cx - 225;
            int iconBaseY = yPos + 6;  // вертикально центровано в рядку
            for (int j = 0; j < Math.min(4, items.size()); j++) {
                ItemStack stack = items.get(j);
                int ix = iconBaseX + j * 20;
                int iy = iconBaseY;
                g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0xFF555555);
                g.fill(ix, iy, ix + 16, iy + 16, 0xFF2A2A2A);
                g.renderItem(stack, ix, iy);
                g.renderItemDecorations(this.font, stack, ix, iy);
                if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16) {
                    g.renderTooltip(this.font, stack, mouseX, mouseY);
                }
            }

            // ── Середина: назва та категорія (праворуч від іконок) ───────
            int textX = iconBaseX + 4 * 20 + 6; // правіше 4 іконок
            String name = shopItem.getItems().isEmpty() ? "?"
                    : shopItem.getItems().get(0).getHoverName().getString();
            if (name.length() > 16) name = name.substring(0, 13) + "…";
            if (shopItem.getItems().size() > 1) name += " §7(×" + shopItem.getItems().size() + ")";
            g.drawString(this.font, "§f§l" + name, textX, yPos + 4, 0xFFFFFF);
            String catLbl = shopItem.getCategory().label;
            g.drawString(this.font, "§8[" + catLbl + "§8]", textX, yPos + 14, 0xAAAAAA);

            // Тригер доступності
            if (shopItem.hasAvailabilityTrigger()) {
                g.drawString(this.font, "§8" + shopItem.getAvailabilityTrigger().label, textX, yPos + 24, 0xAAAAAA);
            }
        }

        ScissorHelper.disable();
        super.render(g, mouseX, mouseY, partialTick);
        // Re-render header/footer widgets outside scissor
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() < listTop || w.getY() >= listBot)
                    w.render(g, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; clearWidgets(); init(); }
        else if (delta < 0 && scrollOffset + itemsPerPage < filteredIndices.size()) {
            scrollOffset++; clearWidgets(); init();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
