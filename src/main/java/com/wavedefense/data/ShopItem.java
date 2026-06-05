package com.wavedefense.data;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopItem {
    public enum ShopCategory {
        ALL("wavedefense.shop.category.all"),
        WEAPON("wavedefense.shop.category.weapon"),
        ARMOR("wavedefense.shop.category.armor"),
        CONSUMABLE("wavedefense.shop.category.consumable"),
        OTHER("wavedefense.shop.category.other");
        public final String label; // i18n key
        ShopCategory(String l) { this.label = l; }
    }

    private List<ItemStack> items; // Can hold up to 4 items
    private ShopCategory category = ShopCategory.OTHER;
    private int buyPrice;
    private int sellPrice;
    // Item availability trigger (null = always available)
    private com.wavedefense.data.WaveTrigger availabilityTrigger = null;
    // For SHOP_WAVE_N — wave number (1-based)
    private int availabilityWave = 1;
    // For SHOP_PLAYER_HAS_ITEM — item id
    private String availabilityItemId = "";
    // NBT check on purchase: if true — the item (first slot) can only be sold if its NBT matches
    // the value stored in nbtRequiredTag (SNBT string, e.g. {display:{Name:"..."}})
    private boolean requireNbtMatch  = false;
    private String  nbtRequiredTag   = "";   // SNBT string or empty

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

    public boolean isRequireNbtMatch()            { return requireNbtMatch; }
    public void    setRequireNbtMatch(boolean v)  { this.requireNbtMatch = v; }
    public String  getNbtRequiredTag()            { return nbtRequiredTag == null ? "" : nbtRequiredTag; }
    public void    setNbtRequiredTag(String s)    { this.nbtRequiredTag = s == null ? "" : s; }

    /**
     * Checks the NBT match for a sale: if requireNbtMatch=true, the first matching
     * item in the player's inventory must have the required NBT tags.
     */
    public boolean matchesNbtForSale(net.minecraft.item.ItemStack playerItem) {
        if (!requireNbtMatch || nbtRequiredTag.trim().isEmpty()) return true;
        if (playerItem.isEmpty()) return false;
        try {
            net.minecraft.nbt.CompoundNBT required = net.minecraft.nbt.JsonToNBT.parseTag(nbtRequiredTag);
            net.minecraft.nbt.CompoundNBT actual = playerItem.hasTag() ? playerItem.getTag() : new net.minecraft.nbt.CompoundNBT();
            // Check that all keys from required are present and equal in actual (partial match)
            for (String key : required.getAllKeys()) {
                if (!actual.contains(key)) return false;
                if (!actual.get(key).equals(required.get(key))) return false;
            }
            return true;
        } catch (Exception e) {
            return false; // invalid SNBT — does not match
        }
    }

    /**
     * Checks whether this item is available for the player / current wave.
     * @param currentWave current wave number (1-based), 0 = outside a wave
     * @param player player whose inventory to check
     */
    public boolean isAvailable(int currentWave, net.minecraft.entity.player.ServerPlayerEntity player) {
        if (availabilityTrigger == null) return true;
        // 1.16.5 / Java 8 port: classic switch instead of v17 switch expression
        switch (availabilityTrigger) {
            case SHOP_LOCATION_START: return true;
            case SHOP_WAVE_START:     return currentWave > 0;
            case SHOP_WAVE_N:         return currentWave == availabilityWave;
            case SHOP_PLAYER_HAS_ITEM:
                if (player == null || availabilityItemId.trim().isEmpty()) return false;
                for (String part : availabilityItemId.split(",")) {
                    String id = part.trim();
                    if (id.isEmpty()) continue;
                    try {
                        net.minecraft.util.ResourceLocation rl = new net.minecraft.util.ResourceLocation(id);
                        net.minecraft.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
                        if (item != null && player.inventory.contains(new net.minecraft.item.ItemStack(item))) return true;
                    } catch (Exception ignored) {}
                }
                return false;
            default: return true;
        }
    }

    public CompoundNBT save() {
        CompoundNBT tag = new CompoundNBT();
        ListNBT itemsList = new ListNBT();
        for (ItemStack item : items) {
            itemsList.add(item.save(new CompoundNBT()));
        }
        tag.put("items", itemsList);
        tag.putInt("buyPrice", buyPrice);
        tag.putInt("sellPrice", sellPrice);
        tag.putString("category", category.name());
        tag.putBoolean("requireNbtMatch", requireNbtMatch);
        if (!nbtRequiredTag.isEmpty()) tag.putString("nbtRequiredTag", nbtRequiredTag);
        if (availabilityTrigger != null) {
            tag.putString("availabilityTrigger", availabilityTrigger.name());
            tag.putInt("availabilityWave", availabilityWave);
            if (!availabilityItemId.isEmpty()) tag.putString("availabilityItemId", availabilityItemId);
        }
        return tag;
    }

    public static ShopItem load(CompoundNBT tag) {
        List<ItemStack> loadedItems = new ArrayList<>();
        ListNBT itemsList = tag.getList("items", 10); // 10 is the NBT type for CompoundNBT
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
        si.requireNbtMatch = tag.contains("requireNbtMatch") && tag.getBoolean("requireNbtMatch");
        si.nbtRequiredTag   = tag.contains("nbtRequiredTag") ? tag.getString("nbtRequiredTag") : "";
        if (tag.contains("availabilityTrigger")) {
            try { si.availabilityTrigger = com.wavedefense.data.WaveTrigger.valueOf(tag.getString("availabilityTrigger")); }
            catch (Exception ignored) {}
            si.availabilityWave = tag.contains("availabilityWave") ? tag.getInt("availabilityWave") : 1;
            si.availabilityItemId = tag.contains("availabilityItemId") ? tag.getString("availabilityItemId") : "";
        }
        return si;
    }
}
