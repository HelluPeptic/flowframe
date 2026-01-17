package com.flowframe.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerRidingManager {
    
    // Track riding relationships: rider -> mount player
    private static final Map<UUID, ServerPlayer> ridingPlayers = new HashMap<>();
    private static boolean initialized = false;
    
    public static void initialize() {
        if (initialized) return;
        
        // Register right-click event for players
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand == InteractionHand.MAIN_HAND && 
                world instanceof net.minecraft.server.level.ServerLevel && 
                player instanceof ServerPlayer serverPlayer &&
                entity instanceof ServerPlayer targetPlayer &&
                player != entity) {
                
                // Check if player has empty hand
                if (!serverPlayer.getItemInHand(hand).isEmpty()) {
                    return InteractionResult.PASS;
                }
                
                // Check if the target player is sneaking (wants to be dismounted)
                if (targetPlayer.isShiftKeyDown()) {
                    return InteractionResult.PASS;
                }
                
                // Try to mount the target player
                if (attemptPlayerRiding(serverPlayer, targetPlayer)) {
                    return InteractionResult.SUCCESS;
                }
                
                return InteractionResult.FAIL;
            }
            
            return InteractionResult.PASS;
        });
        
        // Handle dismounting via sneak (check if MOUNT is sneaking, not rider)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Set<UUID> toRemove = new HashSet<>();
            for (Map.Entry<UUID, ServerPlayer> entry : ridingPlayers.entrySet()) {
                ServerPlayer rider = server.getPlayerList().getPlayer(entry.getKey());
                ServerPlayer mount = entry.getValue();
                
                if (rider == null || mount == null || !rider.isPassenger()) {
                    // Player disconnected or stopped riding
                    if (rider != null) {
                        rider.stopRiding();
                    }
                    toRemove.add(entry.getKey());
                } else if (mount.isShiftKeyDown() || rider.isShiftKeyDown()) {
                    // Either mount or rider is sneaking - dismount
                    rider.stopRiding();
                    toRemove.add(entry.getKey());
                }
            }
            toRemove.forEach(ridingPlayers::remove);
        });
        
        initialized = true;
    }
    
    /**
     * Attempt to make one player ride another (now using the mixin to allow it)
     */
    private static boolean attemptPlayerRiding(ServerPlayer rider, ServerPlayer mount) {
        if (rider == mount) return false;
        
        // Check if rider is already riding something
        if (rider.isPassenger()) {
            return false;
        }
        
        // Check if mount already has passengers
        if (!mount.getPassengers().isEmpty()) {
            return false;
        }
        
        // With the EntityRidingMixin in place, this should now work!
        boolean result = rider.startRiding(mount, true, true);
        
        if (result) {
            // Send sync packet to mount player so they can see the rider
            mount.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(mount));
            ridingPlayers.put(rider.getUUID(), mount);
            return true;
        }

        return false;
    }
    
    /**
     * Handle dismounting when a player leaves
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        // Stop riding if this player was riding someone
        if (ridingPlayers.containsKey(player.getUUID())) {
            ridingPlayers.remove(player.getUUID());
        }
        
        // Remove this player from all riding relationships  
        ridingPlayers.values().removeIf(mount -> mount == player);
    }
}