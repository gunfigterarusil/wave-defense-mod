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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocationEditorScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationEditorScreen.class);
    
    private final Location location;
    private final Screen parent;
    private int currentTab = 0;
    private EditBox totalWavesInput;
    private EditBox timeBetweenWavesInput;
    
    // Поля для координат спавну гравця
    private EditBox playerSpawnXInput;
    private EditBox playerSpawnYInput;
    private EditBox playerSpawnZInput;
    
    // Поля для координат спавну мобів
    private EditBox mobSpawnXInput;
    private EditBox mobSpawnYInput;
    private EditBox mobSpawnZInput;
    
    // Скролінг для списків
    private int mobSpawnsScrollOffset = 0;
    private static final int MOB_SPAWNS_PER_PAGE = 3;

    public LocationEditorScreen(Location location, Screen parent) {
        super(Component.literal("Редагування: " + location.getName()));
        this.location = location;
        this.parent = parent;
        LOGGER.info("Opened location editor for: {}", location.getName());
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

        totalWavesInput = new EditBox(this.font, centerX - 100, startY, 200, 20, Component.literal("Кількість хвиль"));
        totalWavesInput.setValue(String.valueOf(location.getTotalWaves()));
        totalWavesInput.setVisible(false);
        this.addRenderableWidget(totalWavesInput);

        timeBetweenWavesInput = new EditBox(this.font, centerX - 100, startY + 30, 200, 20, Component.literal("Час між хвилями"));
        timeBetweenWavesInput.setValue(String.valueOf(location.getTimeBetweenWaves()));
        timeBetweenWavesInput.setVisible(false);
        this.addRenderableWidget(timeBetweenWavesInput);

        if (currentTab == 0) {
            initBasicTab(centerX, startY);
        } else if (currentTab == 1) {
            initWavesTab(centerX, startY);
        } else if (currentTab == 2) {
            initShopTab(centerX, startY);
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"),
                button -> saveChanges()
        ).bounds(centerX - 160, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Назад"),
                button -> this.minecraft.setScreen(parent)
        ).bounds(centerX - 50, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("✕"),
                button -> this.onClose()
        ).bounds(centerX + 60, this.height - 30, 40, 20).build());
    }

    private void initBasicTab(int centerX, int startY) {
        // === ТОЧКА СПАВНУ ГРАВЦЯ ===
        this.addRenderableWidget(Button.builder(
                Component.literal("§6=== Точка спавну гравця ==="),
                button -> {}
        ).bounds(centerX - 150, startY, 300, 18).build()).active = false;

        BlockPos currentPlayerSpawn = location.getPlayerSpawn();
        
        // Мітка X
        this.addRenderableWidget(Button.builder(
                Component.literal("X:"),
                button -> {}
        ).bounds(centerX - 150, startY + 22, 20, 20).build()).active = false;
        
        playerSpawnXInput = new EditBox(this.font, centerX - 125, startY + 22, 50, 20,
                Component.literal("X координата"));
        playerSpawnXInput.setHint(Component.literal("X"));
        if (currentPlayerSpawn != null) {
            playerSpawnXInput.setValue(String.valueOf(currentPlayerSpawn.getX()));
        }
        playerSpawnXInput.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.addRenderableWidget(playerSpawnXInput);

        // Мітка Y
        this.addRenderableWidget(Button.builder(
                Component.literal("Y:"),
                button -> {}
        ).bounds(centerX - 70, startY + 22, 20, 20).build()).active = false;
        
        playerSpawnYInput = new EditBox(this.font, centerX - 45, startY + 22, 50, 20,
                Component.literal("Y координата"));
        playerSpawnYInput.setHint(Component.literal("Y"));
        if (currentPlayerSpawn != null) {
            playerSpawnYInput.setValue(String.valueOf(currentPlayerSpawn.getY()));
        }
        playerSpawnYInput.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.addRenderableWidget(playerSpawnYInput);

        // Мітка Z
        this.addRenderableWidget(Button.builder(
                Component.literal("Z:"),
                button -> {}
        ).bounds(centerX + 10, startY + 22, 20, 20).build()).active = false;
        
        playerSpawnZInput = new EditBox(this.font, centerX + 35, startY + 22, 50, 20,
                Component.literal("Z координата"));
        playerSpawnZInput.setHint(Component.literal("Z"));
        if (currentPlayerSpawn != null) {
            playerSpawnZInput.setValue(String.valueOf(currentPlayerSpawn.getZ()));
        }
        playerSpawnZInput.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.addRenderableWidget(playerSpawnZInput);

        // Кнопки
        this.addRenderableWidget(Button.builder(
                Component.literal("✓"),
                button -> setPlayerSpawnFromFields()
        ).bounds(centerX + 90, startY + 22, 30, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Поточна"),
                button -> setPlayerSpawnFromCurrentPos()
        ).bounds(centerX + 125, startY + 22, 60, 20).build());

        // Статус
        if (currentPlayerSpawn != null) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§a✓ Встановлено: [%d, %d, %d]",
                            currentPlayerSpawn.getX(), currentPlayerSpawn.getY(), currentPlayerSpawn.getZ())),
                    button -> {}
            ).bounds(centerX - 150, startY + 45, 300, 16).build()).active = false;
        } else {
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕ Не встановлено!"),
                    button -> {}
            ).bounds(centerX - 150, startY + 45, 300, 16).build()).active = false;
        }

        // === ТОЧКИ СПАВНУ МОБІВ ===
        int mobSpawnY = startY + 75;
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§6=== Точки спавну мобів (%d/10) ===", location.getMobSpawns().size())),
                button -> {}
        ).bounds(centerX - 150, mobSpawnY, 300, 18).build()).active = false;

        // Поля координат для мобів з підказками
        this.addRenderableWidget(Button.builder(
                Component.literal("X:"),
                button -> {}
        ).bounds(centerX - 150, mobSpawnY + 22, 20, 20).build()).active = false;
        
        mobSpawnXInput = new EditBox(this.font, centerX - 125, mobSpawnY + 22, 50, 20,
                Component.literal("X координата"));
        mobSpawnXInput.setHint(Component.literal("X"));
        mobSpawnXInput.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.addRenderableWidget(mobSpawnXInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Y:"),
                button -> {}
        ).bounds(centerX - 70, mobSpawnY + 22, 20, 20).build()).active = false;
        
        mobSpawnYInput = new EditBox(this.font, centerX - 45, mobSpawnY + 22, 50, 20,
                Component.literal("Y координата"));
        mobSpawnYInput.setHint(Component.literal("Y"));
        mobSpawnYInput.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.addRenderableWidget(mobSpawnYInput);

        this.addRenderableWidget(Button.builder(
                Component.literal("Z:"),
                button -> {}
        ).bounds(centerX + 10, mobSpawnY + 22, 20, 20).build()).active = false;
        
        mobSpawnZInput = new EditBox(this.font, centerX + 35, mobSpawnY + 22, 50, 20,
                Component.literal("Z координата"));
        mobSpawnZInput.setHint(Component.literal("Z"));
        mobSpawnZInput.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.addRenderableWidget(mobSpawnZInput);

        Button addMobSpawnButton = Button.builder(
                Component.literal("➕"),
                button -> addMobSpawnFromFields()
        ).bounds(centerX + 90, mobSpawnY + 22, 30, 20).build();
        addMobSpawnButton.active = location.getMobSpawns().size() < 10;
        this.addRenderableWidget(addMobSpawnButton);

        Button addCurrentPosButton = Button.builder(
                Component.literal("Поточна"),
                button -> addMobSpawnFromCurrentPos()
        ).bounds(centerX + 125, mobSpawnY + 22, 60, 20).build();
        addCurrentPosButton.active = location.getMobSpawns().size() < 10;
        this.addRenderableWidget(addCurrentPosButton);

        // Список точок спавну зі скролінгом
        int listY = mobSpawnY + 52;
        for (int i = 0; i < Math.min(MOB_SPAWNS_PER_PAGE, location.getMobSpawns().size()); i++) {
            int index = i + mobSpawnsScrollOffset;
            if (index >= location.getMobSpawns().size()) break;

            BlockPos pos = location.getMobSpawns().get(index);
            final int finalIndex = index;

            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7#%d §f[%d, %d, %d]",
                            index + 1, pos.getX(), pos.getY(), pos.getZ())),
                    button -> {}
            ).bounds(centerX - 150, listY + (i * 25), 250, 20).build()).active = false;

            this.addRenderableWidget(Button.builder(
                    Component.literal("✕"),
                    button -> removeMobSpawn(finalIndex)
            ).bounds(centerX + 105, listY + (i * 25), 25, 20).build());
        }

        // Кнопки прокрутки для точок спавну
        if (location.getMobSpawns().size() > MOB_SPAWNS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("▲"),
                    button -> scrollMobSpawnsUp()
            ).bounds(centerX + 135, listY, 25, 25).build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("▼"),
                    button -> scrollMobSpawnsDown()
            ).bounds(centerX + 135, listY + 50, 25, 25).build());
        }

        if (location.getMobSpawns().size() > MOB_SPAWNS_PER_PAGE) {
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7... ще %d точок", 
                            location.getMobSpawns().size() - MOB_SPAWNS_PER_PAGE)),
                    button -> {}
            ).bounds(centerX - 150, listY + 80, 250, 16).build()).active = false;
        }

        // Інвентар
        int invY = this.height - 90;
        this.addRenderableWidget(Button.builder(
                Component.literal(location.isKeepInventory() ? 
                        "§a☑ Зберігати інвентар" : "§c☐ Зберігати інвентар"),
                button -> toggleKeepInventory()
        ).bounds(centerX - 150, invY, 200, 20).build());

        if (!location.isKeepInventory()) {
            this.addRenderableWidget(Button.builder(
                    Component.literal("⚙ Стартове спорядження"),
                    button -> openStartingItemsScreen()
            ).bounds(centerX - 150, invY + 25, 200, 20).build());
        }
    }

    private void initWavesTab(int centerX, int startY) {
        totalWavesInput.setVisible(true);
        timeBetweenWavesInput.setVisible(true);

        this.addRenderableWidget(Button.builder(
                Component.literal("⚙ Налаштувати хвилі та мобів"),
                button -> openWaveConfig()
        ).bounds(centerX - 100, startY + 60, 200, 25).build());
    }

    private void initShopTab(int centerX, int startY) {
        this.addRenderableWidget(Button.builder(
                Component.literal(String.format("§6Товарів: §e%d", location.getShopItems().size())),
                button -> {}
        ).bounds(centerX - 150, startY, 300, 20).build()).active = false;

        this.addRenderableWidget(Button.builder(
                Component.literal("🛒 Редагувати магазин"),
                button -> openShopEditor()
        ).bounds(centerX - 100, startY + 30, 200, 25).build());
    }

    private void setPlayerSpawnFromFields() {
        try {
            int x = playerSpawnXInput.getValue().isEmpty() ? 
                    (minecraft.player != null ? minecraft.player.blockPosition().getX() : 0) : 
                    Integer.parseInt(playerSpawnXInput.getValue());
            int y = playerSpawnYInput.getValue().isEmpty() ? 
                    (minecraft.player != null ? minecraft.player.blockPosition().getY() : 0) : 
                    Integer.parseInt(playerSpawnYInput.getValue());
            int z = playerSpawnZInput.getValue().isEmpty() ? 
                    (minecraft.player != null ? minecraft.player.blockPosition().getZ() : 0) : 
                    Integer.parseInt(playerSpawnZInput.getValue());
            
            location.setPlayerSpawn(new BlockPos(x, y, z));
            LOGGER.info("Set player spawn to: [{}, {}, {}]", x, y, z);
            this.rebuildWidgets();
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid coordinates for player spawn", e);
            showError("Введіть коректні координати");
        }
    }

    private void setPlayerSpawnFromCurrentPos() {
        if (minecraft.player != null) {
            BlockPos pos = minecraft.player.blockPosition();
            location.setPlayerSpawn(pos);
            LOGGER.info("Set player spawn to current position: [{}, {}, {}]", pos.getX(), pos.getY(), pos.getZ());
            this.rebuildWidgets();
        }
    }

    private void addMobSpawnFromFields() {
        if (location.getMobSpawns().size() >= 10) {
            showError("Досягнуто максимум (10 точок)");
            return;
        }
        
        try {
            int x = mobSpawnXInput.getValue().isEmpty() ? 
                    (minecraft.player != null ? minecraft.player.blockPosition().getX() : 0) : 
                    Integer.parseInt(mobSpawnXInput.getValue());
            int y = mobSpawnYInput.getValue().isEmpty() ? 
                    (minecraft.player != null ? minecraft.player.blockPosition().getY() : 0) : 
                    Integer.parseInt(mobSpawnYInput.getValue());
            int z = mobSpawnZInput.getValue().isEmpty() ? 
                    (minecraft.player != null ? minecraft.player.blockPosition().getZ() : 0) : 
                    Integer.parseInt(mobSpawnZInput.getValue());
            
            location.addMobSpawn(new BlockPos(x, y, z));
            LOGGER.info("Added mob spawn point: [{}, {}, {}]", x, y, z);
            mobSpawnXInput.setValue("");
            mobSpawnYInput.setValue("");
            mobSpawnZInput.setValue("");
            this.rebuildWidgets();
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid coordinates for mob spawn", e);
            showError("Введіть коректні координати");
        }
    }

    private void addMobSpawnFromCurrentPos() {
        if (minecraft.player != null && location.getMobSpawns().size() < 10) {
            BlockPos pos = minecraft.player.blockPosition();
            location.addMobSpawn(pos);
            LOGGER.info("Added mob spawn at current position: [{}, {}, {}]", pos.getX(), pos.getY(), pos.getZ());
            this.rebuildWidgets();
        }
    }

    private void removeMobSpawn(int index) {
        location.removeMobSpawn(index);
        LOGGER.info("Removed mob spawn at index: {}", index);
        
        // Коригуємо прокрутку якщо потрібно
        if (mobSpawnsScrollOffset > 0 && mobSpawnsScrollOffset >= location.getMobSpawns().size()) {
            mobSpawnsScrollOffset = Math.max(0, location.getMobSpawns().size() - MOB_SPAWNS_PER_PAGE);
        }
        
        this.rebuildWidgets();
    }

    private void scrollMobSpawnsUp() {
        if (mobSpawnsScrollOffset > 0) {
            mobSpawnsScrollOffset--;
            this.rebuildWidgets();
        }
    }

    private void scrollMobSpawnsDown() {
        if (mobSpawnsScrollOffset + MOB_SPAWNS_PER_PAGE < location.getMobSpawns().size()) {
            mobSpawnsScrollOffset++;
            this.rebuildWidgets();
        }
    }

    private void toggleKeepInventory() {
        location.setKeepInventory(!location.isKeepInventory());
        LOGGER.info("Toggled keep inventory: {}", location.isKeepInventory());
        this.rebuildWidgets();
    }

    private void openStartingItemsScreen() {
        this.minecraft.setScreen(new StartingItemsScreen(this, location));
    }

    private void switchTab(int tab) {
        this.currentTab = tab;
        LOGGER.debug("Switched to tab: {}", tab);
        this.rebuildWidgets();
    }

    private void openWaveConfig() {
        this.minecraft.setScreen(new WaveConfigScreen(location, this));
    }

    private void openShopEditor() {
        this.minecraft.setScreen(new ShopEditorScreen(location, this));
    }

    private void saveChanges() {
        if (currentTab == 1) {
            try {
                int totalWaves = Integer.parseInt(totalWavesInput.getValue());
                location.setTotalWaves(totalWaves);
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid total waves value", e);
            }

            try {
                int timeBetween = Integer.parseInt(timeBetweenWavesInput.getValue());
                location.setTimeBetweenWaves(timeBetween);
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid time between waves value", e);
            }
        }

        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        LOGGER.info("Saved changes for location: {}", location.getName());
        
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("§a✓ Зміни збережено!"),
                    true
            );
        }
    }

    private void showError(String message) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("§c✕ " + message),
                    true
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§6§l" + this.title.getString(), this.width / 2, 10, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
  
