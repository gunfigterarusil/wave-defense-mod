package com.wavedefense.gui;

import com.wavedefense.compat.TaczCompat;
import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
// Network packets used via FQN below — no direct imports needed.
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk-add helper for Tacz guns.
 *
 * <p>Lists every known Tacz gun category with a live count of available guns and
 * a price field. Clicking a category adds <em>N</em> new {@link ShopItem} entries
 * to the location's shop — one per gun in that category — with the price taken
 * from the per-row EditBox and {@link ShopItem.ShopCategory#WEAPON}.
 *
 * <p>Only visible when {@link TaczCompat#isLoaded()} returns {@code true}.
 */
public class TaczBulkAddScreen extends Screen {

    private final Location location;
    private final Screen parent;

    /** Default starting price per gun. Persisted across rebuildWidgets() per category. */
    private static final int DEFAULT_PRICE = 1000;
    /** Per-category price buffer (key = category id). */
    private final java.util.Map<String, Integer> priceByCategory = new java.util.LinkedHashMap<>();

    public TaczBulkAddScreen(Location location, Screen parent) {
        super(Component.translatable("wavedefense.tacz.bulk.title"));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 36;

        if (!TaczCompat.isLoaded()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.tacz.bulk.not_loaded"), b -> {}
            ).bounds(cx - 160, y, 320, 20).build()).active = false;
            y += 26;
        } else {
            // One row per category: [Add Pistols (N)]  [Price: ___]
            for (String cat : TaczCompat.getKnownCategories()) {
                int count = TaczCompat.getGunsByCategory(cat).size();
                final String fcat = cat;
                final int finalY = y;
                String btnLabel = I18n.get("wavedefense.tacz.bulk.add_button",
                    I18n.get("wavedefense.tacz.tab." + cat), count);

                Button addBtn = Button.builder(
                    Component.literal(count == 0 ? "§7" + btnLabel : "§a" + btnLabel),
                    b -> addCategory(fcat)
                ).bounds(cx - 160, finalY, 220, 18).build();
                addBtn.active = count > 0;
                this.addRenderableWidget(addBtn);

                // Price label
                this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.tacz.bulk.price"), b -> {}
                ).bounds(cx + 64, finalY, 50, 18).build()).active = false;

                // Price field
                int curPrice = priceByCategory.getOrDefault(cat, DEFAULT_PRICE);
                EditBox priceBox = new EditBox(this.font, cx + 118, finalY, 50, 18,
                    Component.literal(String.valueOf(DEFAULT_PRICE)));
                priceBox.setMaxLength(7);
                priceBox.setValue(String.valueOf(curPrice));
                priceBox.setResponder(s -> {
                    try { priceByCategory.put(fcat, Math.max(0, Integer.parseInt(s.trim()))); }
                    catch (NumberFormatException ignored) {}
                });
                this.addRenderableWidget(priceBox);
                y += 22;
            }
        }

        y += 8;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.back"),
            b -> this.minecraft.setScreen(parent)
        ).bounds(cx - 60, this.height - 28, 120, 20).build());
    }

    /** Adds every Tacz gun of the given category as a new ShopItem with the chosen price.
     *  Upload is handed to {@link ClientShopUploadQueue}, which sends one item per packet
     *  paced across ticks — a gun's NBT is heavy enough that batching them overflowed the
     *  32767-byte serverbound payload limit on large packs. */
    private void addCategory(String category) {
        if (!TaczCompat.isLoaded()) return;
        int price = priceByCategory.getOrDefault(category, DEFAULT_PRICE);
        List<TaczCompat.TaczGunEntry> guns = TaczCompat.getGunsByCategory(category);

        // Build the full list of ShopItems first
        List<ShopItem> toAdd = new ArrayList<>(guns.size());
        for (TaczCompat.TaczGunEntry entry : guns) {
            ItemStack stack = TaczCompat.buildGunStack(entry.gunId);
            if (stack.isEmpty()) continue;
            List<ItemStack> items = new ArrayList<>();
            items.add(stack);
            ShopItem si = new ShopItem(items, price, 0);
            si.setCategory(ShopItem.ShopCategory.WEAPON);
            toAdd.add(si);
        }
        if (toAdd.isEmpty()) return;

        // Optimistic client-side mutation so the user sees instant feedback when they
        // return to the editor. Server-side state is also updated authoritatively below.
        for (ShopItem si : toAdd) location.getShopItems().add(si);

        // One item per packet, paced across client ticks. A single TACZ gun carries
        // enough NBT that even a handful per packet can exceed the 32767-byte
        // serverbound payload limit, which dropped the connection outright on large
        // packs. See ClientShopUploadQueue for the rest of the reasoning.
        ClientShopUploadQueue.enqueue(location.getName(), toAdd);

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.tacz.bulk.added", toAdd.size(),
                    I18n.get("wavedefense.tacz.tab." + category)),
                false);
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        // Hint line under header
        g.drawCenteredString(this.font,
            Component.translatable("wavedefense.tacz.bulk.hint"),
            this.width / 2, 26, GuiTheme.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
