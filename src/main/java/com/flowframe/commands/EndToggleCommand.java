package com.flowframe.commands;

import com.flowframe.config.FlowframeConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class EndToggleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("endtoggle")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // Require operator level 2
            .executes(EndToggleCommand::execute));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        boolean currentState = FlowframeConfig.isEndPortalEnabled();
        boolean newState = !currentState;
        FlowframeConfig.setEndPortalEnabled(newState);
        
        if (newState) {
            source.sendSuccess(() -> Component.literal("§aEnd portals are now enabled"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§cEnd portals are now disabled"), true);
        }
        
        return 1;
    }
}
