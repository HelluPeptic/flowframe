package com.flowframe.util;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BlazeSpawnerProtection {
    
    public static void initialize() {
        // Register the event to prevent breaking blaze spawners
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            // Only check on server side and for server players
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                // Check if the block being broken is a spawner
                if (state.getBlock() instanceof SpawnerBlock && blockEntity instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    try {
                        // Check if it's a blaze spawner
                        EntityType<?> spawnerEntityType = spawnerBlockEntity.getSpawner().getOrCreateDisplayEntity(level, pos).getType();
                        
                        if (spawnerEntityType == EntityType.BLAZE) {
                            // Check if player is not an operator
                            if (!serverPlayer.canUseGameMasterBlocks()) {
                                // Send a message to the player
                                serverPlayer.sendSystemMessage(
                                    Component.literal("§c[FLOWFRAME] Breaking blaze spawners is disabled on this server!")
                                );
                                
                                // Force client to resync the block to prevent visual glitches
                                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(pos, state));
                                // Also resync the block entity data
                                if (spawnerBlockEntity != null) {
                                    var updatePacket = spawnerBlockEntity.getUpdatePacket();
                                    if (updatePacket != null) {
                                        serverPlayer.connection.send(updatePacket);
                                    }
                                }
                                
                                // Cancel the block breaking
                                return false;
                            }
                        }
                    } catch (Exception e) {
                        // If anything goes wrong, just allow the block to be broken to avoid crashes
                        return true;
                    }
                }
            }
            // Allow breaking by default
            return true;
        });
    }
}