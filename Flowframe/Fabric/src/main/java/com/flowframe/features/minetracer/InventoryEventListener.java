package com.flowframe.features.minetracer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class InventoryEventListener {
    public static void register() {
        // No-op: tick-based fallback, see onPlayerTick
    }

    // Call this from a PlayerEntity mixin's tick method
    public static void onPlayerTick(PlayerEntity player) {
        // Check for picked up items
        for (ItemStack stack : player.getInventory().main) {
            if (!stack.isEmpty() && stack.getOrCreateNbt().getBoolean("flowframe_logged_pickup") == false) {
                BlockPos pos = player.getBlockPos();
                LogStorage.logContainerAction("inventory", player, pos, stack);
                stack.getOrCreateNbt().putBoolean("flowframe_logged_pickup", true);
            }
        }
        // For item drops, you would need to Mixin into the drop method and call LogStorage.logContainerAction("inventory", ...)
    }
}
