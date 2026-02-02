package com.flowframe.commands;

import com.flowframe.config.FlowframeConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class NetherToggleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nethertoggle")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // Require operator level 2
            .executes(NetherToggleCommand::execute));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        boolean currentState = FlowframeConfig.isNetherPortalEnabled();
        boolean newState = !currentState;
        FlowframeConfig.setNetherPortalEnabled(newState);
        
        if (newState) {
            source.sendSuccess(() -> Component.literal("§aNether portals are now enabled"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§cNether portals are now disabled"), true);
        }
        
        return 1;
    }
}