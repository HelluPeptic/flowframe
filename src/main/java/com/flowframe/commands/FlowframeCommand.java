package com.flowframe.commands;

import com.flowframe.config.FlowframeConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;

public class FlowframeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("flowframe")
            .then(Commands.literal("pathblocksspeed")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("percentage", DoubleArgumentType.doubleArg(0, 1000))
                    .executes(FlowframeCommand::setPathBlockSpeed)))
            .then(Commands.literal("endtoggle")
                .executes(FlowframeCommand::toggleEndPortal)));
    }
    
    private static int setPathBlockSpeed(CommandContext<CommandSourceStack> context) {
        double percentage = DoubleArgumentType.getDouble(context, "percentage");
        double multiplier = 1.0 + (percentage / 100.0);
        
        FlowframeConfig.setPathBlockSpeedMultiplier(multiplier);
        
        context.getSource().sendSuccess(
            () -> Component.literal(String.format("§aPath block speed set to %.1f%% (%.2fx multiplier)", 
                percentage, multiplier)), 
            true
        );
        
        return 1;
    }
    
    private static int toggleEndPortal(CommandContext<CommandSourceStack> context) {
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
