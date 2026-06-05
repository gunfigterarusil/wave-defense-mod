package com.wavedefense.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientWaveExportManager {
    private static final List<String> files = new ArrayList<>();
    private static Runnable onUpdate = null;

    public static void update(List<String> newFiles) {
        files.clear();
        files.addAll(newFiles);
        if (onUpdate != null) onUpdate.run();
    }

    public static List<String> getFiles() { return Collections.unmodifiableList(files); }
    public static void setOnUpdate(Runnable r) { onUpdate = r; }
    public static void clearOnUpdate() { onUpdate = null; }
}
