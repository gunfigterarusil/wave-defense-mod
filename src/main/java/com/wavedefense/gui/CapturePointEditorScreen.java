package com.wavedefense.gui;

import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import com.wavedefense.data.CapturePoint;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import java.util.List;

/**
 * Editor for the capture points of a CtP / KotH location.
 *
 * Two modes:
 *  List  — shows all configured points with ✎ Edit and ✕ Delete buttons.
 *  Edit  — form to create/modify a single CapturePoint.
 */
public class CapturePointEditorScreen extends Screen {
    /** 1.16.5 shim for 1.20.1's Screen.rebuildWidgets(): clear widgets + re-init. */
    protected void rebuild() {
        this.buttons.clear();
        this.children.clear();
        this.setFocused(null);
        this.init();
    }


    private static final int PER_PAGE = 5;

    private final Location location;
    private final Screen   parent;

    // List mode state
    private int  scrollOffset       = 0;
    private int  pendingDeleteIndex = -1;

    // Edit mode state
    private boolean editingPoint    = false;
    private int     editingIndex    = -1;  // -1 = new point

    private TextFieldWidget nameInput;
    private CoordinateInputField coordField;
    private TextFieldWidget radiusInput;
    private TextFieldWidget captureTimeInput;
    private TextFieldWidget particleCountInput;
    private String  selectedParticle = "minecraft:smoke";

    // L-1: error message shown when trying to save with empty name
    private String saveError = null;

    // Particle presets
    private static final String[] PARTICLE_IDS = {
        "minecraft:smoke", "minecraft:flame", "minecraft:end_rod",
        "minecraft:witch", "minecraft:portal", "minecraft:snowflake",
        "minecraft:crit",  "minecraft:enchant"
    };
    private static final String[] PARTICLE_LABELS = {
        "smoke", "flame", "end_rod", "witch", "portal", "snow", "crit", "enchant"
    };

    public CapturePointEditorScreen(Location location, Screen parent) {
        super(new TranslationTextComponent("wavedefense.capture_point.editor.title"));
        this.location = location;
        this.parent   = parent;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y  = 42;

        // Title accent bar
        if (editingPoint) {
            initEditForm(cx, y);
        } else {
            initListMode(cx, y);
        }

        // ── Footer ────────────────────────────────────────────────────────
        if (!editingPoint) {
            this.addButton(new Button(cx - 55, this.height - 28, 110, 20, new TranslationTextComponent("wavedefense.button.save_back"), b -> saveAndClose()));
        }
        this.addButton(new Button(cx + 60, this.height - 28, 60, 20, new TranslationTextComponent("wavedefense.button.back"), b -> {
                    if (editingPoint) { editingPoint = false; init(); }
                    else              { this.minecraft.setScreen(parent); }
                }));
    }

    // ── List mode ─────────────────────────────────────────────────────────
    private void initListMode(int cx, int y) {
        List<CapturePoint> points = location.getCapturePoints();

        // Add button
        this.addButton(new Button(cx - 155, y, 310, 20, new TranslationTextComponent("wavedefense.capture_point.add"), b -> { editingPoint = true; editingIndex = -1; selectedParticle = "minecraft:smoke"; rebuild(); }));
        y += 26;

        int rowH = 30;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = i + scrollOffset;
            if (idx >= points.size()) break;
            CapturePoint cp = points.get(idx);
            int rowY = y + i * rowH;

            // Info label
            BlockPos pos = cp.getPos();
            String posStr = pos != null ? pos.getX() + " " + pos.getY() + " " + pos.getZ() : "?";
            String info = "§e" + cp.getName() + "§7  " + posStr
                    + "  r=" + cp.getCaptureRadius()
                    + "  t=" + cp.getCaptureTimeSec() + "s";
            this.addButton(new Button(cx - 155, rowY, 280, 20, new StringTextComponent(info), b -> {})).active = false;

            // Edit button
            final int fIdx = idx;
            this.addButton(new Button(cx + 130, rowY, 22, 20, new StringTextComponent("✎"), b -> { pendingDeleteIndex = -1; startEditPoint(fIdx); }));

            // Delete button (with confirm)
            boolean isPending = (pendingDeleteIndex == fIdx);
            int delW = isPending ? 35 : 22;
            int delX = isPending ? cx + 142 : cx + 155;
            this.addButton(new Button(delX, rowY, delW, 20, isPending
                        ? new TranslationTextComponent("wavedefense.button.confirm_delete")
                        : new StringTextComponent("§c✕"), b -> {
                        if (isPending) {
                            pendingDeleteIndex = -1;
                            location.removeCapturePoint(fIdx);
                            scrollOffset = Math.max(0, Math.min(scrollOffset,
                                Math.max(0, location.getCapturePoints().size() - PER_PAGE)));
                            rebuild();
                        } else {
                            pendingDeleteIndex = fIdx;
                            rebuild();
                        }
                    }));
        }

