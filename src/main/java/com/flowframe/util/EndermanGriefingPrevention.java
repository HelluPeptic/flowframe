package com.flowframe.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;

public class EndermanGriefingPrevention {
    
    private static int tickCounter = 0;
    
    public static void initialize() {
        // Register server tick event to clear endermen carried blocks
        // Check every 20 ticks (1 second) to reduce performance impact
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 20) {
                tickCounter = 0;
                
                // Check all loaded worlds
                for (ServerLevel world : server.getAllLevels()) {
                    // Get all endermen in the world and clear their carried blocks
                    world.getAllEntities().forEach(entity -> {
                        if (entity instanceof EnderMan enderman) {
                            // Clear any block the enderman is carrying
                            if (enderman.getCarriedBlock() != null) {
                                enderman.setCarriedBlock(null);
                            }
                        }
                    });
                }
            }
        });
    }
}