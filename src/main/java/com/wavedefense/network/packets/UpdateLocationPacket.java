package com.wavedefense.network.packets;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.data.Location;
import com.wavedefense.network.PacketHandler;
import com.wavedefense.wave.PlayerWaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateLocationPacket {
    private final Location location;

    public UpdateLocationPacket(Location location) {
        this.location = location;
    }

    public static void encode(UpdateLocationPacket packet, FriendlyByteBuf buf) {
        buf.writeNbt(packet.location.save());
    }

    public static UpdateLocationPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) return null;
        return new UpdateLocationPacket(Location.load(tag));
    }

    public static void handle(UpdateLocationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2) && packet.location != null) {
                WaveDefenseMod.locationManager.updateLocation(packet.location);

                // Broadcastуємо оновлені дані локацій всім гравцям на сервері
                WaveDefenseMod.waveManager.broadcastLocationData();

                // Синхронізуємо магазин з гравцями що зараз на цій локації
                String locName = packet.location.getName();
                CompoundTag locNbt = packet.location.save();
                SyncShopPacket syncPkt = new SyncShopPacket(locName, locNbt);
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    PlayerWaveData pd = WaveDefenseMod.waveManager.getPlayerData(p.getUUID());
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
