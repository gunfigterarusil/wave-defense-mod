package com.wavedefense.gui;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class LocationEditorScreen extends Screen {

    private final Location location;
    private final Screen parent;
    private int currentTab = 0; // 0=основні, 1=хвилі, 2=магазин

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
        Button basicTab = Button.builder(
                Component.literal(currentTab == 0 ? "§a§l⬤ Основні" : "§7○ Основні"),
                button -> switchTab(0)
        ).bounds(centerX - 160, 25, 100, 20).build();
        this.addRenderableWidget(basicTab);

        Button wavesTab = Button.builder(
                Component.literal(currentTab == 1 ? "§a§l⬤ Хвилі" : "§7○ Хвилі"),
                button -> switchTab(1)
        ).bounds(centerX - 50, 25, 100, 20).build();
        this.addRenderableWidget(wavesTab);

        Button shopTab = Button.builder(
                Component.literal(currentTab == 2 ? "§a§l⬤ Магазин" : "§7○ Магазин"),
                button -> switchTab(2)
        ).bounds(centerX + 60, 25, 100, 20).build();
        this.addRenderableWidget(shopTab);

        if (currentTab == 0) {
            initBasicTab(centerX, startY);
        } else if (currentTab == 1) {
            initWavesTab(centerX, startY);
        } else if (currentTab == 2) {
            initShopTab(centerX, startY);
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
        this.addRenderableWidget(Button.builder(
                Component.literal("📍 Встановити точку спавну гравця"),
                button -> setPlayerSpawn()
        ).bounds(centerX - 150, startY, 300, 20).build());

        BlockPos playerSpawn = location.getPlayerSpawn();
        if (playerSpawn != null) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§aПоточна: X:%d Y:%d Z:%d",
                            playerSpawn.getX(), playerSpawn.getY(), playerSpawn.getZ())),
                    button -> {}
            ).bounds(centerX - 150, startY + 22, 300, 18).build()).active = false;
        } else {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§cТочка спавну не встановлена!"),
                    button -> {}
            ).bounds(centerX - 150, startY + 22, 300, 18).build()).active = false;
        }

        int mobSpawnY = startY + 55;
        this.addRenderableWidget(Button.builder(
                Component.literal("➕ Додати точку спавну мобів"),
                button -> addMobSpawn()
        ).bounds(centerX - 150, mobSpawnY, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§7Налаштовано: %d/10", location.getMobSpawns().size())),
                button -> {}
        ).bounds(centerX - 150, mobSpawnY + 22, 200, 18).build()).active = false;

        int listY = mobSpawnY + 50;
        for (int i = 0; i < Math.min(5, location.getMobSpawns().size()); i++) {
            BlockPos pos = location.getMobSpawns().get(i);
            final int index = i;

            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7#%d: §fX:%d Y:%d Z:%d",
                            i + 1, pos.getX(), pos.getY(), pos.getZ())),
                    button -> {}
            ).bounds(centerX - 150, listY + (i * 22), 220, 20).build()).active = false;

            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> removeMobSpawn(index)
            ).bounds(centerX + 75, listY + (i * 22), 25, 20).build());
        }

        if (location.getMobSpawns().size() > 5) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7... ще %d точок", location.getMobSpawns().size() - 5)),
                    button -> {}
            ).bounds(centerX - 150, listY + 110, 250, 18).build()).active = false;
        }

        int invY = this.height - 90;
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
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§6Налаштовано хвиль: §e%d", location.getWaves().size())),
                button -> {}
        ).bounds(centerX - 150, startY, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("⚙ Налаштувати хвилі"),
                button -> openWaveConfig()
        ).bounds(centerX - 100, startY + 30, 200, 25).build());

        if (!location.getWaves().isEmpty()) {
            int previewY = startY + 70;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Попередній перегляд:"),
                    button -> {}
            ).bounds(centerX - 150, previewY, 300, 18).build()).active = false;

            for (int i = 0; i < Math.min(5, location.getWaves().size()); i++) {
                String info = String.format("§7Хвиля %d: §f%d типів мобів, %d нагород",
                        i + 1,
                        location.getWaves().get(i).getMobs().size(),
                        location.getWaves().get(i).getRewards().size()
                );

                this.addRenderableWidget(Button.builder(
                        Component.literal(info),
                        button -> {}
                ).bounds(centerX - 150, previewY + 20 + (i * 20), 300, 18).build()).active = false;
            }

            if (location.getWaves().size() > 5) {
                this.addRenderableWidget(Button.builder(
                        Component.literal(String.format("§7... ще %d хвиль", location.getWaves().size() - 5)),
                        button -> {}
                ).bounds(centerX - 150, previewY + 120, 300, 18).build()).active = false;
            }
        }
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
                    Component.literal("§7Попередній перегляд товарів:"),
                    button -> {}
            ).bounds(centerX - 150, previewY, 300, 18).build()).active = false;

            for (int i = 0; i < Math.min(5, location.getShopItems().size()); i++) {
                var shopItem = location.getShopItems().get(i);
                String itemName = shopItem.getItem().getHoverName().getString();
                if (itemName.length() > 20) {
                    itemName = itemName.substring(0, 17) + "...";
                }

                String info = String.format("§e%s §7- Купівля: §6%d §7Продаж: §a%d",
                        itemName,
                        shopItem.getBuyPrice(),
                        shopItem.getSellPrice()
                );

                this.addRenderableWidget(Button.builder(
                        Component.literal(info),
                        button -> {}
                ).bounds(centerX - 150, previewY + 20 + (i * 20), 300, 18).build()).active = false;
            }

            if (location.getShopItems().size() > 5) {
                this.addRenderableWidget(Button.builder(
                        Component.literal(String.format("§7... ще %d товарів", location.getShopItems().size() - 5)),
                        button -> {}
                ).bounds(centerX - 150, previewY + 120, 300, 18).build()).active = false;
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

    private void openShopEditor() {
        this.minecraft.setScreen(new ShopEditorScreen(location, this));
    }

    private void saveChanges() {
        WaveDefenseMod.locationManager.save();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("§a✓ Зміни збережено!"),
                    true
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 10, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