        // Scroll buttons
        if (points.size() > PER_PAGE) {
            this.addButton(new Button(cx + 182, y, 18, 18, new StringTextComponent("▲"), b -> { if (scrollOffset > 0) { scrollOffset--; rebuild(); } }));
            this.addButton(new Button(cx + 182, y + (PER_PAGE - 1) * rowH, 18, 18, new StringTextComponent("▼"), b -> { if (scrollOffset + PER_PAGE < points.size()) { scrollOffset++; rebuild(); } }));
        }
    }

    // ── Edit form ─────────────────────────────────────────────────────────
    private void initEditForm(int cx, int y) {
        CapturePoint cp = (editingIndex >= 0 && editingIndex < location.getCapturePoints().size())
                ? location.getCapturePoints().get(editingIndex) : null;

        // Name
        this.addButton(new Button(cx - 155, y, 100, 14, new TranslationTextComponent("wavedefense.capture_point.name"), b -> {})).active = false;
        nameInput = new TextFieldWidget(this.font, cx - 155, y + 15, 200, 18,
                new TranslationTextComponent("wavedefense.capture_point.name"));
        nameInput.setValue(cp != null ? cp.getName() : "");
        nameInput.setMaxLength(20);
        this.addButton(nameInput);
        y += 40;

        // Position
        this.addButton(new Button(cx - 155, y, 280, 14, new TranslationTextComponent("wavedefense.capture_point.pos_hint"), b -> {})).active = false;
        y += 16;
        coordField = new CoordinateInputField(this.font, cx - 155, y, 14, 52, 16, 73);
        coordField.setValue(cp != null ? cp.getPos() : null);
        coordField.addToScreen(this::addButton);
        this.addButton(new Button(coordField.getEndX() + 7, y, 30, 16, new StringTextComponent("📌"), b -> coordField.setFromPlayer(minecraft.player)));
        y += 26;

        // Radius
        this.addButton(new Button(cx - 155, y, 130, 14, new TranslationTextComponent("wavedefense.capture_point.radius"), b -> {})).active = false;
        radiusInput = new TextFieldWidget(this.font, cx, y, 50, 14, new StringTextComponent("5"));
        radiusInput.setValue(String.valueOf(cp != null ? cp.getCaptureRadius() : 5));
        radiusInput.setMaxLength(3);
        this.addButton(radiusInput);

        // Capture time
        this.addButton(new Button(cx + 60, y, 95, 14, new TranslationTextComponent("wavedefense.capture_point.capture_time"), b -> {})).active = false;
        captureTimeInput = new TextFieldWidget(this.font, cx + 158, y, 50, 14, new StringTextComponent("10"));
        captureTimeInput.setValue(String.valueOf(cp != null ? cp.getCaptureTimeSec() : 10));
        captureTimeInput.setMaxLength(3);
        this.addButton(captureTimeInput);
        y += 24;

        // Particle selector
        this.addButton(new Button(cx - 155, y, 100, 14, new TranslationTextComponent("wavedefense.capture_point.particle"), b -> {})).active = false;
        if (cp != null) selectedParticle = cp.getParticleType();
        y += 16;
        int pBtnW = 60, pGap = 3;
        int pStartX = cx - 155;
        for (int i = 0; i < PARTICLE_IDS.length; i++) {
            final String pid = PARTICLE_IDS[i];
            boolean sel = pid.equals(selectedParticle);
            int row = i / 4, col = i % 4;
            this.addButton(new Button(pStartX + col * (pBtnW + pGap), y + row * 20, pBtnW, 18, new StringTextComponent(sel ? "§a§l" + PARTICLE_LABELS[i] : "§7" + PARTICLE_LABELS[i]), b -> { selectedParticle = pid; rebuild(); }));
        }
        // M-9: PARTICLE_IDS.length/4 rows needed (8 items → 2 rows, not 3)
        y += (PARTICLE_IDS.length / 4) * 20 + 4;

        // Particle count
        this.addButton(new Button(cx - 155, y, 80, 14, new TranslationTextComponent("wavedefense.capture_point.count"), b -> {})).active = false;
        particleCountInput = new TextFieldWidget(this.font, cx - 70, y, 40, 14, new StringTextComponent("4"));
        particleCountInput.setValue(String.valueOf(cp != null ? cp.getParticleCount() : 4));
        particleCountInput.setMaxLength(2);
        this.addButton(particleCountInput);
        y += 24;

        // Save / Cancel
        this.addButton(new Button(cx - 110, y, 100, 20, new TranslationTextComponent("wavedefense.button.save"), b -> savePoint()));
        this.addButton(new Button(cx + 10, y, 100, 20, new TranslationTextComponent("wavedefense.button.cancel"), b -> { editingPoint = false; rebuild(); }));
    }

    // ── Actions ───────────────────────────────────────────────────────────
    private void startEditPoint(int idx) {
        editingPoint  = true;
        editingIndex  = idx;
        CapturePoint cp = location.getCapturePoints().get(idx);
        selectedParticle = cp.getParticleType();
        rebuild();
    }

    private void savePoint() {
        String name = nameInput != null ? nameInput.getValue().trim() : "";
        if (name.isEmpty()) {
            // L-1: show inline error instead of silently ignoring
            saveError = I18n.get("wavedefense.capture_point.error_name_empty");
            return;
        }
        saveError = null;

        BlockPos pos = null;
        if (coordField != null) {
            pos = coordField.getValue();
        }
        if (pos == null && minecraft.player != null) {
            pos = minecraft.player.blockPosition();
        }
        if (pos == null) return;

        int radius = 5;
        try { if (radiusInput != null) radius = Math.max(1, Math.min(30, Integer.parseInt(radiusInput.getValue().trim()))); }
        catch (NumberFormatException ignored) {}

        int capTime = 10;
        try { if (captureTimeInput != null) capTime = Math.max(1, Math.min(120, Integer.parseInt(captureTimeInput.getValue().trim()))); }
        catch (NumberFormatException ignored) {}

        int count = 4;
        try { if (particleCountInput != null) count = Math.max(1, Math.min(32, Integer.parseInt(particleCountInput.getValue().trim()))); }
        catch (NumberFormatException ignored) {}

        if (editingIndex >= 0 && editingIndex < location.getCapturePoints().size()) {
            CapturePoint cp = location.getCapturePoints().get(editingIndex);
            cp.setName(name);
            cp.setPos(pos);
            cp.setCaptureRadius(radius);
            cp.setCaptureTimeSec(capTime);
            cp.setParticleType(selectedParticle);
            cp.setParticleCount(count);
        } else {
            CapturePoint cp = new CapturePoint(name, pos);
            cp.setCaptureRadius(radius);
            cp.setCaptureTimeSec(capTime);
            cp.setParticleType(selectedParticle);
            cp.setParticleCount(count);
            location.addCapturePoint(cp);
        }

        editingPoint = false;
        rebuild();
    }

    private void saveAndClose() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                new TranslationTextComponent("wavedefense.capture_point.saved"), true);
        this.minecraft.setScreen(parent);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        int cx = this.width / 2;
        GuiTheme.renderContentFrame(g, 8, 40, this.width - 8, this.height - 32);
        super.render(g, mouseX, mouseY, partialTick);

        // Point count summary
        if (!editingPoint) {
            int pts = location.getCapturePoints().size();
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§7" + pts + " point(s) configured",
                    cx, this.height - 44, GuiTheme.TEXT_MUTED);
        }

        // L-1: show save error if present
        if (saveError != null && editingPoint) {
            com.wavedefense.gui.GuiCompat.drawCenteredString(g, this.font, "§c" + saveError, cx, this.height - 44, 0xFFFF5555);
        }
    }

}
