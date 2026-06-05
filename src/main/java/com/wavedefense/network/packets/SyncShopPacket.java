package com.wavedefense.network.packets;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C: Синхронізація магазину локації.
 * Надсилається коли адмін змінює налаштування магазину поки гравці на локації.
 */
public class SyncShopPacket {
    private final CompoundNBT locationData; // повний NBT локації
    private final String locationName;

    public SyncShopPacket(String locationName, CompoundNBT locationData) {
        this.locationName = locationName;
        this.locationData = locationData;
    }

    public static void encode(SyncShopPacket p, PacketBuffer buf) {
        buf.writeUtf(p.locationName);
        buf.writeNbt(p.locationData);
    }

    public static SyncShopPacket decode(PacketBuffer buf) {
        return new SyncShopPacket(buf.readUtf(), buf.readNbt());
    }

    public static void handle(SyncShopPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(p))
        );
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static class ClientHandler {
        static void handle(SyncShopPacket p) {
            // Оновлюємо локальний кеш локацій
            com.wavedefense.data.Location updated = com.wavedefense.data.Location.load(p.locationData);
            if (updated == null) return;
            com.wavedefense.gui.ClientLocationManager.updateSingleLocation(updated);

            // Якщо зараз відкритий магазин цієї локації — оновлюємо його
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof com.wavedefense.gui.PlayerShopScreen) {

                com.wavedefense.gui.PlayerShopScreen shopScreen = (com.wavedefense.gui.PlayerShopScreen) mc.screen;
                if (p.locationName.equals(shopScreen.getLocationName())) {
                    com.wavedefense.data.Location loc =
                        com.wavedefense.gui.ClientLocationManager.getLocation(p.locationName);
                    if (loc != null) {
                        com.wavedefense.data.ShopPoint currentPoint = shopScreen.getShopPoint();
                        // Оновлюємо відповідну точку магазину якщо є
                        if (currentPoint != null && loc.isPointShopMode()) {
                            com.wavedefense.data.ShopPoint updated2 = loc.getShopPoints().stream()
                                .filter(sp -> sp.getName().equals(currentPoint.getName()))
                                .findFirst().orElse(null);
                            mc.setScreen(new com.wavedefense.gui.PlayerShopScreen(loc,
                                updated2 != null ? updated2 : currentPoint));
                        } else {
                            mc.setScreen(new com.wavedefense.gui.PlayerShopScreen(loc));
                        }
                    }
                }
            }
        }
    }
}
