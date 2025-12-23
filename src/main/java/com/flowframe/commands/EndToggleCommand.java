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
            .executes(EndToggleCommand::execute));
    }
    
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }
        
        boolean currentState = FlowframeConfig.isEndPortalEnabled(player.getUUID());
        boolean newState = !currentState;
        FlowframeConfig.setEndPortalEnabled(player.getUUID(), newState);
        
        if (newState) {
            player.sendSystemMessage(Component.literal("§aEnd portals are now enabled for you"));
        } else {
            player.sendSystemMessage(Component.literal("§cEnd portals are now disabled for you"));
        }
        
        return 1;
    }
}
