package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TeleportPacket {
    private final String locationName;

    public TeleportPacket(String locationName) {
        this.locationName = locationName;
    }

    public static void encode(TeleportPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.locationName);
    }

    public static TeleportPacket decode(FriendlyByteBuf buf) {
        return new TeleportPacket(buf.readUtf());
    }

    public static void handle(TeleportPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (packet.locationName.equals("surrender")) {
                WaveDefenseMod.waveManager.surrenderPlayer(player);
                return;
            }

            Location location = WaveDefenseMod.locationManager.getLocation(packet.locationName);
            if (location == null || location.getPlayerSpawn() == null) {
                WaveDefenseMod.LOGGER.warn("Invalid location: " + packet.locationName);
                return;
            }

            // Зберігаємо оригінальну позицію та інвентар
            BlockPos originalPos = player.blockPosition();

            // Телепортація
            BlockPos spawnPos = location.getPlayerSpawn();
            player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

            // Управління інвентарем
            if (!location.isKeepInventory()) {
                // Зберігаємо оригінальний інвентар і очищаємо
                player.getInventory().clearContent();

                // Видаємо стартове спорядження
                for (ItemStack item : location.getStartingItems()) {
                    player.getInventory().add(item.copy());
                }
            }

            // Запуск системи хвиль
            WaveDefenseMod.waveManager.startWave(player, location);
        });
        ctx.get().setPacketHandled(true);
    }
}