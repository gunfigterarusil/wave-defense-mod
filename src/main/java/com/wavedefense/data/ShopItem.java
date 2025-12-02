package com.wavedefense.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class ShopItem {
    private ItemStack item;
    private int buyPrice;    // НОВОЕ: Ціна купівлі
    private int sellPrice;   // НОВОЕ: Ціна продажу

    public ShopItem(ItemStack item, int buyPrice, int sellPrice) {
        this.item = item.copy();
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public ItemStack getItem() { return item.copy(); }
    public void setItem(ItemStack item) { this.item = item.copy(); }

    public int getBuyPrice() { return buyPrice; }
    public void setBuyPrice(int price) { this.buyPrice = price; }

    public int getSellPrice() { return sellPrice; }
    public void setSellPrice(int price) { this.sellPrice = price; }

    // Перевірка чи можна продати цей предмет
    public boolean canSell() { return sellPrice > 0; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.save(new CompoundTag()));
        tag.putInt("buyPrice", buyPrice);
        tag.putInt("sellPrice", sellPrice);
        return tag;
    }

    public static ShopItem load(CompoundTag tag) {
        return new ShopItem(
                ItemStack.of(tag.getCompound("item")),
                tag.getInt("buyPrice"),
                tag.getInt("sellPrice")
        );
    }

    // Перевірка чи ItemStack відповідає цьому ShopItem (з NBT)
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || item.isEmpty()) return false;

        // Перевірка типу предмета
        if (!ItemStack.isSameItem(stack, item)) return false;

        // Перевірка NBT
        CompoundTag stackNbt = stack.getTag();
        CompoundTag itemNbt = item.getTag();

        if (stackNbt == null && itemNbt == null) return true;
        if (stackNbt == null || itemNbt == null) return false;

        return stackNbt.equals(itemNbt);
    }
}