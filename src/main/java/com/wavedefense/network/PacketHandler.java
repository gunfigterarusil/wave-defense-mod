package com.wavedefense.network;

import com.wavedefense.WaveDefenseMod;
import com.wavedefense.network.packets.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "8";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WaveDefenseMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() { return packetId++; }

    public static void register() {
        c2s(TeleportPacket.class, TeleportPacket::encode, TeleportPacket::decode, TeleportPacket::handle);
        s2c(UpdatePointsPacket.class, UpdatePointsPacket::encode, UpdatePointsPacket::decode, UpdatePointsPacket::handle);
        c2s(PurchaseItemPacket.class, PurchaseItemPacket::encode, PurchaseItemPacket::decode, PurchaseItemPacket::handle);
        c2s(SellItemPacket.class, SellItemPacket::encode, SellItemPacket::decode, SellItemPacket::handle);
        s2c(SyncStatsPacket.class, SyncStatsPacket::encode, SyncStatsPacket::decode, SyncStatsPacket::handle);
        s2c(SyncPlayerDataPacket.class, SyncPlayerDataPacket::encode, SyncPlayerDataPacket::decode, SyncPlayerDataPacket::handle);
        s2c(SyncLocationDataPacket.class, SyncLocationDataPacket::encode, SyncLocationDataPacket::decode, SyncLocationDataPacket::handle);
        c2s(RequestLocationDataPacket.class, RequestLocationDataPacket::encode, RequestLocationDataPacket::decode, RequestLocationDataPacket::handle);
        c2s(CreateLocationPacket.class, CreateLocationPacket::encode, CreateLocationPacket::decode, CreateLocationPacket::handle);
        c2s(DeleteLocationPacket.class, DeleteLocationPacket::encode, DeleteLocationPacket::decode, DeleteLocationPacket::handle);
        c2s(UpdateLocationPacket.class, UpdateLocationPacket::encode, UpdateLocationPacket::decode, UpdateLocationPacket::handle);
        c2s(MergeLocationPacket.class, MergeLocationPacket::encode, MergeLocationPacket::decode, MergeLocationPacket::handle);
        c2s(UpdatePlayerSettingsPacket.class, UpdatePlayerSettingsPacket::encode, UpdatePlayerSettingsPacket::decode, UpdatePlayerSettingsPacket::handle);
        c2s(SurrenderPacket.class, SurrenderPacket::encode, SurrenderPacket::decode, SurrenderPacket::handle);
        s2c(OpenMenuPacket.class, OpenMenuPacket::encode, OpenMenuPacket::decode, OpenMenuPacket::handle);
        c2s(AdminTeleportPacket.class, AdminTeleportPacket::encode, AdminTeleportPacket::decode, AdminTeleportPacket::handle);
        s2c(SyncPvpStatePacket.class, SyncPvpStatePacket::encode, SyncPvpStatePacket::decode, SyncPvpStatePacket::handle);
        c2s(ExportLocationPacket.class, ExportLocationPacket::encode, ExportLocationPacket::decode, ExportLocationPacket::handle);
        c2s(ImportLocationPacket.class, ImportLocationPacket::encode, ImportLocationPacket::decode, ImportLocationPacket::handle);
        s2c(ExportListResponsePacket.class, ExportListResponsePacket::encode, ExportListResponsePacket::decode, ExportListResponsePacket::handle);
        s2c(SyncShopPacket.class, SyncShopPacket::encode, SyncShopPacket::decode, SyncShopPacket::handle);
        c2s(ExitPvpPacket.class, ExitPvpPacket::encode, ExitPvpPacket::decode, ExitPvpPacket::handle);

        s2c(SyncTeammatesPacket.class, SyncTeammatesPacket::encode, SyncTeammatesPacket::decode, SyncTeammatesPacket::handle);
        c2s(LeaveLocationPacket.class, LeaveLocationPacket::encode, LeaveLocationPacket::decode, LeaveLocationPacket::handle);
        c2s(ExportShopPacket.class, ExportShopPacket::encode, ExportShopPacket::decode, ExportShopPacket::handle);
        c2s(ImportShopPacket.class, ImportShopPacket::encode, ImportShopPacket::decode, ImportShopPacket::handle);
        s2c(ShopExportListPacket.class, ShopExportListPacket::encode, ShopExportListPacket::decode, ShopExportListPacket::handle);
        c2s(RequestShopExportListPacket.class, RequestShopExportListPacket::encode, RequestShopExportListPacket::decode, RequestShopExportListPacket::handle);

        c2s(ExportWavePacket.class, ExportWavePacket::encode, ExportWavePacket::decode, ExportWavePacket::handle);
        c2s(ImportWavePacket.class, ImportWavePacket::encode, ImportWavePacket::decode, ImportWavePacket::handle);
        s2c(WaveExportListPacket.class, WaveExportListPacket::encode, WaveExportListPacket::decode, WaveExportListPacket::handle);
        c2s(RequestWaveExportListPacket.class, RequestWaveExportListPacket::encode, RequestWaveExportListPacket::decode, RequestWaveExportListPacket::handle);

        // CtP / KotH + Leaderboard packets
        s2c(SyncCtpStatePacket.class, SyncCtpStatePacket::encode, SyncCtpStatePacket::decode, SyncCtpStatePacket::handle);
        c2s(RequestLeaderboardPacket.class, RequestLeaderboardPacket::encode, RequestLeaderboardPacket::decode, RequestLeaderboardPacket::handle);
        s2c(LeaderboardDataPacket.class, LeaderboardDataPacket::encode, LeaderboardDataPacket::decode, LeaderboardDataPacket::handle);

        // B5: post-match scoreboard (PvP)
        s2c(OpenPostMatchScoreboardPacket.class,
            OpenPostMatchScoreboardPacket::encode,
            OpenPostMatchScoreboardPacket::decode,
            OpenPostMatchScoreboardPacket::handle);

        // Bulk-add for Tacz (and any other future workflow) — keeps each network
        // packet small instead of serialising the entire Location for every change.
        c2s(BulkAddShopItemsPacket.class,
            BulkAddShopItemsPacket::encode,
            BulkAddShopItemsPacket::decode,
            BulkAddShopItemsPacket::handle);

        // v0.2.62: ready-check toggle (hotkey R)
        c2s(ReadyCheckPacket.class,
            ReadyCheckPacket::encode,
            ReadyCheckPacket::decode,
            ReadyCheckPacket::handle);

        // v0.2.63: duplicate location (⎘ button in AdminMenuScreen)
        c2s(DuplicateLocationPacket.class,
            DuplicateLocationPacket::encode,
            DuplicateLocationPacket::decode,
            DuplicateLocationPacket::handle);

        // v0.2.64: chunked shop save (ShopEditor with > BATCH_SIZE items)
        c2s(ReplaceShopItemsPacket.class,
            ReplaceShopItemsPacket::encode,
            ReplaceShopItemsPacket::decode,
            ReplaceShopItemsPacket::handle);

        WaveDefenseMod.LOGGER.info("Network packets registered");
    }

    private static <MSG> void c2s(Class<MSG> type,
                                  BiConsumer<MSG, FriendlyByteBuf> encoder,
                                  Function<FriendlyByteBuf, MSG> decoder,
                                  BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        INSTANCE.registerMessage(id(), type, encoder, decoder, handler,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static <MSG> void s2c(Class<MSG> type,
                                  BiConsumer<MSG, FriendlyByteBuf> encoder,
                                  Function<FriendlyByteBuf, MSG> decoder,
                                  BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        INSTANCE.registerMessage(id(), type, encoder, decoder, handler,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
