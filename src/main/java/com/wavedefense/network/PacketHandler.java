package com.wavedefense.network;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WaveDefenseMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.registerMessage(id(), TeleportPacket.class,
                TeleportPacket::encode,
                TeleportPacket::decode,
                TeleportPacket::handle);

        INSTANCE.registerMessage(id(), UpdatePointsPacket.class,
                UpdatePointsPacket::encode,
                UpdatePointsPacket::decode,
                UpdatePointsPacket::handle);

        INSTANCE.registerMessage(id(), PurchaseItemPacket.class,
                PurchaseItemPacket::encode,
                PurchaseItemPacket::decode,
                PurchaseItemPacket::handle);

        INSTANCE.registerMessage(id(), SellItemPacket.class,
                SellItemPacket::encode,
                SellItemPacket::decode,
                SellItemPacket::handle);

        INSTANCE.registerMessage(id(), SyncStatsPacket.class,
                SyncStatsPacket::encode,
                SyncStatsPacket::decode,
                SyncStatsPacket::handle);

        INSTANCE.registerMessage(id(), SyncLocationDataPacket.class,
                SyncLocationDataPacket::encode,
                SyncLocationDataPacket::decode,
                SyncLocationDataPacket::handle);

        INSTANCE.registerMessage(id(), RequestLocationDataPacket.class,
                RequestLocationDataPacket::encode,
                RequestLocationDataPacket::decode,
                RequestLocationDataPacket::handle);

        INSTANCE.registerMessage(id(), CreateLocationPacket.class,
                CreateLocationPacket::encode,
                CreateLocationPacket::decode,
                CreateLocationPacket::handle);

        INSTANCE.registerMessage(id(), DeleteLocationPacket.class,
                DeleteLocationPacket::encode,
                DeleteLocationPacket::decode,
                DeleteLocationPacket::handle);

        INSTANCE.registerMessage(id(), UpdateLocationPacket.class,
                UpdateLocationPacket::encode,
                UpdateLocationPacket::decode,
                UpdateLocationPacket::handle);

        INSTANCE.registerMessage(id(), UpdatePlayerSettingsPacket.class,
                UpdatePlayerSettingsPacket::encode,
                UpdatePlayerSettingsPacket::decode,
                UpdatePlayerSettingsPacket::handle);

        INSTANCE.registerMessage(id(), SurrenderPacket.class,
                SurrenderPacket::encode,
                SurrenderPacket::decode,
                SurrenderPacket::handle);

        WaveDefenseMod.LOGGER.info("Network packets registered");
    }

    public void send(PacketDistributor.PacketTarget target, Object message) {
        INSTANCE.send(target, message);
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }
}
