package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.Location;
import com.wavedefense.data.ShopItem;
import com.wavedefense.data.ShopPoint;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;

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

    private TextFieldWidget nameInput;
    private TextFieldWidget radiusInput;
    private CoordinateInputField posCoordField;

    private int scrollOffset = 0;
    private static final int ROW_H = 58;
    private int itemsPerPage = 3; // обчислюється динамічно в init()
    private int listStartY = 148; // оновлюється в init()
    private int listBotY   = 0;   // оновлюється в init()

    public ShopPointEditorScreen(Location location, int pointIndex, Screen parent) {
        super(pointIndex >= 0 ? new TranslationTextComponent("wavedefense.title.edit_shop_point") : new TranslationTextComponent("wavedefense.title.new_shop_point"));
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
        this.addButton(new Button(cx - 160, y, 50, 14, new TranslationTextComponent("wavedefense.auto.назва_c3a01b80"), b -> {})).active = false;
        nameInput = new TextFieldWidget(this.font, cx - 106, y, 220, 14, new TranslationTextComponent("wavedefense.auto.назва_382c87f9"));
        nameInput.setMaxLength(32);
        nameInput.setValue(editingPoint.getName());
        nameInput.setResponder(s -> editingPoint.setName(s.trim().isEmpty() ? I18n.get("wavedefense.shop.default_name") : s));
        this.addButton(nameInput);

        // ── Позиція ─────────────────────────────────────────────────
        y += 18;
        this.addButton(new Button(cx - 160, y, 118, 14, new TranslationTextComponent("wavedefense.auto.позиція_точки_9c5a6640"), b -> {})).active = false;
        this.addButton(new Button(cx - 38, y, 100, 14, new TranslationTextComponent("wavedefense.button.my_position"), b -> setCurrentPos()));
        this.addButton(new Button(cx + 66, y, 22, 14, new StringTextComponent("§a✓"), b -> applyPosCoords()))
        /* setTooltip omitted on 1.16.5 */;

        y += 16;
        // startX=cx-160, labelW=14, fieldW=48, height=14, stride=68
        posCoordField = new CoordinateInputField(this.font, cx - 160, y, 14, 48, 14, 68);
        posCoordField.setValue(editingPoint.getPos());
        posCoordField.addToScreen(this::addButton);

        // ── Радіус ─────────────────────────────────────────────────
        y += 18;
        this.addButton(new Button(cx - 160, y, 168, 14, new TranslationTextComponent("wavedefense.auto.радіус_доступу_1_64_бл_228e2d0c"), b -> {})).active = false;
        radiusInput = new TextFieldWidget(this.font, cx + 12, y, 48, 14, new StringTextComponent("5"));
        radiusInput.setValue(String.valueOf(editingPoint.getRadius()));
        radiusInput.setMaxLength(3);
        radiusInput.setResponder(s -> { try { editingPoint.setRadius(Integer.parseInt(s.trim())); } catch (Exception ignored){} });
        this.addButton(radiusInput);

        // Статус позиції
        y += 18;
        BlockPos cur = editingPoint.getPos();
        net.minecraft.util.text.ITextComponent posStatus = cur != null
            ? new TranslationTextComponent("wavedefense.label.shop_point_pos_set",
                cur.getX(), cur.getY(), cur.getZ(), editingPoint.getRadius())
            : new TranslationTextComponent("wavedefense.label.shop_point_pos_unset");
        this.addButton(new Button(cx - 160, y, 340, 12, posStatus, b -> {})).active = false;

        // ── Товари точки ────────────────────────────────────────────
        y += 16;
        this.addButton(new Button(cx - 160, y, 180, 14, new TranslationTextComponent("wavedefense.auto.товари_цієї_точки_9270f86c"), b -> {})).active = false;
        this.addButton(new Button(cx + 24, y, 120, 14, new TranslationTextComponent("wavedefense.button.add_shop_item"), b -> minecraft.setScreen(new ShopItemEditorScreen(location, editingPoint, -1, this))));

        y += 18;
        // ── Список товарів ──────────────────────────────────────────
        int listTop = y;
        listStartY = y; // зберігаємо для render()
        int listBot = this.height - 32;
        listBotY = listBot; // зберігаємо для mouseScrolled і render()
        List<ShopItem> items = editingPoint.getItems();

        // Кнопки скролу — завжди правіше списку; видимі тільки якщо є що скролити
        boolean canScroll = items.size() > itemsPerPage;
        Button btnUp = new Button(cx + 142, listTop, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; init(); } });
        btnUp.active = canScroll && scrollOffset > 0;
        Button btnDown = new Button(cx + 142, listBot - 20, 18, 18, new StringTextComponent("▼"), b -> { int max = Math.max(0, items.size() - itemsPerPage);
                   if (scrollOffset < max) { scrollOffset++; init(); } });
        btnDown.active = canScroll && scrollOffset < Math.max(0, items.size() - itemsPerPage);
        if (canScroll) {
            this.addButton(btnUp);
            this.addButton(btnDown);
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
            this.addButton(new Button(cx - 160, yy + 2, 220, 14, new StringTextComponent(nm), b -> {})).active = false;

            this.addButton(new Button(cx - 160, yy + 18, 220, 12, new TranslationTextComponent("wavedefense.auto.купити_d_продати_d_05ac8a91", si.getBuyPrice(), si.getSellPrice()), b -> {})).active = false;

            final int fi = idx;
            this.addButton(new Button(cx + 68, yy, 32, 20, new StringTextComponent("✎"), b -> minecraft.setScreen(new ShopItemEditorScreen(location, editingPoint, fi, this))));
            this.addButton(new Button(cx + 104, yy, 32, 20, new TranslationTextComponent("wavedefense.button.delete"), b -> { editingPoint.removeItem(fi); scrollOffset = Math.min(scrollOffset, Math.max(0, editingPoint.getItems().size() - itemsPerPage)); init(); }));

            // Іконки — рендеряться у render()
            for (int j = 0; j < Math.min(4, si.getItems().size()); j++)
                this.addButton(new Button(cx - 155 + j*20, yy + 34, 18, 18, new StringTextComponent(""), b->{})).active = false;
        }

        // Підказка якщо список порожній
        if (items.isEmpty()) {
            this.addButton(new Button(cx - 160, listTop + 4, 320, 14, new TranslationTextComponent("wavedefense.auto.товарів_немає_натисніть_додати_т_1516fbe1"), b -> {})).active = false;
        }

        // ── Нижні кнопки ────────────────────────────────────────────
        this.addButton(new Button(cx - 130, this.height - 28, 120, 20, new TranslationTextComponent("wavedefense.button.save"), b -> save()));
        this.addButton(new Button(cx - 5, this.height - 28, 120, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> minecraft.setScreen(parent)));
    }

    private void setCurrentPos() {
        if (minecraft.player != null) {
            editingPoint.setPos(minecraft.player.blockPosition());
            init();
        }
    }

    private void applyPosCoords() {
        if (posCoordField == null) return;
        BlockPos pos = posCoordField.getValue();
        if (pos != null) {
            editingPoint.setPos(pos);
            init();
        } else if (!posCoordField.isEmpty() && minecraft.player != null) {
            minecraft.player.displayClientMessage(new TranslationTextComponent("wavedefense.auto.невірний_формат_координат_607bcb51"), true);
        }
    }

    private void save() {
        applyPosCoords(); // apply coord fields even if user didn't click "✓"
        if (editingPoint.getPos() == null) {
            if (minecraft.player != null)
                minecraft.player.displayClientMessage(new TranslationTextComponent("wavedefense.auto.спочатку_вкажіть_позицію_f2c05d75"), true);
            return;
        }
        // Застосовуємо поля перед збереженням
        if (nameInput != null) editingPoint.setName(nameInput.getValue().trim().isEmpty() ? I18n.get("wavedefense.shop.default_name") : nameInput.getValue().trim());
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
        if (delta > 0 && scrollOffset > 0) { scrollOffset--; init(); }
        else if (delta < 0 && scrollOffset < max) { scrollOffset++; init(); }
        return true;
    }

    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partial) {
        GuiTheme.renderBackground(g, this.width, this.height);
        int cx = this.width / 2;
        GuiTheme.renderHeader(g, this.font, this.title, this.width);

        // clipTop = де починається список товарів (динамічно, не хардкодований)
        int clipTop = listStartY - 2;
        int clipBot = this.height - 32;

        GuiTheme.renderContentFrame(g, 8, clipTop - 4, this.width - 8, clipBot + 4);
        ScissorHelper.enable(0, clipTop, this.width, Math.max(1, clipBot - clipTop));
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) { net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y + w.getHeight() > clipTop && w.y < clipBot)
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
                com.wavedefense.gui.GuiCompat.fill(g, ix-1,iy-1,ix+17,iy+17,GuiTheme.BORDER);
                com.wavedefense.gui.GuiCompat.fill(g, ix,iy,ix+16,iy+16,GuiTheme.PANEL_DARK);
                com.wavedefense.gui.GuiCompat.renderItem(g, st, ix, iy);
                com.wavedefense.gui.GuiCompat.renderItemDecorations(g, this.font, st, ix, iy);
                if (mouseX>=ix&&mouseX<ix+16&&mouseY>=iy&&mouseY<iy+16) {
                    com.wavedefense.gui.GuiCompat.flush(g); // flush renderItemDecorations text before disabling scissor
                    ScissorHelper.disable();
                    com.wavedefense.gui.GuiCompat.renderTooltip(this, g, this.font, st, mouseX, mouseY);
                    ScissorHelper.enable(0,clipTop,this.width,Math.max(1,clipBot-clipTop));
                }
            }
        }

        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Pass 2: статичний header (назва, позиція, радіус, кнопки "Додати")
        ScissorHelper.enable(0, 0, this.width, clipTop);
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y < clipTop) w.render(g, mouseX, mouseY, partial);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();

        // Pass 3: статичний footer (Save / Cancel)
        ScissorHelper.enable(0, clipBot, this.width, this.height - clipBot);
        for (Object r : this.buttons) {
            if (r instanceof net.minecraft.client.gui.widget.Widget) {
                net.minecraft.client.gui.widget.Widget w = (net.minecraft.client.gui.widget.Widget) r;
                if (w.y >= clipBot) w.render(g, mouseX, mouseY, partial);
            }
        }
        com.wavedefense.gui.GuiCompat.flush(g);
        ScissorHelper.disable();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
