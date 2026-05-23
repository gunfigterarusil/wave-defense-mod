package com.wavedefense.gui;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Редактор точки магазину:
 *   — назва
 *   — позиція (взяти поточну / вручну)
 *   — радіус доступу
 *   — список товарів точки
 */
public class ShopPointEditorScreen extends Screen {

    private final Location location;
    private final int      pointIndex; // -1 = новий
    private final Screen   parent;

    private ShopPoint editingPoint;

    private EditBox nameInput;
    private EditBox radiusInput;
    private CoordinateInputField posCoordField;

    private int scrollOffset = 0;
    private static final int ROW_H = 58;
    private int itemsPerPage = 3; // обчислюється динамічно в init()
    private int listStartY = 148; // оновлюється в init()
    private int listBotY   = 0;   // оновлюється в init()

    public ShopPointEditorScreen(Location location, int pointIndex, Screen parent) {
        super(pointIndex >= 0 ? Component.translatable("wavedefense.title.edit_shop_point") : Component.translatable("wavedefense.title.new_shop_point"));
        this.location   = location;
        this.pointIndex = pointIndex;
        this.parent     = parent;

        if (pointIndex >= 0 && pointIndex < location.getShopPoints().size()) {
            this.editingPoint = location.getShopPoints().get(pointIndex); // редагуємо in-place
        } else {
            this.editingPoint = new ShopPoint(I18n.get("wavedefense.shop.default_name"), null, 5);
        }
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        // Динамічна кількість товарів на сторінку залежно від висоти екрану
        // Заголовок (~160px) + нижні кнопки (32px) + хоча б 1 товар (58px) = ~250px мінімум
        int availableForList = this.height - 160 - 32;
        itemsPerPage = Math.max(1, availableForList / ROW_H);
        // ── Назва ──────────────────────────────────────────────────
        int y = 28;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.назва_c3a01b80"), b -> {}
        ).bounds(cx - 160, y, 50, 14).build()).active = false;
        nameInput = new EditBox(this.font, cx - 106, y, 220, 14, Component.translatable("wavedefense.auto.назва_382c87f9"));
        nameInput.setMaxLength(32);
        nameInput.setValue(editingPoint.getName());
        nameInput.setResponder(s -> editingPoint.setName(s.isBlank() ? I18n.get("wavedefense.shop.default_name") : s));
        this.addRenderableWidget(nameInput);

        // ── Позиція ─────────────────────────────────────────────────
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.позиція_точки_9c5a6640"), b -> {}
        ).bounds(cx - 160, y, 118, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.my_position"), b -> setCurrentPos()
        ).bounds(cx - 38, y, 100, 14).build());
        this.addRenderableWidget(Button.builder(
            Component.literal("§a✓"), b -> applyPosCoords()
        ).bounds(cx + 66, y, 22, 14).build())
        .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("wavedefense.auto.застосувати_координати_e1ba0f17")));

        y += 16;
        // startX=cx-160, labelW=14, fieldW=48, height=14, stride=68
        posCoordField = new CoordinateInputField(this.font, cx - 160, y, 14, 48, 14, 68);
        posCoordField.setValue(editingPoint.getPos());
        posCoordField.addToScreen(this::addRenderableWidget);

        // ── Радіус ─────────────────────────────────────────────────
        y += 18;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.радіус_доступу_1_64_бл_228e2d0c"), b -> {}
        ).bounds(cx - 160, y, 168, 14).build()).active = false;
        radiusInput = new EditBox(this.font, cx + 12, y, 48, 14, Component.literal("5"));
        radiusInput.setValue(String.valueOf(editingPoint.getRadius()));
        radiusInput.setMaxLength(3);
        radiusInput.setResponder(s -> { try { editingPoint.setRadius(Integer.parseInt(s.trim())); } catch (Exception ignored){} });
        this.addRenderableWidget(radiusInput);

        // Статус позиції
        y += 18;
        BlockPos cur = editingPoint.getPos();
        net.minecraft.network.chat.Component posStatus = cur != null
            ? Component.translatable("wavedefense.label.shop_point_pos_set",
                cur.getX(), cur.getY(), cur.getZ(), editingPoint.getRadius())
            : Component.translatable("wavedefense.label.shop_point_pos_unset");
        this.addRenderableWidget(Button.builder(
            posStatus, b -> {}
        ).bounds(cx - 160, y, 340, 12).build()).active = false;

        // ── Товари точки ────────────────────────────────────────────
        y += 16;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.auto.товари_цієї_точки_9270f86c"), b -> {}
        ).bounds(cx - 160, y, 180, 14).build()).active = false;
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.add_shop_item"),
            b -> minecraft.setScreen(new ShopItemEditorScreen(location, editingPoint, -1, this))
        ).bounds(cx + 24, y, 120, 14).build());

        y += 18;
        // ── Список товарів ──────────────────────────────────────────
        int listTop = y;
        listStartY = y; // зберігаємо для render()
        int listBot = this.height - 32;
        listBotY = listBot; // зберігаємо для mouseScrolled і render()
        List<ShopItem> items = editingPoint.getItems();

        // Кнопки скролу — завжди правіше списку; видимі тільки якщо є що скролити
        boolean canScroll = items.size() > itemsPerPage;
        Button btnUp = Button.builder(Component.literal("▲"),
            b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
        ).bounds(cx + 142, listTop, 18, 18).build();
        btnUp.active = canScroll && scrollOffset > 0;
        Button btnDown = Button.builder(Component.literal("▼"),
            b -> { int max = Math.max(0, items.size() - itemsPerPage);
                   if (scrollOffset < max) { scrollOffset++; rebuildWidgets(); } }
        ).bounds(cx + 142, listBot - 20, 18, 18).build();
        btnDown.active = canScroll && scrollOffset < Math.max(0, items.size() - itemsPerPage);
        if (canScroll) {
            this.addRenderableWidget(btnUp);
            this.addRenderableWidget(btnDown);
        }

        for (int i = 0; i < Math.min(itemsPerPage, items.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= items.size()) break;
            ShopItem si = items.get(idx);
            int yy = y + i * ROW_H;

            String nm = si.getItems().isEmpty() ? "§c" + I18n.get("wavedefense.gui.empty")
                : "§e" + si.getItems().get(0).getHoverName().getString();
            if (nm.length() > 30) nm = nm.substring(0, 28) + "…";
            if (si.getItems().size() > 1) nm += " §8(+" + (si.getItems().size()-1) + ")";
            this.addRenderableWidget(Button.builder(
                Component.literal(nm), b -> {}
            ).bounds(cx - 160, yy + 2, 220, 14).build()).active = false;

            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.купити_d_продати_d_05ac8a91", si.getBuyPrice(), si.getSellPrice()), b -> {}
            ).bounds(cx - 160, yy + 18, 220, 12).build()).active = false;

            final int fi = idx;
            this.addRenderableWidget(Button.builder(
                Component.literal("✎"),
                b -> minecraft.setScreen(new ShopItemEditorScreen(location, editingPoint, fi, this))
            ).bounds(cx + 68, yy, 32, 20).build());
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.delete"),
                b -> { editingPoint.removeItem(fi); scrollOffset = Math.min(scrollOffset, Math.max(0, editingPoint.getItems().size() - itemsPerPage)); rebuildWidgets(); }
            ).bounds(cx + 104, yy, 32, 20).build());

            // Іконки — рендеряться у render()
            for (int j = 0; j < Math.min(4, si.getItems().size()); j++)
                this.addRenderableWidget(Button.builder(Component.literal(""), b->{})
                    .bounds(cx - 155 + j*20, yy + 34, 18, 18).build()).active = false;
        }

        // Підказка якщо список порожній
        if (items.isEmpty()) {
            this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.auto.товарів_немає_натисніть_додати_т_1516fbe1"), b -> {}
            ).bounds(cx - 160, listTop + 4, 320, 14).build()).active = false;
        }

        // ── Нижні кнопки ────────────────────────────────────────────
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.save"), b -> save()
        ).bounds(cx - 130, this.height - 28, 120, 20).build());
        this.addRenderableWidget(Button.builder(
            Component.translatable("wavedefense.button.cancel"), b -> minecraft.setScreen(parent)
        ).bounds(cx - 5, this.height - 28, 120, 20).build());
    }

    private void setCurrentPos() {
        if (minecraft.player != null) {
            editingPoint.setPos(minecraft.player.blockPosition());
            rebuildWidgets();
        }
    }

    private void applyPosCoords() {
        if (posCoordField == null) return;
        BlockPos pos = posCoordField.getValue();
        if (pos != null) {
            editingPoint.setPos(pos);
            rebuildWidgets();
        } else if (!posCoordField.isEmpty() && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("wavedefense.auto.невірний_формат_координат_607bcb51"), true);
        }
    }

    private void save() {
        applyPosCoords(); // apply coord fields even if user didn't click "✓"
        if (editingPoint.getPos() == null) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(Component.translatable("wavedefense.auto.спочатку_вкажіть_позицію_f2c05d75"), true);
            return;
        }
        // Застосовуємо поля перед збереженням
        if (nameInput != null) editingPoint.setName(nameInput.getValue().isBlank() ? I18n.get("wavedefense.shop.default_name") : nameInput.getValue().trim());
        if (radiusInput != null) { try { editingPoint.setRadius(Integer.parseInt(radiusInput.getValue().trim())); } catch (Exception ignored){} }
        // Якщо нова точка — додаємо до локації
        if (pointIndex < 0) location.addShopPoint(editingPoint);
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { minecraft.setScreen(parent); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, editingPoint.getItems().size() - itemsPerPage);
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; rebuildWidgets(); }
        else if (delta < 0 && scrollOffset < max) { scrollOffset++; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFFF);

        // clipTop = де починається список товарів (динамічно, не хардкодований)
        int clipTop = listStartY - 2;
        int clipBot = this.height - 32;

        ScissorHelper.enable(0, clipTop, this.width, Math.max(1, clipBot - clipTop));
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() + w.getHeight() > clipTop && w.getY() < clipBot)
                    w.render(g, mouseX, mouseY, partial);
            }
        }

        // Іконки товарів — позиціонуються так само як placeholder-кнопки в init()
        List<ShopItem> items = editingPoint.getItems();
        for (int i = 0; i < Math.min(itemsPerPage, items.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= items.size()) break;
            List<ItemStack> stacks = items.get(idx).getItems();
            int yy = listStartY + i * ROW_H;
            int iy = yy + 34; // збігається з placeholder-кнопками в init()
            for (int j = 0; j < Math.min(4, stacks.size()); j++) {
                ItemStack st = stacks.get(j);
                int ix = cx - 155 + j * 20; // лівіше щоб не накладатись
                g.fill(ix-1,iy-1,ix+17,iy+17,0xFF444444);
                g.fill(ix,iy,ix+16,iy+16,0xFF222222);
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(this.font, st, ix, iy);
                if (mouseX>=ix&&mouseX<ix+16&&mouseY>=iy&&mouseY<iy+16) {
                    ScissorHelper.disable();
                    g.renderTooltip(this.font, st, mouseX, mouseY);
                    ScissorHelper.enable(0,clipTop,this.width,Math.max(1,clipBot-clipTop));
                }
            }
        }

        ScissorHelper.disable();

        // Static header + footer
        for (var r : this.renderables) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                if (w.getY() < clipTop || w.getY() >= clipBot)
                    w.render(g, mouseX, mouseY, partial);
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
