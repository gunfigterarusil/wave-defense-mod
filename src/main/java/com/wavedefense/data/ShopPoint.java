package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Точка магазину — фізична позиція у світі з власним списком товарів і радіусом доступу.
 * Коли shopMode = POINT у Location — гравець може відкрити магазин лише якщо
 * він знаходиться в межах radius блоків від однієї з точок. Кожна точка має
 * свій окремий список ShopItem.
 */
public class ShopPoint {

    private String      name;        // відображувана назва, наприклад "Крамниця зброї"
    private BlockPos    pos;         // центр точки магазину
    private int         radius;      // радіус доступу (блоків, 1-64)
    private List<ShopItem> items;    // товари цієї точки

    public ShopPoint(String name, BlockPos pos, int radius) {
        this.name   = name == null ? "Shop" : name;
        this.pos    = pos;
        this.radius = Math.max(1, Math.min(64, radius));
        this.items  = new ArrayList<>();
    }

    // ── Getters / Setters ─────────────────────────────────────────────
    public String   getName()               { return name; }
    public void     setName(String n)       { this.name = n == null ? "Shop" : n; }

    public BlockPos getPos()                { return pos; }
    public void     setPos(BlockPos p)      { this.pos = p; }

    public int      getRadius()             { return radius; }
    public void     setRadius(int r)        { this.radius = Math.max(1, Math.min(64, r)); }

    public List<ShopItem> getItems()        { return items; }
    public void addItem(ShopItem item)      { items.add(item); }
    public void removeItem(int index)       {
        if (index >= 0 && index < items.size()) items.remove(index);
    }

    /** Перевіряє чи гравець у заданих координатах знаходиться в радіусі цієї точки. */
    public boolean isPlayerInRange(double px, double py, double pz) {
        if (pos == null) return false;
        double dx = px - (pos.getX() + 0.5);
        double dz = pz - (pos.getZ() + 0.5);
        // Використовуємо горизонтальну відстань (ігноруємо Y)
        return Math.sqrt(dx * dx + dz * dz) <= radius;
    }

    // ── NBT Serialization ────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        if (pos != null) tag.putLong("pos", pos.asLong());
        tag.putInt("radius", radius);

        ListTag itemsList = new ListTag();
        for (ShopItem item : items) itemsList.add(item.save());
        tag.put("items", itemsList);

        return tag;
    }

    public static ShopPoint load(CompoundTag tag) {
        String   name   = tag.contains("name")   ? tag.getString("name")          : "Shop";
        BlockPos pos    = tag.contains("pos")    ? BlockPos.of(tag.getLong("pos")) : null;
        int      radius = tag.contains("radius") ? tag.getInt("radius")            : 5;

        ShopPoint sp = new ShopPoint(name, pos, radius);

        if (tag.contains("items")) {
            ListTag list = tag.getList("items", 10);
            for (int i = 0; i < list.size(); i++) {
                sp.items.add(ShopItem.load(list.getCompound(i)));
            }
        }
        return sp;
    }
}
