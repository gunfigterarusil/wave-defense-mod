package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenceMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateLocationPacket {
    private final Location location;

    public UpdateLocationPacket(Location location) {
        this.location = location;
    }

    public static void encode(UpdateLocationPacket packet, PacketBuffer buf) {
        buf.writeNbt(packet.location.save());
    }

    public static UpdateLocationPacket decode(PacketBuffer buf) {
        CompoundNBT tag = buf.readNbt();
        if (tag == null) return null;
        return new UpdateLocationPacket(Location.load(tag));
    }

    public static void handle(UpdateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2) && packet.location != null) {
                WaveDefenceMod.locationManager.updateLocation(packet.location);

                // Broadcastуємо оновлені дані локацій всім гравцям на сервері
                WaveDefenceMod.waveManager.broadcastLocationData();

                // Синхронізуємо магазин з гравцями що зараз на цій локації
                String locName = packet.location.getName();
                CompoundNBT locNbt = packet.location.save();
                SyncShopPacket syncPkt = new SyncShopPacket(locName, locNbt);
                for (ServerPlayerEntity p : player.getServer().getPlayerList().getPlayers()) {
                    PlayerWaveData pd = WaveDefenceMod.waveManager.getPlayerData(p.getUUID());
                    if (pd != null && pd.getCurrentLocation() != null
                            && pd.getCurrentLocation().getName().equals(locName)) {
                        PacketHandler.sendToPlayer(p, syncPkt);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
