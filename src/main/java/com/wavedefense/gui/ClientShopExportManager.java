package com.wavedefense.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Клієнтський кеш списку файлів магазину для import UI. */
@OnlyIn(Dist.CLIENT)
public class ClientShopExportManager {
    private static final List<String> files = new ArrayList<>();
    private static Runnable onUpdate;

    public static void setFiles(List<String> names) {
        files.clear();
        files.addAll(names);
        if (onUpdate != null) onUpdate.run();
    }

    public static List<String> getFiles() { return Collections.unmodifiableList(files); }
    public static void setOnUpdate(Runnable r) { onUpdate = r; }
}
