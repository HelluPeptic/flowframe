package com.flowframe.features.spawnerprotection;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class SpawnerProtection {
    
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            // Check if the block being clicked is a spawner
            if (world.getBlockState(hitResult.getBlockPos()).getBlock() == Blocks.SPAWNER) {
                // Check if player is holding a spawn egg
                if (player.getStackInHand(hand).getItem() instanceof SpawnEggItem) {
                    // Check if player is in survival mode
                    if (!player.isCreative() && !player.isSpectator()) {
                        // Cancel the interaction
                        if (!world.isClient) {
                            player.sendMessage(Text.literal("§c[FLOWFRAME] You cannot change spawners with spawn eggs in survival mode!"), true);
                        }
                        return ActionResult.FAIL;
                    }
                }
            }
            
            return ActionResult.PASS;
        });
    }
}
