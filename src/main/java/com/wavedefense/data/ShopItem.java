package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopItem {
    public enum ShopCategory {
        ALL("Всі"), WEAPON("⚔ Зброя"), ARMOR("🛡 Броня"),
        CONSUMABLE("🧪 Розхідники"), OTHER("📦 Інше");
        public final String label;
        ShopCategory(String l) { this.label = l; }
    }

    private List<ItemStack> items; // Can hold up to 4 items
    private ShopCategory category = ShopCategory.OTHER;
    private int buyPrice;
    private int sellPrice;
    // Тригер доступності товару (null = завжди доступний)
    private com.wavedefense.data.WaveTrigger availabilityTrigger = null;
    // Для SHOP_WAVE_N — номер хвилі (1-based)
    private int availabilityWave = 1;
    // Для SHOP_PLAYER_HAS_ITEM — id предмета
    private String availabilityItemId = "";

    public ShopItem(List<ItemStack> items, int buyPrice, int sellPrice) {
        // Ensure we have a mutable list and copy items to prevent outside modification
        this.items = items.stream().map(ItemStack::copy).collect(Collectors.toList());
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public List<ItemStack> getItems() {
        // Return copies to maintain encapsulation
        return items.stream().map(ItemStack::copy).collect(Collectors.toList());
    }

    public void setItems(List<ItemStack> items) {
        this.items = items.stream().map(ItemStack::copy).collect(Collectors.toList());
    }

    public int getBuyPrice() { return buyPrice; }
    public void setBuyPrice(int price) { this.buyPrice = price; }

    public int getSellPrice() { return sellPrice; }
    public void setSellPrice(int price) { this.sellPrice = price; }

    public boolean canSell() { return sellPrice > 0; }

    public ShopCategory getCategory() { return category; }
    public void setCategory(ShopCategory c) { this.category = c; }

    public com.wavedefense.data.WaveTrigger getAvailabilityTrigger() { return availabilityTrigger; }
    public void setAvailabilityTrigger(com.wavedefense.data.WaveTrigger t) { this.availabilityTrigger = t; }
    public boolean hasAvailabilityTrigger() { return availabilityTrigger != null; }

    public int  getAvailabilityWave()      { return availabilityWave; }
    public void setAvailabilityWave(int w) { this.availabilityWave = Math.max(1, w); }

    public String getAvailabilityItemId()       { return availabilityItemId == null ? "" : availabilityItemId; }
    public void   setAvailabilityItemId(String s){ this.availabilityItemId = s == null ? "" : s; }

    /**
     * Перевіряє чи доступний цей товар для гравця/поточної хвилі.
     * @param currentWave поточна хвиля (1-based), 0 = поза хвилею
     * @param player гравець для перевірки інвентаря
     */
    public boolean isAvailable(int currentWave, net.minecraft.server.level.ServerPlayer player) {
        if (availabilityTrigger == null) return true;
        return switch (availabilityTrigger) {
            case SHOP_LOCATION_START -> true; // завжди
            case SHOP_WAVE_START     -> currentWave > 0;
            case SHOP_WAVE_N         -> currentWave == availabilityWave;
            case SHOP_PLAYER_HAS_ITEM -> {
                if (player == null || availabilityItemId.isBlank()) yield false;
                try {
                    net.minecraft.resources.ResourceLocation rl =
                        new net.minecraft.resources.ResourceLocation(availabilityItemId);
                    net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                    yield item != null && player.getInventory().contains(new net.minecraft.world.item.ItemStack(item));
                } catch (Exception e) { yield false; }
            }
            default -> true;
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag itemsList = new ListTag();
        for (ItemStack item : items) {
            itemsList.add(item.save(new CompoundTag()));
        }
        tag.put("items", itemsList);
        tag.putInt("buyPrice", buyPrice);
        tag.putInt("sellPrice", sellPrice);
        tag.putString("category", category.name());
        if (availabilityTrigger != null) {
            tag.putString("availabilityTrigger", availabilityTrigger.name());
            tag.putInt("availabilityWave", availabilityWave);
            if (!availabilityItemId.isEmpty()) tag.putString("availabilityItemId", availabilityItemId);
        }
        return tag;
    }

    public static ShopItem load(CompoundTag tag) {
        List<ItemStack> loadedItems = new ArrayList<>();
        ListTag itemsList = tag.getList("items", 10); // 10 is the NBT type for CompoundTag
        for (int i = 0; i < itemsList.size(); i++) {
            loadedItems.add(ItemStack.of(itemsList.getCompound(i)));
        }
        ShopItem si = new ShopItem(
                loadedItems,
                tag.getInt("buyPrice"),
                tag.getInt("sellPrice")
        );
        if (tag.contains("category")) {
            try { si.category = ShopCategory.valueOf(tag.getString("category")); } catch (Exception ignored) {}
        }
        if (tag.contains("availabilityTrigger")) {
            try { si.availabilityTrigger = com.wavedefense.data.WaveTrigger.valueOf(tag.getString("availabilityTrigger")); }
            catch (Exception ignored) {}
            si.availabilityWave = tag.contains("availabilityWave") ? tag.getInt("availabilityWave") : 1;
            si.availabilityItemId = tag.contains("availabilityItemId") ? tag.getString("availabilityItemId") : "";
        }
        return si;
    }
}
