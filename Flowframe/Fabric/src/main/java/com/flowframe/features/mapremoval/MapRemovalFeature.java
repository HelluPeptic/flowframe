package com.flowframe.features.mapremoval;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.TypedActionResult;

public class MapRemovalFeature {
    public static void register() {
        // Register item use event to cancel map usage
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Items.MAP) || stack.isOf(Items.FILLED_MAP)) {
                stack.setCount(0); // Delete the map
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });

        System.out.println("[FLOWFRAME] Map removal feature initialized");
    }
}
