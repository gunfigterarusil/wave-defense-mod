package com.wavedefense.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Меню вибору предмета — сітка всіх Items + всі варіанти зілля (по одному ItemStack на кожен тип Potion).
 */
public class ItemSelectionScreen extends Screen {

    public enum Category {
        ALL("wavedefense.item.category.all"), WEAPON("wavedefense.item.category.weapon"),
        ARMOR("wavedefense.item.category.armor"), POTION("wavedefense.item.category.potion"),
        FOOD("wavedefense.item.category.food"), OTHER("wavedefense.item.category.other");
        public final String label;
        Category(String l) { this.label = l; }
    }

    private final Screen              parent;
    private final Consumer<ItemStack> onSelect;
    private       ItemStack           currentItem;

    private int SLOT_SIZE = 18;
    private int PREVIEW_W = 70;
    private int COLS      = 10;

    private Category    currentCategory = Category.ALL;
    private String      searchQuery     = "";
    private EditBox     searchBox;
    private int         scrollOffset    = 0;
    private float       previewAngle    = 0;
    /** Active Tacz sub-tab: null = no Tacz filter; otherwise one of TaczCompat.CAT_* (or CAT_ALL). */
    private String      taczCategory    = null;

    // Базові предмети (один ItemStack на Item)
    private List<ItemStack> allStacks;
    private List<ItemStack> filteredStacks;

    public ItemSelectionScreen(Screen parent, Consumer<ItemStack> onSelect, ItemStack currentItem) {
        super(Component.translatable("wavedefense.title.item_selection"));
        this.parent      = parent;
        this.onSelect    = onSelect;
        this.currentItem = currentItem == null ? ItemStack.EMPTY : currentItem;
        buildAllStacks();
    }

