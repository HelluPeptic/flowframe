package com.flowframe.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SitCommand {
    
    // Track sitting armor stands for each player
    private static final Map<UUID, ArmorStand> sittingEntities = new HashMap<>();
    private static boolean tickEventRegistered = false;
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sit")
            .executes(SitCommand::execute));
            
        // Register tick event to check for shift-to-stand
        if (!tickEventRegistered) {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                checkForShiftToStand();
            });
            tickEventRegistered = true;
        }
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Check if player is already sitting
        if (sittingEntities.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou are already sitting! Shift to stand up."));
            return 0;
        }
        
        // Check if player is on a solid block
        BlockPos blockBelow = player.blockPosition().below();
        Level world = player.level();
        BlockState blockState = world.getBlockState(blockBelow);
        
        if (!blockState.isSolid()) {
            player.sendSystemMessage(Component.literal("§cYou can only sit on solid blocks!"));
            return 0;
        }
        
        // Check if player is riding something else
        if (player.isPassenger()) {
            player.sendSystemMessage(Component.literal("§cYou cannot sit while riding something!"));
            return 0;
        }
        
        // Create invisible armor stand for sitting
        ArmorStand sittingStand = new ArmorStand(EntityType.ARMOR_STAND, world);
        
        // Position the armor stand at player location, slightly lower to look like sitting
        Vec3 playerPos = player.position();
        sittingStand.setPos(playerPos.x, playerPos.y - 2.0, playerPos.z);
        
        // Configure armor stand for sitting
        sittingStand.setInvisible(true);
        sittingStand.setInvulnerable(true);
        sittingStand.setSilent(true);
        sittingStand.setNoGravity(true);
        sittingStand.setShowArms(false);
        sittingStand.setNoBasePlate(true);
        sittingStand.setCustomNameVisible(false);
        
        // Add armor stand to world
        world.addFreshEntity(sittingStand);
        
        // Make player ride the armor stand
        player.startRiding(sittingStand);
        
        // Store the armor stand reference
        sittingEntities.put(player.getUUID(), sittingStand);
        
        player.sendSystemMessage(Component.literal("§aYou are now sitting. Shift to stand up."));
        
        return 1;
    }
    
    /**
     * Make a player stand up if they are sitting
     */
    public static void standUp(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ArmorStand sittingStand = sittingEntities.get(playerId);
        
        if (sittingStand != null) {
            // Dismount player
            player.stopRiding();
            
            // Remove the armor stand
            sittingStand.discard();
            
            // Remove from tracking
            sittingEntities.remove(playerId);
        }
    }
    
    /**
     * Check for players who are sitting and shifting to stand them up
     */
    private static void checkForShiftToStand() {
        // Create a copy of the keyset to avoid concurrent modification
        Set<UUID> sittingPlayerIds = new HashSet<>(sittingEntities.keySet());
        
        for (UUID playerId : sittingPlayerIds) {
            ArmorStand sittingStand = sittingEntities.get(playerId);
            if (sittingStand != null && !sittingStand.isRemoved()) {
                // Check if the player is still a passenger
                if (!sittingStand.getPassengers().isEmpty()) {
                    Entity passenger = sittingStand.getPassengers().get(0);
                    if (passenger instanceof ServerPlayer player) {
                        // Check if player is shifting/sneaking
                        if (player.isShiftKeyDown()) {
                            standUp(player);
                            player.sendSystemMessage(Component.literal("§aYou stood up."));
                        }
                    }
                } else {
                    // Player is no longer riding, clean up
                    sittingEntities.remove(playerId);
                    sittingStand.discard();
                }
            }
        }
    }

    /**
     * Clean up sitting entities when player disconnects
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        standUp(player);
    }
    
    /**
     * Check if a player is currently sitting
     */
    public static boolean isPlayerSitting(ServerPlayer player) {
        return sittingEntities.containsKey(player.getUUID());
    }
}