package com.wavedefense.network.packets;

import net.minecraft.util.text.ITextComponent;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.data.WaveConfig;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * C→S: Імпортує хвилі з файлу .nbt у локацію.
 * insertMode = "replace_all"   → замінити всі хвилі
 * insertMode = "replace:N"     → замінити хвилю N (0-based)
 * insertMode = "insert_after:N"→ вставити після хвилі N
 * insertMode = "append"        → додати в кінець
 */
public class ImportWavePacket {
    private final String fileName;      // без .nbt
    private final String locationName;
    private final String insertMode;

    public ImportWavePacket(String fileName, String locationName, String insertMode) {
        this.fileName = fileName;
        this.locationName = locationName;
        this.insertMode = insertMode;
    }

    public static void encode(ImportWavePacket p, PacketBuffer buf) {
        buf.writeUtf(p.fileName);
        buf.writeUtf(p.locationName);
        buf.writeUtf(p.insertMode);
    }

    public static ImportWavePacket decode(PacketBuffer buf) {
        return new ImportWavePacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(ImportWavePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            Location loc = WaveDefenceMod.locationManager.getLocation(p.locationName);
            if (loc == null) {
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.error.location_not_found", p.locationName), false);
                return;
            }

            File dir = new File(WaveDefenceMod.getServer()
                .getWorldPath(net.minecraft.world.storage.FolderName.ROOT).toFile(),
                "wavedefense/wave_export");
            File file = new File(dir, p.fileName + ".nbt");
            if (!file.exists()) {
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.файл_не_знайдено_value_e8d8c6cc", p.fileName + ".nbt"), false);
                return;
            }

            try {
                CompoundNBT root = CompressedStreamTools.readCompressed(file);
                List<WaveConfig> imported = new ArrayList<>();

                if (root.contains("waves")) {
                    // Файл з кількома хвилями
                    ListNBT list = root.getList("waves", 10);
                    for (int i = 0; i < list.size(); i++) {
                        imported.add(WaveConfig.load(list.getCompound(i)));
                    }
                } else if (root.contains("wave")) {
                    // Файл з однією хвилею
                    imported.add(WaveConfig.load(root.getCompound("wave")));
                } else {
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.неправильний_формат_файлу_69c73738"), false);
                    return;
                }

                if (imported.isEmpty()) {
                    player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent("wavedefense.auto.файл_не_містить_хвиль_ed0312f7"), false);
                    return;
                }

                List<WaveConfig> waves = loc.getWaves();

                if ("replace_all".equals(p.insertMode)) {
                    waves.clear();
                    for (int i = 0; i < imported.size(); i++) {
                        WaveConfig wc = imported.get(i);
                        wc = rebuildWithNumber(wc, i + 1);
                        waves.add(wc);
                    }
                } else if (p.insertMode.startsWith("replace:")) {
                    int idx = Integer.parseInt(p.insertMode.substring(8));
                    if (idx < 0 || idx >= waves.size()) {
                        // Вставляємо в кінець якщо індекс поза межами
                        appendAll(waves, imported);
                    } else {
                        waves.remove(idx);
                        for (int i = 0; i < imported.size(); i++) {
                            waves.add(idx + i, rebuildWithNumber(imported.get(i), idx + i + 1));
                        }
                        renumberAll(waves);
                    }
                } else if (p.insertMode.startsWith("insert_after:")) {
                    int idx = Integer.parseInt(p.insertMode.substring(13));
                    int insertAt = Math.min(idx + 1, waves.size());
                    for (int i = 0; i < imported.size(); i++) {
                        waves.add(insertAt + i, imported.get(i));
                    }
                    renumberAll(waves);
                } else { // append
                    appendAll(waves, imported);
                    renumberAll(waves);
                }

                WaveDefenceMod.locationManager.saveToFile();
                WaveDefenceMod.waveManager.broadcastLocationData();

                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                    "wavedefense.msg.wave_import_success",
                    imported.size(), p.fileName, p.locationName, waves.size()), false);

            } catch (Exception e) {
                WaveDefenceMod.LOGGER.error("ImportWavePacket error", e);
                player.displayClientMessage(new net.minecraft.util.text.TranslationTextComponent(
                    "wavedefense.msg.import_error", e.getMessage()), false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void appendAll(List<WaveConfig> waves, List<WaveConfig> toAdd) {
        int base = waves.size();
        for (int i = 0; i < toAdd.size(); i++) {
            waves.add(rebuildWithNumber(toAdd.get(i), base + i + 1));
        }
    }

    private static void renumberAll(List<WaveConfig> waves) {
        for (int i = 0; i < waves.size(); i++) {
            WaveConfig old = waves.get(i);
            waves.set(i, rebuildWithNumber(old, i + 1));
        }
    }

    /** Повертає копію WaveConfig зі зміненим номером хвилі. */
    private static WaveConfig rebuildWithNumber(WaveConfig src, int number) {
        net.minecraft.nbt.CompoundNBT tag = src.save();
        tag.putInt("waveNumber", number);
        return WaveConfig.load(tag);
    }
}
