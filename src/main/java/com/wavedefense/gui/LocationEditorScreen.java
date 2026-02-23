package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class LocationEditorScreen extends Screen {

    private final Location location;
    private final Screen parent;
    private int currentTab = 0; // 0=основні, 1=хвилі, 2=магазин, 3=лут
    private int mobSpawnScrollOffset = 0;
    private static final int MOB_SPAWN_PER_PAGE = 5;

    public LocationEditorScreen(Location location, Screen parent) {
        super(Component.literal("Редагування: " + location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int startY = 50;

        // Вкладки
        this.addRenderableWidget(Button.builder(
                Component.literal(currentTab == 0 ? "§a§l⬤ Основні" : "§7○ Основні"),
                button -> switchTab(0)
        ).bounds(centerX - 160, 25, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(currentTab == 1 ? "§a§l⬤ Хвилі" : "§7○ Хвилі"),
                button -> switchTab(1)
        ).bounds(centerX - 50, 25, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(currentTab == 2 ? "§a§l⬤ Магазин" : "§7○ Магазин"),
                button -> switchTab(2)
        ).bounds(centerX + 60, 25, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(currentTab == 3 ? "§a§l⬤ Лут" : "§7○ Лут"),
                button -> switchTab(3)
        ).bounds(centerX + 165, 25, 70, 20).build());

        if (currentTab == 0) {
            initBasicTab(centerX, startY);
        } else if (currentTab == 1) {
            initWavesTab(centerX, startY);
        } else if (currentTab == 2) {
            initShopTab(centerX, startY);
        } else if (currentTab == 3) {
            initLootTab(centerX, startY);
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти зміни"),
                button -> saveChanges()
        ).bounds(centerX - 160, this.height - 30, 130, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Назад до списку"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX - 20, this.height - 30, 130, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Закрити"),
                button -> this.onClose()
        ).bounds(centerX + 120, this.height - 30, 40, 20).build());
    }

    private void initBasicTab(int centerX, int startY) {
        // Точка спавну гравця
        this.addRenderableWidget(Button.builder(
                Component.literal("📍 Встановити точку спавну гравця"),
                button -> setPlayerSpawn()
        ).bounds(centerX - 150, startY, 300, 20).build());

        BlockPos playerSpawn = location.getPlayerSpawn();
        String spawnText = playerSpawn != null
                ? String.format("§aПоточна: X:%d Y:%d Z:%d", playerSpawn.getX(), playerSpawn.getY(), playerSpawn.getZ())
                : "§cТочка спавну не встановлена!";
        this.addRenderableWidget(Button.builder(
                Component.literal(spawnText), button -> {}
        ).bounds(centerX - 150, startY + 22, 300, 18).build()).active = false;

        // Точки спавну мобів
        int mobSpawnY = startY + 50;
        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати точку спавну мобів"),
                button -> addMobSpawn()
        ).bounds(centerX - 150, mobSpawnY, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§7Налаштовано: %d/10", location.getMobSpawns().size())),
                button -> {}
        ).bounds(centerX - 150, mobSpawnY + 22, 200, 18).build()).active = false;

        // Список точок зі скролом
        int listY = mobSpawnY + 48;
        int maxVisible = Math.min(MOB_SPAWN_PER_PAGE, location.getMobSpawns().size());
        for (int i = 0; i < maxVisible; i++) {
            int realIndex = i + mobSpawnScrollOffset;
            if (realIndex >= location.getMobSpawns().size()) break;
            BlockPos pos = location.getMobSpawns().get(realIndex);
            final int index = realIndex;
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7#%d: §fX:%d Y:%d Z:%d", realIndex + 1, pos.getX(), pos.getY(), pos.getZ())),
                    button -> {}
            ).bounds(centerX - 150, listY + (i * 22), 220, 20).build()).active = false;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> removeMobSpawn(index)
            ).bounds(centerX + 75, listY + (i * 22), 25, 20).build());
        }

        if (location.getMobSpawns().size() > MOB_SPAWN_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> { if (mobSpawnScrollOffset > 0) { mobSpawnScrollOffset--; rebuildWidgets(); } }
            ).bounds(centerX + 105, listY, 20, 20).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> { if (mobSpawnScrollOffset + MOB_SPAWN_PER_PAGE < location.getMobSpawns().size()) { mobSpawnScrollOffset++; rebuildWidgets(); } }
            ).bounds(centerX + 105, listY + (MOB_SPAWN_PER_PAGE - 1) * 22, 20, 20).build());
        }

        int invY = listY + MOB_SPAWN_PER_PAGE * 22 + 8;
        if (invY > this.height - 90) invY = this.height - 90;

        this.addRenderableWidget(Button.builder(
                Component.literal(location.isKeepInventory() ? "§a☑ Зберігати інвентар" : "§c☐ Зберігати інвентар"),
                button -> toggleKeepInventory()
        ).bounds(centerX - 150, invY, 200, 20).build());

        if (!location.isKeepInventory()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("⚙ Налаштувати стартове спорядження"),
                    button -> openStartingItemsScreen()
            ).bounds(centerX - 150, invY + 25, 300, 20).build());
        }
    }

    private void initWavesTab(int centerX, int startY) {
        // Тут тільки кнопки переходу до підменю — дублів полів вводу немає
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Налаштування хвиль (моби, час, нагороди):"),
                button -> {}
        ).bounds(centerX - 150, startY, 300, 18).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("⚙ Налаштувати моби та хвилі"),
                button -> openWaveConfig()
        ).bounds(centerX - 130, startY + 26, 260, 24).build());

        // Інфо про поточний стан
        int waveCount = location.getWaves().size();
        String waveInfo = waveCount > 0
                ? String.format("§7Хвиль: §e%d §7| Час між хвилями: §e%d сек", waveCount,
                    location.getWaves().isEmpty() ? 0 : location.getWaves().get(0).getTimeBetweenWaves())
                : "§cХвилі не налаштовані";
        this.addRenderableWidget(Button.builder(
                Component.literal(waveInfo), button -> {}
        ).bounds(centerX - 150, startY + 58, 300, 18).build()).active = false;

        // Нагороди за проходження локації
        this.addRenderableWidget(Button.builder(
                Component.literal("§6🏆 Нагороди за проходження локації"),
                button -> openCompletionRewards()
        ).bounds(centerX - 130, startY + 84, 260, 24).build());

        String rewardInfo = String.format("§7Поінти: §e%d §7| Предмети: §e%d",
                location.getCompletionPointsReward(), location.getCompletionRewards().size());
        this.addRenderableWidget(Button.builder(
                Component.literal(rewardInfo), button -> {}
        ).bounds(centerX - 150, startY + 116, 300, 18).build()).active = false;
    }

    private void initShopTab(int centerX, int startY) {
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§6Товарів у магазині: §e%d", location.getShopItems().size())),
                button -> {}
        ).bounds(centerX - 150, startY, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("🛒 Редагувати магазин"),
                button -> openShopEditor()
        ).bounds(centerX - 100, startY + 30, 200, 25).build());

        if (!location.getShopItems().isEmpty()) {
            int previewY = startY + 70;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Попередній перегляд:"), button -> {}
            ).bounds(centerX - 150, previewY, 300, 18).build()).active = false;

            for (int i = 0; i < Math.min(5, location.getShopItems().size()); i++) {
                var shopItem = location.getShopItems().get(i);
                String itemName = shopItem.getItems().get(0).getHoverName().getString();
                if (itemName.length() > 20) itemName = itemName.substring(0, 17) + "...";
                String info = String.format("§e%s §7- Купівля: §6%d §7Продаж: §a%d",
                        itemName, shopItem.getBuyPrice(), shopItem.getSellPrice());
                this.addRenderableWidget(Button.builder(
                        Component.literal(info), button -> {}
                ).bounds(centerX - 150, previewY + 20 + (i * 20), 300, 18).build()).active = false;
            }
        }
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        this.rebuildWidgets();
    }

    private void setPlayerSpawn() {
        if (minecraft.player != null) {
            location.setPlayerSpawn(minecraft.player.blockPosition());
            this.rebuildWidgets();
        }
    }

    private void addMobSpawn() {
        if (minecraft.player != null && location.getMobSpawns().size() < 10) {
            location.addMobSpawn(minecraft.player.blockPosition());
            this.rebuildWidgets();
        }
    }

    private void removeMobSpawn(int index) {
        location.removeMobSpawn(index);
        if (mobSpawnScrollOffset > 0 && mobSpawnScrollOffset >= location.getMobSpawns().size()) {
            mobSpawnScrollOffset = Math.max(0, location.getMobSpawns().size() - MOB_SPAWN_PER_PAGE);
        }
        this.rebuildWidgets();
    }

    private void toggleKeepInventory() {
        location.setKeepInventory(!location.isKeepInventory());
        this.rebuildWidgets();
    }

    private void openStartingItemsScreen() {
        this.minecraft.setScreen(new StartingItemsScreen(this, location));
    }

    private void openWaveConfig() {
        this.minecraft.setScreen(new WaveConfigScreen(location, this));
    }

    private void openCompletionRewards() {
        this.minecraft.setScreen(new CompletionRewardScreen(location, this));
    }

    private void initLootTab(int centerX, int startY) {
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§7Точок луту: §e%d", location.getLootSpawns().size())),
                button -> {}
        ).bounds(centerX - 150, startY, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("📦 Редагувати точки луту"),
                button -> openLootEditor()
        ).bounds(centerX - 100, startY + 30, 200, 25).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Лут з'являється у вказаних точках з заданим шансом"),
                button -> {}
        ).bounds(centerX - 150, startY + 64, 300, 18).build()).active = false;
    }

    private void openLootEditor() {
        this.minecraft.setScreen(new LootSpawnEditorScreen(location, this));
    }

    private void openShopEditor() {
        this.minecraft.setScreen(new ShopEditorScreen(location, this));
    }

    private void saveChanges() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§a✓ Зміни збережено!"), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == 0) {
            if (delta > 0 && mobSpawnScrollOffset > 0) { mobSpawnScrollOffset--; rebuildWidgets(); }
            else if (delta < 0 && mobSpawnScrollOffset + MOB_SPAWN_PER_PAGE < location.getMobSpawns().size()) { mobSpawnScrollOffset++; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 10, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
