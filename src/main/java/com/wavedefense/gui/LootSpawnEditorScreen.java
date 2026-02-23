package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.LootSpawn;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LootSpawnEditorScreen extends Screen {
    private final Location location;
    private final Screen parent;

    private boolean editingItem = false;
    private int editingIndex = -1;
    private List<ItemStack> editItems = new ArrayList<>();
    private EditBox chanceInput;
    private EditBox countInput;

    private int scrollOffset = 0;
    private static final int PER_PAGE = 5;

    public LootSpawnEditorScreen(Location location, Screen parent) {
        super(Component.literal("Точки луту: " + location.getName()));
        this.location = location;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 35;

        if (editingItem) {
            initEditMode(cx, y);
            return;
        }

        // Кнопка: встановити нову точку луту (позиція береться з позиції гравця)
        this.addRenderableWidget(Button.builder(
                Component.literal("§e➕ Додати точку луту (на позиції гравця)"),
                button -> startAddAtPlayerPos()
        ).bounds(cx - 175, y, 260, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§7" + location.getLootSpawns().size() + " точок"),
                button -> {}
        ).bounds(cx + 90, y, 90, 20).build()).active = false;

        y += 28;

        List<LootSpawn> spawns = location.getLootSpawns();
        int rowH = 38;
        for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= spawns.size()) break;
            LootSpawn ls = spawns.get(idx);
            int yPos = y + i * rowH;

            BlockPos pos = ls.getPos();
            List<ItemStack> items = ls.getItems().stream().filter(it -> !it.isEmpty()).collect(Collectors.toList());
            String firstName = items.isEmpty() ? "порожньо" : items.get(0).getHoverName().getString();
            if (firstName.length() > 15) firstName = firstName.substring(0, 12) + "...";
            if (items.size() > 1) firstName += " (+" + (items.size() - 1) + ")";

            String label = String.format("§e%s §7| X%d Y%d Z%d §7| %d%% x%d",
                    firstName, pos.getX(), pos.getY(), pos.getZ(), ls.getSpawnChance(), ls.getCount());
            this.addRenderableWidget(Button.builder(
                    Component.literal(label), button -> {}
            ).bounds(cx - 175, yPos, 310, 17).build()).active = false;

            // Іконки предметів — рендеряться у render()
            // Кнопки управління
            final int fIdx = idx;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"), button -> startEditItem(fIdx)
            ).bounds(cx + 140, yPos, 22, 17).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("§c✕"), button -> { location.removeLootSpawn(fIdx); rebuildWidgets(); }
            ).bounds(cx + 165, yPos, 22, 17).build());

            // Іконки у рядку 2
            for (int j = 0; j < Math.min(4, items.size()); j++) {
                this.addRenderableWidget(Button.builder(
                        Component.literal(""), button -> {}
                ).bounds(cx - 175 + j * 22, yPos + 18, 20, 18).build()).active = false;
            }
        }

        if (spawns.size() > PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    button -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 192, y, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    button -> { if (scrollOffset + PER_PAGE < spawns.size()) { scrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + 192, y + (PER_PAGE - 1) * rowH, 18, 18).build());
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти і назад"), button -> saveAndBack()
        ).bounds(cx - 110, this.height - 28, 220, 20).build());
    }

    private void initEditMode(int cx, int y) {
        // Позиція відображається як підпис
        if (editingIndex >= 0 && editingIndex < location.getLootSpawns().size()) {
            BlockPos pos = location.getLootSpawns().get(editingIndex).getPos();
            this.addRenderableWidget(Button.builder(
                    Component.literal(String.format("§7Позиція: X%d Y%d Z%d", pos.getX(), pos.getY(), pos.getZ())),
                    button -> {}
            ).bounds(cx - 160, y, 320, 16).build()).active = false;
            y += 20;
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§7Предмети (до 4 шт., візьміть у руку → «Встановити»):"),
                button -> {}
        ).bounds(cx - 160, y, 320, 16).build()).active = false;
        y += 18;

        int slotW = 75;
        for (int i = 0; i < 4; i++) {
            int xPos = cx - 160 + i * slotW;
            final int si = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal("§7Слот " + (i + 1)), button -> {}
            ).bounds(xPos, y, slotW - 4, 12).build()).active = false;
            this.addRenderableWidget(Button.builder(
                    Component.literal("Встановити"), button -> setItem(si)
            ).bounds(xPos, y + 22, slotW - 4, 16).build());
            this.addRenderableWidget(Button.builder(
                    Component.literal("Очистити"), button -> clearItem(si)
            ).bounds(xPos, y + 41, slotW - 4, 16).build());
        }
        y += 66;

        // Шанс появи
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Шанс появи (1–100%):"), button -> {}
        ).bounds(cx - 160, y, 160, 18).build()).active = false;
        chanceInput = new EditBox(this.font, cx - 160 + 165, y, 55, 20, Component.literal("Шанс"));
        chanceInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getSpawnChance()) : "100");
        chanceInput.setMaxLength(3);
        this.addRenderableWidget(chanceInput);
        y += 26;

        // Кількість
        this.addRenderableWidget(Button.builder(
                Component.literal("§7Кількість кожного предмета:"), button -> {}
        ).bounds(cx - 160, y, 190, 18).build()).active = false;
        countInput = new EditBox(this.font, cx - 160 + 195, y, 45, 20, Component.literal("К-сть"));
        countInput.setValue(editingIndex >= 0
                ? String.valueOf(location.getLootSpawns().get(editingIndex).getCount()) : "1");
        countInput.setMaxLength(3);
        this.addRenderableWidget(countInput);
        y += 30;

        this.addRenderableWidget(Button.builder(
                Component.literal("§a✓ Зберегти"), button -> saveItem()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal("Скасувати"), button -> { editingItem = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    private void startAddAtPlayerPos() {
        if (minecraft.player == null) return;
        editingItem = true;
        editingIndex = -1;
        editItems = new ArrayList<>();
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void startEditItem(int idx) {
        editingItem = true;
        editingIndex = idx;
        LootSpawn ls = location.getLootSpawns().get(idx);
        editItems = new ArrayList<>(ls.getItems());
        while (editItems.size() < 4) editItems.add(ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void setItem(int slot) {
        if (minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
            editItems.set(slot, minecraft.player.getMainHandItem().copy());
            rebuildWidgets();
        }
    }

    private void clearItem(int slot) {
        editItems.set(slot, ItemStack.EMPTY);
        rebuildWidgets();
    }

    private void saveItem() {
        try {
            int chance = Math.max(1, Math.min(100, Integer.parseInt(chanceInput.getValue())));
            int count = Math.max(1, Integer.parseInt(countInput.getValue()));
            List<ItemStack> finalItems = editItems.stream().filter(i -> !i.isEmpty()).collect(Collectors.toList());
            if (finalItems.isEmpty()) return;

            if (editingIndex >= 0) {
                LootSpawn existing = location.getLootSpawns().get(editingIndex);
                existing.setItems(finalItems);
                existing.setSpawnChance(chance);
                existing.setCount(count);
            } else {
                // Позиція гравця
                BlockPos pos = minecraft.player != null ? minecraft.player.blockPosition() : BlockPos.ZERO;
                location.addLootSpawn(new LootSpawn(pos, finalItems, chance, count));
            }
            editingItem = false;
            rebuildWidgets();
        } catch (NumberFormatException ignored) {}
    }

    private void saveAndBack() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("§a✓ Точки луту збережено!"), true);
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int cx = this.width / 2;

        if (editingItem) {
            int slotW = 75;
            for (int i = 0; i < 4; i++) {
                int xPos = cx - 160 + i * slotW;
                ItemStack item = editItems.get(i);
                graphics.renderItem(item, xPos + 27, 78);
                graphics.renderItemDecorations(this.font, item, xPos + 27, 78);
            }
        } else {
            int listY = 63;
            int rowH = 38;
            List<LootSpawn> spawns = location.getLootSpawns();
            for (int i = 0; i < Math.min(PER_PAGE, spawns.size()); i++) {
                int idx = i + scrollOffset;
                if (idx >= spawns.size()) break;
                List<ItemStack> items = spawns.get(idx).getItems().stream()
                        .filter(it -> !it.isEmpty()).collect(Collectors.toList());
                int yPos = listY + i * rowH;
                for (int j = 0; j < Math.min(4, items.size()); j++) {
                    graphics.renderItem(items.get(j), cx - 175 + j * 22, yPos + 18);
                    graphics.renderItemDecorations(this.font, items.get(j), cx - 175 + j * 22, yPos + 18);
                }
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!editingItem) {
            if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
            else if (delta < 0 && scrollOffset + PER_PAGE < location.getLootSpawns().size()) { scrollOffset++; rebuildWidgets(); }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
