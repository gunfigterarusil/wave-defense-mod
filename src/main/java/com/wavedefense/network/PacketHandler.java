package com.wavedefense.network;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
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

        INSTANCE.registerMessage(id(), SyncLocationDataPacket.class,
                SyncLocationDataPacket::encode,
                SyncLocationDataPacket::decode,
                SyncLocationDataPacket::handle);

        INSTANCE.registerMessage(id(), SurrenderPacket.class,
                SurrenderPacket::encode,
                SurrenderPacket::decode,
                SurrenderPacket::handle);

        WaveDefenseMod.LOGGER.info("Network packets registered");
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }
}
