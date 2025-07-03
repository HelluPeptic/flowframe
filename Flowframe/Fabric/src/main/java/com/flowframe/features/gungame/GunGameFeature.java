package com.flowframe.features.gungame;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class GunGameFeature {
    
    public static void register() {
        // Register commands
        GunGameCommand.register();
        
        // Initialize gun game when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            GunGame.getInstance().initialize(server);
        });
        
        // Handle player disconnections during gun game
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GunGame game = GunGame.getInstance();
            if (game.isPlayerInGame(handler.getPlayer().getUuid())) {
                game.kickPlayer(handler.getPlayer().getUuid());
            }
        });
        
        // Cleanup when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Any cleanup if needed
        });
    }
}
