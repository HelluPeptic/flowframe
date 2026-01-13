package com.flowframe;

import com.flowframe.commands.FlowframeCommand;
import com.flowframe.commands.TownCommand;
import com.flowframe.commands.TownAdminCommand;
import com.flowframe.config.FlowframeConfig;
import com.flowframe.town.TownManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowframeMod implements ModInitializer {

    public static final String MOD_ID = "flowframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Flowframe mod initializing...");

        // Initialize config
        FlowframeConfig.init();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FlowframeCommand.register(dispatcher);
            TownCommand.register(dispatcher);
            TownAdminCommand.register(dispatcher);
        });

        // Register server lifecycle events for town system
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TownManager.initialize(server);
            LOGGER.info("Town system initialized");
        });
        
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TownManager.shutdown();
            LOGGER.info("Town system shut down");
        });

        // Handle player join events to restore team prefixes
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Delay team restoration by 1 tick to ensure player is fully loaded
            server.execute(() -> {
                TownManager.restorePlayerTeamOnJoin(handler.getPlayer());
            });
        });

        LOGGER.info("Flowframe mod initialized!");
    }
}
