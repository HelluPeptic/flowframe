package com.flowframe.features.mapremoval;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.FilledMapItem;
import net.minecraft.util.TypedActionResult;
import net.minecraft.server.network.ServerPlayerEntity;

public class MapRemovalFeature {
    private static boolean isMapItem(ItemStack stack) {
        return stack.isOf(Items.MAP) || 
               stack.isOf(Items.FILLED_MAP) || 
               stack.isOf(Items.WRITABLE_BOOK) ||  // To catch map-in-book
               stack.getItem() instanceof FilledMapItem;
    }
    
    public static void register() {
        // Remove maps when used
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (isMapItem(stack)) {
                stack.setCount(0);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // Check and remove maps from player inventory every tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // Check main inventory
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (isMapItem(stack)) {
                        player.getInventory().removeStack(i);
                    }
                }
                
                // Check offhand
                ItemStack offhandStack = player.getOffHandStack();
                if (isMapItem(offhandStack)) {
                    player.getInventory().offHand.set(0, ItemStack.EMPTY);
                }
            }
        });

        System.out.println("[FLOWFRAME] Map removal feature initialized");
    }
}
