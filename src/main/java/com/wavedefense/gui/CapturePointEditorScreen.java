package com.wavedefense.gui;

import com.wavedefense.data.CapturePoint;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.network.packets.UpdateLocationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Editor for the capture points of a CtP / KotH location.
 *
 * Two modes:
 *  List  — shows all configured points with ✎ Edit and ✕ Delete buttons.
 *  Edit  — form to create/modify a single CapturePoint.
 */
public class CapturePointEditorScreen extends Screen {

    private static final int PER_PAGE = 5;

    private final Location location;
    private final Screen   parent;

    // List mode state
    private int  scrollOffset       = 0;
    private int  pendingDeleteIndex = -1;

    // Edit mode state
    private boolean editingPoint    = false;
    private int     editingIndex    = -1;  // -1 = new point

    private EditBox nameInput;
    private CoordinateInputField coordField;
    private EditBox radiusInput;
    private EditBox captureTimeInput;
    private EditBox particleCountInput;
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
        super(Component.translatable("wavedefense.capture_point.editor.title"));
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
            this.addRenderableWidget(Button.builder(
                    Component.translatable("wavedefense.button.save_back"), b -> saveAndClose()
            ).bounds(cx - 55, this.height - 28, 110, 20).build());
        }
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.back"), b -> {
                    if (editingPoint) { editingPoint = false; rebuildWidgets(); }
                    else              { this.minecraft.setScreen(parent); }
                }
        ).bounds(cx + 60, this.height - 28, 60, 20).build());
    }

    // ── List mode ─────────────────────────────────────────────────────────
    private void initListMode(int cx, int y) {
        List<CapturePoint> points = location.getCapturePoints();

        // Add button
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.add"),
                b -> { editingPoint = true; editingIndex = -1; selectedParticle = "minecraft:smoke"; rebuildWidgets(); }
        ).bounds(cx - 155, y, 310, 20).build());
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
            this.addRenderableWidget(Button.builder(
                    Component.literal(info), b -> {}
            ).bounds(cx - 155, rowY, 280, 20).build()).active = false;

            // Edit button
            final int fIdx = idx;
            this.addRenderableWidget(Button.builder(
                    Component.literal("✎"),
                    b -> { pendingDeleteIndex = -1; startEditPoint(fIdx); }
            ).bounds(cx + 130, rowY, 22, 20).build());

            // Delete button (with confirm)
            boolean isPending = (pendingDeleteIndex == fIdx);
            int delW = isPending ? 35 : 22;
            int delX = isPending ? cx + 142 : cx + 155;
            this.addRenderableWidget(Button.builder(
                    isPending
                        ? Component.translatable("wavedefense.button.confirm_delete")
                        : Component.literal("§c✕"),
                    b -> {
                        if (isPending) {
                            pendingDeleteIndex = -1;
                            location.removeCapturePoint(fIdx);
                            scrollOffset = Math.max(0, Math.min(scrollOffset,
                                Math.max(0, location.getCapturePoints().size() - PER_PAGE)));
                            rebuildWidgets();
                        } else {
                            pendingDeleteIndex = fIdx;
                            rebuildWidgets();
                        }
                    }
            ).bounds(delX, rowY, delW, 20).build());
        }

        // Scroll buttons
        if (points.size() > PER_PAGE) {
            this.addRenderableWidget(Button.builder(Component.literal("▲"),
                    b -> { if (scrollOffset > 0) { scrollOffset--; rebuildWidgets(); } }
            ).bounds(cx + 182, y, 18, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("▼"),
                    b -> { if (scrollOffset + PER_PAGE < points.size()) { scrollOffset++; rebuildWidgets(); } }
            ).bounds(cx + 182, y + (PER_PAGE - 1) * rowH, 18, 18).build());
        }
    }

    // ── Edit form ─────────────────────────────────────────────────────────
    private void initEditForm(int cx, int y) {
        CapturePoint cp = (editingIndex >= 0 && editingIndex < location.getCapturePoints().size())
                ? location.getCapturePoints().get(editingIndex) : null;

        // Name
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.name"), b -> {}
        ).bounds(cx - 155, y, 100, 14).build()).active = false;
        nameInput = new EditBox(this.font, cx - 155, y + 15, 200, 18,
                Component.translatable("wavedefense.capture_point.name"));
        nameInput.setValue(cp != null ? cp.getName() : "");
        nameInput.setMaxLength(20);
        this.addRenderableWidget(nameInput);
        y += 40;

        // Position
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.pos_hint"), b -> {}
        ).bounds(cx - 155, y, 280, 14).build()).active = false;
        y += 16;
        coordField = new CoordinateInputField(this.font, cx - 155, y, 14, 52, 16, 73);
        coordField.setValue(cp != null ? cp.getPos() : null);
        coordField.addToScreen(this::addRenderableWidget);
        this.addRenderableWidget(Button.builder(Component.literal("📌"),
                b -> coordField.setFromPlayer(minecraft.player)
        ).bounds(coordField.getEndX() + 7, y, 30, 16).build());
        y += 26;

        // Radius
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.radius"), b -> {}
        ).bounds(cx - 155, y, 130, 14).build()).active = false;
        radiusInput = new EditBox(this.font, cx, y, 50, 14, Component.literal("5"));
        radiusInput.setValue(String.valueOf(cp != null ? cp.getCaptureRadius() : 5));
        radiusInput.setMaxLength(3);
        this.addRenderableWidget(radiusInput);

        // Capture time
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.capture_time"), b -> {}
        ).bounds(cx + 60, y, 95, 14).build()).active = false;
        captureTimeInput = new EditBox(this.font, cx + 158, y, 50, 14, Component.literal("10"));
        captureTimeInput.setValue(String.valueOf(cp != null ? cp.getCaptureTimeSec() : 10));
        captureTimeInput.setMaxLength(3);
        this.addRenderableWidget(captureTimeInput);
        y += 24;

        // Particle selector
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.particle"), b -> {}
        ).bounds(cx - 155, y, 100, 14).build()).active = false;
        if (cp != null) selectedParticle = cp.getParticleType();
        y += 16;
        int pBtnW = 60, pGap = 3;
        int pStartX = cx - 155;
        for (int i = 0; i < PARTICLE_IDS.length; i++) {
            final String pid = PARTICLE_IDS[i];
            boolean sel = pid.equals(selectedParticle);
            int row = i / 4, col = i % 4;
            this.addRenderableWidget(Button.builder(
                    Component.literal(sel ? "§a§l" + PARTICLE_LABELS[i] : "§7" + PARTICLE_LABELS[i]),
                    b -> { selectedParticle = pid; rebuildWidgets(); }
            ).bounds(pStartX + col * (pBtnW + pGap), y + row * 20, pBtnW, 18).build());
        }
        // M-9: PARTICLE_IDS.length/4 rows needed (8 items → 2 rows, not 3)
        y += (PARTICLE_IDS.length / 4) * 20 + 4;

        // Particle count
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.capture_point.count"), b -> {}
        ).bounds(cx - 155, y, 80, 14).build()).active = false;
        particleCountInput = new EditBox(this.font, cx - 70, y, 40, 14, Component.literal("4"));
        particleCountInput.setValue(String.valueOf(cp != null ? cp.getParticleCount() : 4));
        particleCountInput.setMaxLength(2);
        this.addRenderableWidget(particleCountInput);
        y += 24;

        // Save / Cancel
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.save"),
                b -> savePoint()
        ).bounds(cx - 110, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.translatable("wavedefense.button.cancel"),
                b -> { editingPoint = false; rebuildWidgets(); }
        ).bounds(cx + 10, y, 100, 20).build());
    }

    // ── Actions ───────────────────────────────────────────────────────────
    private void startEditPoint(int idx) {
        editingPoint  = true;
        editingIndex  = idx;
        CapturePoint cp = location.getCapturePoints().get(idx);
        selectedParticle = cp.getParticleType();
        rebuildWidgets();
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
        rebuildWidgets();
    }

    private void saveAndClose() {
        PacketHandler.sendToServer(new UpdateLocationPacket(location));
        if (minecraft.player != null)
            minecraft.player.displayClientMessage(
                Component.translatable("wavedefense.capture_point.saved"), true);
        this.minecraft.setScreen(parent);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiTheme.renderBackground(g, this.width, this.height);
        GuiTheme.renderHeader(g, this.font, this.title, this.width);
        int cx = this.width / 2;
        GuiTheme.renderContentFrame(g, 8, 40, this.width - 8, this.height - 32);
        super.render(g, mouseX, mouseY, partialTick);

        // Point count summary
        if (!editingPoint) {
            int pts = location.getCapturePoints().size();
            g.drawCenteredString(this.font, "§7" + pts + " point(s) configured",
                    cx, this.height - 44, GuiTheme.TEXT_MUTED);
        }

        // L-1: show save error if present
        if (saveError != null && editingPoint) {
            g.drawCenteredString(this.font, "§c" + saveError, cx, this.height - 44, 0xFFFF5555);
        }
    }

}
