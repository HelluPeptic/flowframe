package com.flowframe.features.minetracer;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;

// Listens for chest and container events
public class ChestEventListener {
    public static void register() {
        // Listen for block use (opening containers)
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            Block block = world.getBlockState(pos).getBlock();
            if (isTrackedContainer(block)) {
                // Optionally: log open event or prepare for inventory change tracking
            }
            return ActionResult.PASS;
        });

        // Listen for block break (removal of containers)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            Block block = state.getBlock();
            if (isTrackedContainer(block) && blockEntity instanceof Inventory inv) {
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (!stack.isEmpty()) {
                        LogStorage.logContainerAction("remove", player, pos, stack);
                    }
                }
            }
            return true;
        });
        // Block break (removal)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            Identifier blockId = Registries.BLOCK.getId(state.getBlock());
            String nbt = blockEntity != null ? blockEntity.createNbt().toString() : null;
            LogStorage.logBlockAction("remove", player, pos, blockId.toString(), nbt);
            // Sign text logging
            if (blockEntity instanceof SignBlockEntity sign) {
                LogStorage.logSignAction("remove", player, pos, sign.getFrontText().getMessage(0, false).getString(), sign.createNbt().toString());
            }
            return true;
        });
        // TODO: Block place logging via mixin
    }

    private static boolean isTrackedContainer(Block block) {
        // Track vanilla chests, shulker boxes, and (future) Inmis backpacks
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL ||
               block == Blocks.ENDER_CHEST || block.getTranslationKey().contains("shulker_box");
        // TODO: Add Inmis backpack block/item detection
    }
}
