package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopItem {
    private List<ItemStack> items; // Can hold up to 4 items
    private int buyPrice;
    private int sellPrice;

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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag itemsList = new ListTag();
        for (ItemStack item : items) {
            itemsList.add(item.save(new CompoundTag()));
        }
        tag.put("items", itemsList);
        tag.putInt("buyPrice", buyPrice);
        tag.putInt("sellPrice", sellPrice);
        return tag;
    }

    public static ShopItem load(CompoundTag tag) {
        List<ItemStack> loadedItems = new ArrayList<>();
        ListTag itemsList = tag.getList("items", 10); // 10 is the NBT type for CompoundTag
        for (int i = 0; i < itemsList.size(); i++) {
            loadedItems.add(ItemStack.of(itemsList.getCompound(i)));
        }
        return new ShopItem(
                loadedItems,
                tag.getInt("buyPrice"),
                tag.getInt("sellPrice")
        );
    }
}
