package com.wavedefense.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public class PlayerBackup {
    private final BlockPos originalPosition;
    private final List<ItemStack> inventory = new ArrayList<>();

    public PlayerBackup(ServerPlayer player) {
        this.originalPosition = player.blockPosition();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            inventory.add(player.getInventory().getItem(i).copy());
        }
    }

    public void restore(ServerPlayer player) {
        player.teleportTo(originalPosition.getX() + 0.5, originalPosition.getY(), originalPosition.getZ() + 0.5);
        player.getInventory().clearContent();
        for (int i = 0; i < inventory.size(); i++) {
            player.getInventory().setItem(i, inventory.get(i));
        }
    }
}
