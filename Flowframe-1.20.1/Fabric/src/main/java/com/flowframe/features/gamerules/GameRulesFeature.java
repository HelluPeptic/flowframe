package com.flowframe.features.gamerules;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class GameRulesFeature {
    
    public static void register() {
        // Set gamerules when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Set gamerules to ensure they're always enabled after restart
            try {
                server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(), 
                    "gamerule doDaylightCycle true"
                );
                server.getCommandManager().executeWithPrefix(
                    server.getCommandSource(), 
                    "gamerule doWeatherCycle true"
                );
                
                System.out.println("[FLOWFRAME] GameRules set: doDaylightCycle=true, doWeatherCycle=true");
            } catch (Exception e) {
                System.err.println("[FLOWFRAME] Failed to set game rules: " + e.getMessage());
            }
        });
    }
}
