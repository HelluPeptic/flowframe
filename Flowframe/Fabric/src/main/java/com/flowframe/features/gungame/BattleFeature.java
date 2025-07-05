package com.flowframe.features.gungame;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class BattleFeature {
    
    public static void register() {
        // Register commands
        BattleCommand.register();
        
        // Initialize battle when server starts
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Battle.getInstance().initialize(server);
            BattleNotificationManager.getInstance().initialize(server);
            SpectatorPersistence.getInstance().initialize(server);
        });
        
        // Handle player disconnections during battle
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Battle game = Battle.getInstance();
            if (game.isPlayerInGame(handler.getPlayer().getUuid())) {
                game.kickPlayer(handler.getPlayer().getUuid());
            }
        });
        
        // Handle player joins to restore spectators
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SpectatorPersistence.getInstance().handlePlayerJoin(handler.getPlayer());
        });
        
        // Cleanup when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Any cleanup if needed
        });
    }
}