    /**
     * Будує повний список ItemStack з УСІХ доступних предметів:
     *
     * <ol>
     *   <li>Сканує {@code ForgeRegistries.ITEMS} — базовий список всіх зареєстрованих Item.</li>
     *   <li>Сканує всі {@link net.minecraft.world.item.CreativeModeTab} — отримує варіанти
     *       предметів з кастомних creative tabs модів (tacz, тощо). Деякі моди реєструють
     *       один Item, але додають до creative tab кілька ItemStack з різним NBT.</li>
     *   <li>Об'єднує і дедуплікує за {@code namespace:path + NBT-ключ}.</li>
     *   <li>Розгортає PotionItem × всі зареєстровані Potion.</li>
     * </ol>
     *
     * <p>Весь збір захищений {@code try/catch}: якщо один предмет крашить
     * (наприклад, uninit NBT у модових item), він просто пропускається.</p>
     */
    private void buildAllStacks() {
        allStacks = new ArrayList<>();

        // ── 1. Зілля — всі варіанти ──────────────────────────────────────
        List<Potion> allPotions = new ArrayList<>();
        try {
            allPotions = ForgeRegistries.POTIONS.getValues().stream()
                    .filter(p -> p != Potions.EMPTY)
                    .sorted(Comparator.comparing(p -> {
                        ResourceLocation k = ForgeRegistries.POTIONS.getKey(p);
                        return k != null ? k.toString() : "";
                    }))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {}

        // ── 2. Базовий список із ForgeRegistries ──────────────────────────
        //    Сортуємо безпечно: getDescription() може кидати NPE у деяких мобів.
        List<Item> baseItems = new ArrayList<>();
        try {
            baseItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(i -> i != Items.AIR)
                    .sorted(Comparator.comparing(i -> {
                        try { return i.getDescription().getString(); }
                        catch (Exception e) { return ""; }
                    }))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {}

        // ── 3. Creative-tab scanning — збираємо SubItems ──────────────────
        //    Моди як tacz реєструють один GunItem, але додають до creative tab
        //    багато ItemStack з різним NBT (один на кожну зброю).
        //    Ключ дедуплікації: "modid:name" + base64(NBT без display/damage)
        Set<String> seen = new LinkedHashSet<>();
        List<ItemStack> creativeTabStacks = new ArrayList<>();
        try {
            // Forge 1.20.1: tabs вже побудовані під час завантаження модів.
            // getDisplayItems() повертає список без виклику buildContents().
            for (net.minecraft.world.item.CreativeModeTab tab
                    : net.minecraftforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
                try {
                    // getDisplayItems() у Forge 47.x (1.20.1) повертає вже заповнений список
                    @SuppressWarnings("unchecked")
                    Iterable<ItemStack> displayItems = (Iterable<ItemStack>) tab.getDisplayItems();
                    for (ItemStack st : displayItems) {
                        if (st == null || st.isEmpty() || st.getItem() == Items.AIR) continue;
                        String dedupeKey = stableKey(st);
                        if (seen.add(dedupeKey)) creativeTabStacks.add(st.copy());
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // ── 4. Збираємо кінцевий список ───────────────────────────────────
        //    Спочатку всі стандартні items (щоб іти в алфавітному порядку),
        //    потім creative-tab стеки яких іще немає.
        for (Item item : baseItems) {
            if (item instanceof PotionItem || item instanceof SplashPotionItem
                    || item instanceof LingeringPotionItem || item instanceof TippedArrowItem) {
                for (Potion potion : allPotions) {
                    try {
                        ItemStack ps = PotionUtils.setPotion(new ItemStack(item), potion);
                        if (seen.add(stableKey(ps))) allStacks.add(ps);
                    } catch (Exception ignored) {}
                }
            } else {
                try {
                    ItemStack st = new ItemStack(item);
                    if (seen.add(stableKey(st))) allStacks.add(st);
                } catch (Exception ignored) {}
            }
        }

        // Додаємо creative-tab стеки (modded variants: tacz guns, etc.)
        // що ще не потрапили через ForgeRegistries (унікальні NBT-варіанти)
        allStacks.addAll(creativeTabStacks.stream()
                .filter(st -> {
                    try {
                        // Якщо base item вже є — цей стек — NBT-варіант, треба його
                        // Якщо base item НЕ вже є — все одно додаємо
                        return true;
                    } catch (Exception e) { return false; }
                })
                // Але відфільтровуємо ті що вже додані через ForgeRegistries без NBT,
                // якщо creative-tab дає той самий ключ — вже є в seen, не додасться
                .collect(Collectors.toList()));
        // Примітка: seen.add() вище вже відфільтрував дублікати при побудові creativeTabStacks

        filteredStacks = new ArrayList<>(allStacks);
    }

    /**
     * Стабільний ключ дедуплікації: "modid:itempath[|nbt_без_display]".
     * Поля display/Damage не враховуються (різні стаки одного предмету без значущого NBT
     * вважаються однаковими).
     */
    private static String stableKey(ItemStack st) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(st.getItem());
        String base = rl != null ? rl.toString() : st.getItem().toString();
        if (!st.hasTag()) return base;
        // Клонуємо тег, прибираємо нестабільні/несуттєві поля
        net.minecraft.nbt.CompoundTag tag = st.getTag().copy();
        tag.remove("display");
        tag.remove("Damage");
        tag.remove("RepairCost");
        String tagStr = tag.isEmpty() ? "" : "|" + tag;
        return base + tagStr;
    }

    @Override
    protected void init() {
        super.init();
        SLOT_SIZE = this.width < 320 ? 16 : 18;
        PREVIEW_W = Math.max(50, Math.min(80, this.width / 10));
        COLS      = Math.max(4, (this.width - PREVIEW_W - 20) / SLOT_SIZE);

        int cx = this.width / 2;

        // Пошук
        searchBox = new EditBox(this.font, cx - 80, 24, 200, 16, Component.translatable("wavedefense.label.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(s -> {
            searchQuery = s;
            scrollOffset = 0;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        // Категорії з кількістю
        Category[] cats = Category.values();
        int catW = Math.max(44, (this.width - PREVIEW_W - 20) / cats.length - 2);
        int catX = PREVIEW_W + 8;
        for (Category cat : cats) {
            final Category c = cat;
            boolean active = (cat == currentCategory);
            long cnt = allStacks.stream().filter(s -> matchesCategory(s, cat)).count();
            String lbl = (active ? "§e§l" : "§7") + I18n.get(cat.label) + " §8(" + cnt + ")";
            this.addRenderableWidget(Button.builder(
                    Component.literal(lbl),
                    b -> { currentCategory = c; scrollOffset = 0; applyFilter(); }
            ).bounds(catX, 44, catW, 14).build());
            catX += catW + 2;
        }

        // C3: Tacz sub-tabs — only when Tacz is loaded.
        // First button "All Tacz" resets filter so admin can include every Tacz gun.
        if (com.wavedefense.compat.TaczCompat.isLoaded()) {
            String[] subTabs = {
                com.wavedefense.compat.TaczCompat.CAT_ALL,
                com.wavedefense.compat.TaczCompat.CAT_PISTOL,
                com.wavedefense.compat.TaczCompat.CAT_RIFLE,
                com.wavedefense.compat.TaczCompat.CAT_SHOTGUN,
                com.wavedefense.compat.TaczCompat.CAT_SMG,
                com.wavedefense.compat.TaczCompat.CAT_SNIPER,
                com.wavedefense.compat.TaczCompat.CAT_RPG,
                com.wavedefense.compat.TaczCompat.CAT_MG,
                com.wavedefense.compat.TaczCompat.CAT_OTHER,
            };
            int subW = Math.max(40, (this.width - PREVIEW_W - 20) / subTabs.length - 2);
            int subX = PREVIEW_W + 8;
            for (String sub : subTabs) {
                final String fsub = sub;
                int cnt = com.wavedefense.compat.TaczCompat.getGunsByCategory(sub).size();
                boolean active = sub.equals(taczCategory);
                String langKey = "wavedefense.tacz.tab." + sub;
                String lbl = (active ? "§e§l" : "§7") + I18n.get(langKey) + " §8(" + cnt + ")";
                // Clicking the active sub-tab again clears the Tacz filter.
                this.addRenderableWidget(Button.builder(
                        Component.literal(lbl),
                        b -> {
                            taczCategory = active ? null : fsub;
                            scrollOffset = 0;
                            applyFilter();
                        }
                ).bounds(subX, 60, subW, 14).build());
                subX += subW + 2;
            }
        }

        // Закрити
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.close"),
                b -> this.minecraft.setScreen(parent)
        ).bounds(this.width - 72, 24, 68, 16).build());
    }

    private void applyFilter() {
        // C3: Tacz sub-tab overrides the regular category filter — when active, the list
        // comes from TaczCompat.getGunsByCategory() directly. Search still applies.
        if (taczCategory != null && com.wavedefense.compat.TaczCompat.isLoaded()) {
            filteredStacks = com.wavedefense.compat.TaczCompat
                .getGunsByCategory(taczCategory).stream()
                .map(e -> com.wavedefense.compat.TaczCompat.buildGunStack(e.gunId))
                .filter(s -> !s.isEmpty())
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
        } else {
            filteredStacks = allStacks.stream()
                    .filter(s -> matchesCategory(s, currentCategory))
                    .filter(this::matchesSearch)
                    .collect(Collectors.toList());
        }
        rebuildWidgets();
    }

    private boolean matchesCategory(ItemStack stack, Category cat) {
        Item item = stack.getItem();
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        String ns   = key != null ? key.getNamespace() : "";
        String path = key != null ? key.getPath() : "";

        // Тег-перевірки для модових предметів (tacz, moreweapons тощо)
        boolean isTagWeapon = stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "tools/sword")));
        boolean isModGun    = stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "guns")))
                           || stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("tacz",  "guns")));
        boolean isTagArmor  = stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "armors")))
                           || stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "armor")));

        return switch (cat) {
            case ALL    -> true;
            case WEAPON -> item instanceof SwordItem || item instanceof AxeItem
                        || item instanceof BowItem   || item instanceof CrossbowItem
                        || item instanceof TridentItem || item instanceof ShieldItem
                        || item instanceof PickaxeItem || item instanceof ShovelItem
                        || item instanceof HoeItem
                        || isTagWeapon || isModGun
                        // Fallback за назвою registry для незнайомих модів
                        || (!ns.equals("minecraft") && containsAny(path,
                               "gun","rifle","pistol","sword","knife","blade",
                               "sniper","shotgun","smg","rpg","launcher","weapon","firearm"));
            case ARMOR  -> item instanceof ArmorItem || isTagArmor
                        || (!ns.equals("minecraft") && containsAny(path,
                               "helmet","chestplate","leggings","boots","armor","vest","suit","plate"));
            case POTION -> item instanceof PotionItem || item instanceof SplashPotionItem
                        || item instanceof LingeringPotionItem || item instanceof TippedArrowItem;
            case FOOD   -> item.isEdible();
            case OTHER  -> !matchesCategory(stack, Category.WEAPON)
                        && !matchesCategory(stack, Category.ARMOR)
                        && !item.isEdible()
                        && !(item instanceof PotionItem)
                        && !(item instanceof SplashPotionItem)
                        && !(item instanceof LingeringPotionItem)
                        && !(item instanceof TippedArrowItem);
        };
    }

    /** true якщо {@code path} містить хоча б одне із ключових слів. */
    private static boolean containsAny(String path, String... keywords) {
        for (String kw : keywords) if (path.contains(kw)) return true;
        return false;
    }

    private boolean matchesSearch(ItemStack stack) {
        if (searchQuery.isEmpty()) return true;
        String q = searchQuery.toLowerCase();
        String name = "";
        try { name = stack.getHoverName().getString().toLowerCase(); } catch (Exception ignored) {}
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String regId = key != null ? key.toString().toLowerCase() : "";
        return name.contains(q) || regId.contains(q);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return super.charTyped(ch, modifiers);
    }

    private ItemStack hoveredStack = ItemStack.EMPTY;

    /** Y of the item grid — shifted down when Tacz sub-tabs are visible. */
    private int computeGridY() {
        return com.wavedefense.compat.TaczCompat.isLoaded() ? 78 : 64;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        previewAngle = (previewAngle + 0.5f) % 360f;

        int gridX = PREVIEW_W + 8;
        int gridY = computeGridY();
        int gridW = this.width - gridX - 10;
        int rows  = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;
        int gridBottom = gridY + rows * SLOT_SIZE;

        hoveredStack = ItemStack.EMPTY;

        // (Grid items moved inside scissor below)

        // Лічильник
        String counter = filteredStacks.size() + " " + I18n.get("wavedefense.item.count_suffix");
        if (!searchQuery.isEmpty()) counter += " (\"" + searchQuery + "\")";
        g.drawString(this.font, counter, PREVIEW_W + 8, this.height - 12, GuiTheme.TEXT_MUTED, false);

        // Скролбар (GuiTheme)
        int maxScroll = Math.max(0, filteredStacks.size() - perPage);
        GuiTheme.scrollBar(g, this.width - 7, gridY, gridBottom, scrollOffset, filteredStacks.size(), perPage);

        // ── Scissor: сітка предметів ─────────────────────────────────
        ScissorHelper.enable(gridX, gridY, gridW + 8, Math.max(1, gridBottom - gridY));
        // Рендер іконок сітки (тепер у scissor-зоні)
        // ── Рендер сітки предметів ────────────────────────────────────
        for (int i = 0; i < perPage; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredStacks.size()) break;
            ItemStack stack = filteredStacks.get(idx);
            int col = i % COLS, row = i / COLS;
            int sx = gridX + col * SLOT_SIZE;
            int sy = gridY + row * SLOT_SIZE;

            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, GuiTheme.BORDER);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, GuiTheme.PANEL_DARK);

            // Підсвічування вибраного (порівнюємо і item і теги)
            boolean isSelected = !currentItem.isEmpty()
                    && currentItem.getItem() == stack.getItem()
                    && ItemStack.isSameItemSameTags(currentItem, stack);
            if (isSelected) g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x55FFD700);

            int iconX = sx + (SLOT_SIZE - 16) / 2;
            int iconY = sy + (SLOT_SIZE - 16) / 2;
            g.renderItem(stack, iconX, iconY);
            g.renderItemDecorations(this.font, stack, iconX, iconY);

            if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0x44FFFFFF);
                hoveredStack = stack;
            }
        }
        // Widgets у межах сітки
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.getX() >= gridX
                    && w.getY() + w.getHeight() > gridY && w.getY() < gridBottom)
                w.render(g, mouseX, mouseY, partial);
        }
        ScissorHelper.disable();

        // Статичні: пошук (y=24), категорії (y=44), закрити (top-right)
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && (w.getY() < gridY || w.getX() < gridX))
                w.render(g, mouseX, mouseY, partial);
        }

        // Tooltip
        if (!hoveredStack.isEmpty())
            g.renderTooltip(this.font, hoveredStack, mouseX, mouseY);

        renderItemPreview(g);
    }

    private void renderItemPreview(GuiGraphics g) {
        int px = 4, py = 64;
        int pw = PREVIEW_W - 4;
        int ph = Math.min(pw, this.height - py - 40);

        ItemStack preview = hoveredStack.isEmpty() ? currentItem : hoveredStack;
        if (preview.isEmpty()) return;

        GuiTheme.panel(g, px - 1, py - 1, px + pw + 1, py + ph + 1);

        int iconX = px + (pw - 16) / 2;
        int iconY = py + (ph - 16) / 2;
        g.renderItem(preview, iconX, iconY);
        g.renderItemDecorations(this.font, preview, iconX, iconY);

        // Повна назва предмета (зілля мають довгу назву)
        String name = preview.getHoverName().getString();
        if (name.length() > 12) name = name.substring(0, 11) + "…";
        g.drawCenteredString(this.font, name, px + pw / 2, py + ph + 2, GuiTheme.TEXT);

        ResourceLocation key = ForgeRegistries.ITEMS.getKey(preview.getItem());
        if (key != null && !key.getNamespace().equals("minecraft")) {
            g.drawCenteredString(this.font, "[" + key.getNamespace() + "]",
                    px + pw / 2, py + ph + 12, GuiTheme.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int gridX  = PREVIEW_W + 8;
        int gridY  = computeGridY();
        int rows   = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;

        for (int i = 0; i < perPage; i++) {
            int idx = scrollOffset + i;
            if (idx >= filteredStacks.size()) break;
            int col = i % COLS, row = i / COLS;
            int sx = gridX + col * SLOT_SIZE;
            int sy = gridY + row * SLOT_SIZE;
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                ItemStack selected = filteredStacks.get(idx).copy();
                if (onSelect != null) onSelect.accept(selected);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridY   = computeGridY();
        int rows    = Math.max(1, (this.height - gridY - 24) / SLOT_SIZE);
        int perPage = rows * COLS;
        int maxScroll = Math.max(0, filteredStacks.size() - perPage);
        if (delta < 0) scrollOffset = Math.min(scrollOffset + COLS, maxScroll);
        else           scrollOffset = Math.max(scrollOffset - COLS, 0);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
