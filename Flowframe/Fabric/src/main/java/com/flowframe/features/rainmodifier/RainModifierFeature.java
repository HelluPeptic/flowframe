package com.flowframe.features.rainmodifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.flowframe.config.FlowframeConfig;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class RainModifierFeature {
    
    // Track which players have notifications enabled
    private static final Map<UUID, Boolean> playerNotifications = new HashMap<>();
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("rainmodifier")
                    .then(CommandManager.literal("percentage")
                        .then(CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                            .requires(source -> Permissions.check(source, "flowframe.rainmodifier", true))
                            .executes(RainModifierFeature::setRainPercentage)
                        )
                    )
                    .then(CommandManager.literal("notificationtoggle")
                        .requires(source -> Permissions.check(source, "flowframe.rainmodifier", true))
                        .executes(RainModifierFeature::toggleNotifications)
                    )
                )
            );
        });
        
        System.out.println("[FLOWFRAME] Rain Modifier feature registered");
    }
    
    private static int setRainPercentage(CommandContext<ServerCommandSource> context) {
        double percentage = DoubleArgumentType.getDouble(context, "value");
        
        FlowframeConfig config = FlowframeConfig.getInstance();
        config.setRainSkipPercentage(percentage);
        
        context.getSource().sendFeedback(
            () -> Text.literal("§a[FLOWFRAME] Rain skip percentage set to " + String.format("%.1f%%", percentage * 100)),
            false
        );
        
        return 1;
    }
    
    private static int toggleNotifications(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            UUID playerId = player.getUuid();
            
            boolean currentState = playerNotifications.getOrDefault(playerId, false);
            boolean newState = !currentState;
            playerNotifications.put(playerId, newState);
            
            String status = newState ? "§aenabled" : "§cdisabled";
            context.getSource().sendFeedback(
                () -> Text.literal("§e[FLOWFRAME] Rain skip notifications " + status),
                false
            );
            
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§c[FLOWFRAME] Error: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Check if rain should be skipped based on the configured percentage
     * @return true if rain should be skipped, false otherwise
     */
    public static boolean shouldSkipRain() {
        FlowframeConfig config = FlowframeConfig.getInstance();
        double skipChance = config.getRainSkipPercentage();
        
        if (skipChance <= 0.0) {
            return false; // Never skip if percentage is 0
        }
        
        double random = Math.random();
        boolean shouldSkip = random < skipChance;
        
        // Send notifications to players who have them enabled
        if (shouldSkip) {
            notifyPlayersRainSkipped();
        }
        
        return shouldSkip;
    }
    
    /**
     * Send notifications to players who have rain skip notifications enabled
     */
    private static void notifyPlayersRainSkipped() {
        // This will be called from a mixin context, so we need to get the server instance
        // For now, we'll store the notification request and handle it in the next tick
        // This is a simplified approach - in a real implementation you might want to use a proper event system
        FlowframeConfig config = FlowframeConfig.getInstance();
        if (config.isRainNotificationsEnabled()) {
            // Global notification setting could be used here if needed
            System.out.println("[FLOWFRAME] Rain skipped due to rain modifier");
        }
    }
    
    /**
     * Check if a specific player has notifications enabled
     * @param playerId The player's UUID
     * @return true if notifications are enabled for this player
     */
    public static boolean hasNotificationsEnabled(UUID playerId) {
        return playerNotifications.getOrDefault(playerId, false);
    }
    
    /**
     * Send rain skip notification to a specific player
     * @param player The player to notify
     */
    public static void notifyPlayer(ServerPlayerEntity player) {
        if (hasNotificationsEnabled(player.getUuid())) {
            FlowframeConfig config = FlowframeConfig.getInstance();
            double percentage = config.getRainSkipPercentage();
            player.sendMessage(Text.literal("§7[FLOWFRAME] Rain skipped! (Skip chance: " + 
                String.format("%.1f%%", percentage * 100) + ")"), false);
        }
    }
}