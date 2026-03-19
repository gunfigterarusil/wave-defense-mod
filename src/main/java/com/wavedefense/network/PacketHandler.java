package com.wavedefense.network;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "7";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WaveDefenseMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() { return packetId++; }

    public static void register() {
        INSTANCE.registerMessage(id(), TeleportPacket.class,
                TeleportPacket::encode, TeleportPacket::decode, TeleportPacket::handle);
        INSTANCE.registerMessage(id(), UpdatePointsPacket.class,
                UpdatePointsPacket::encode, UpdatePointsPacket::decode, UpdatePointsPacket::handle);
        INSTANCE.registerMessage(id(), PurchaseItemPacket.class,
                PurchaseItemPacket::encode, PurchaseItemPacket::decode, PurchaseItemPacket::handle);
        INSTANCE.registerMessage(id(), SellItemPacket.class,
                SellItemPacket::encode, SellItemPacket::decode, SellItemPacket::handle);
        INSTANCE.registerMessage(id(), SyncStatsPacket.class,
                SyncStatsPacket::encode, SyncStatsPacket::decode, SyncStatsPacket::handle);
        INSTANCE.registerMessage(id(), SyncPlayerDataPacket.class,
                SyncPlayerDataPacket::encode, SyncPlayerDataPacket::decode, SyncPlayerDataPacket::handle);
        INSTANCE.registerMessage(id(), SyncLocationDataPacket.class,
                SyncLocationDataPacket::encode, SyncLocationDataPacket::decode, SyncLocationDataPacket::handle);
        INSTANCE.registerMessage(id(), RequestLocationDataPacket.class,
                RequestLocationDataPacket::encode, RequestLocationDataPacket::decode, RequestLocationDataPacket::handle);
        INSTANCE.registerMessage(id(), CreateLocationPacket.class,
                CreateLocationPacket::encode, CreateLocationPacket::decode, CreateLocationPacket::handle);
        INSTANCE.registerMessage(id(), DeleteLocationPacket.class,
                DeleteLocationPacket::encode, DeleteLocationPacket::decode, DeleteLocationPacket::handle);
        INSTANCE.registerMessage(id(), UpdateLocationPacket.class,
                UpdateLocationPacket::encode, UpdateLocationPacket::decode, UpdateLocationPacket::handle);
        INSTANCE.registerMessage(id(), UpdatePlayerSettingsPacket.class,
                UpdatePlayerSettingsPacket::encode, UpdatePlayerSettingsPacket::decode, UpdatePlayerSettingsPacket::handle);
        INSTANCE.registerMessage(id(), SurrenderPacket.class,
                SurrenderPacket::encode, SurrenderPacket::decode, SurrenderPacket::handle);
        INSTANCE.registerMessage(id(), OpenMenuPacket.class,
                OpenMenuPacket::encode, OpenMenuPacket::decode, OpenMenuPacket::handle);
        INSTANCE.registerMessage(id(), AdminTeleportPacket.class,
                AdminTeleportPacket::encode, AdminTeleportPacket::decode, AdminTeleportPacket::handle);
        INSTANCE.registerMessage(id(), SyncPvpStatePacket.class,
                SyncPvpStatePacket::encode, SyncPvpStatePacket::decode, SyncPvpStatePacket::handle);
        INSTANCE.registerMessage(id(), ExportLocationPacket.class,
                ExportLocationPacket::encode, ExportLocationPacket::decode, ExportLocationPacket::handle);
        INSTANCE.registerMessage(id(), ImportLocationPacket.class,
                ImportLocationPacket::encode, ImportLocationPacket::decode, ImportLocationPacket::handle);
        INSTANCE.registerMessage(id(), ExportListResponsePacket.class,
                ExportListResponsePacket::encode, ExportListResponsePacket::decode, ExportListResponsePacket::handle);
        INSTANCE.registerMessage(id(), SyncShopPacket.class,
                SyncShopPacket::encode, SyncShopPacket::decode, SyncShopPacket::handle);
        INSTANCE.registerMessage(id(), ExitPvpPacket.class,
                ExitPvpPacket::encode, ExitPvpPacket::decode, ExitPvpPacket::handle);

        // v0.2.27: нові пакети
        INSTANCE.registerMessage(id(), SyncTeammatesPacket.class,
                SyncTeammatesPacket::encode, SyncTeammatesPacket::decode, SyncTeammatesPacket::handle);
        INSTANCE.registerMessage(id(), LeaveLocationPacket.class,
                LeaveLocationPacket::encode, LeaveLocationPacket::decode, LeaveLocationPacket::handle);
        INSTANCE.registerMessage(id(), ExportShopPacket.class,
                ExportShopPacket::encode, ExportShopPacket::decode, ExportShopPacket::handle);
        INSTANCE.registerMessage(id(), ImportShopPacket.class,
                ImportShopPacket::encode, ImportShopPacket::decode, ImportShopPacket::handle);
        INSTANCE.registerMessage(id(), ShopExportListPacket.class,
                ShopExportListPacket::encode, ShopExportListPacket::decode, ShopExportListPacket::handle);
        INSTANCE.registerMessage(id(), RequestShopExportListPacket.class,
                RequestShopExportListPacket::encode, RequestShopExportListPacket::decode, RequestShopExportListPacket::handle);

        // Wave export/import
        INSTANCE.registerMessage(id(), ExportWavePacket.class,
                ExportWavePacket::encode, ExportWavePacket::decode, ExportWavePacket::handle);
        INSTANCE.registerMessage(id(), ImportWavePacket.class,
                ImportWavePacket::encode, ImportWavePacket::decode, ImportWavePacket::handle);
        INSTANCE.registerMessage(id(), WaveExportListPacket.class,
                WaveExportListPacket::encode, WaveExportListPacket::decode, WaveExportListPacket::handle);
        INSTANCE.registerMessage(id(), RequestWaveExportListPacket.class,
                RequestWaveExportListPacket::encode, RequestWaveExportListPacket::decode, RequestWaveExportListPacket::handle);

        WaveDefenseMod.LOGGER.info("Network packets registered");
    }

    public void send(PacketDistributor.PacketTarget target, Object message) {
        INSTANCE.send(target, message);
    }

    public static void sendToServer(Object packet) {
        INSTANCE.sendToServer(packet);
    }

    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, Object packet) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(Object packet) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }
}
