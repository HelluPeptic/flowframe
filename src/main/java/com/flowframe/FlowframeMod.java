package com.flowframe;

import com.flowframe.commands.FlowframeCommand;
import com.flowframe.config.FlowframeConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
        });
        
        LOGGER.info("Flowframe mod initialized!");
    }
}
