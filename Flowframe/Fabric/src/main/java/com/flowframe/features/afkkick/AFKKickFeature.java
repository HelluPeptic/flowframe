package com.flowframe.features.afkkick;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.flowframe.config.FlowframeConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class AFKKickFeature {
    
    // Track player data for AFK detection
    private static final Map<UUID, PlayerAFKData> playerData = new ConcurrentHashMap<>();
    private static int tickCounter = 0;
    
    public static void register() {
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("afkkicking")
                    .then(CommandManager.literal("toggle")
                        .requires(source -> Permissions.check(source, "flowframe.afkkicking", true))
                        .executes(AFKKickFeature::toggleAFKKicking)
                    )
                    .then(CommandManager.literal("limit")
                        .requires(source -> Permissions.check(source, "flowframe.afkkicking", true))
                        .then(CommandManager.argument("minutes", IntegerArgumentType.integer(1, 120))
                            .executes(AFKKickFeature::setAFKLimit)
                        )
                    )
                    .then(CommandManager.literal("status")
                        .requires(source -> Permissions.check(source, "flowframe.afkkicking", true))
                        .executes(AFKKickFeature::showAFKStatus)
                    )
                )
            );
        });
        
        // Register player connection events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // Give players a grace period when they first join (add 30 seconds to current time)
            long joinTime = System.currentTimeMillis() + 30000; // 30 second grace period
            playerData.put(player.getUuid(), new PlayerAFKData(
                player.getYaw(), 
                player.getPitch(), 
                joinTime
            ));
        });
        
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerData.remove(handler.getPlayer().getUuid());
        });
        
        // Register tick event for AFK checking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            
            // Check AFK status every 20 ticks (1 second)
            if (tickCounter % 20 == 0) {
                FlowframeConfig config = FlowframeConfig.getInstance();
                if (config.isAFKKickingEnabled()) {
                    checkAFKPlayers(server);
                }
            }
        });

    }
    
    private static int toggleAFKKicking(CommandContext<ServerCommandSource> context) {
        FlowframeConfig config = FlowframeConfig.getInstance();
        boolean currentState = config.isAFKKickingEnabled();
        boolean newState = !currentState;
        
        config.setAFKKickingEnabled(newState);
        
        String status = newState ? "§aenabled" : "§cdisabled";
        context.getSource().sendFeedback(
            () -> Text.literal("§e[FLOWFRAME] AFK kicking " + status),
            false
        );
        
        return 1;
    }
    
    private static int setAFKLimit(CommandContext<ServerCommandSource> context) {
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        
        FlowframeConfig config = FlowframeConfig.getInstance();
        config.setAFKTimeoutMinutes(minutes);
        
        context.getSource().sendFeedback(
            () -> Text.literal("§a[FLOWFRAME] AFK timeout set to " + minutes + " minutes"),
            false
        );
        
        return 1;
    }
    
    private static int showAFKStatus(CommandContext<ServerCommandSource> context) {
        FlowframeConfig config = FlowframeConfig.getInstance();
        boolean enabled = config.isAFKKickingEnabled();
        int timeout = config.getAFKTimeoutMinutes();
        
        context.getSource().sendFeedback(
            () -> Text.literal("§6[FLOWFRAME] AFK Kicking Status:\n" +
                             "§7Enabled: " + (enabled ? "§aYes" : "§cNo") + "\n" +
                             "§7Timeout: §e" + timeout + " minutes\n" +
                             "§7Tracked Players: §e" + playerData.size()),
            false
        );
        
        return 1;
    }
    
    private static void checkAFKPlayers(net.minecraft.server.MinecraftServer server) {
        FlowframeConfig config = FlowframeConfig.getInstance();
        long afkTimeoutMs = config.getAFKTimeoutMinutes() * 60 * 1000L; // Convert minutes to milliseconds
        long currentTime = System.currentTimeMillis();
        
        // Use a copy of the player list to avoid concurrent modification
        java.util.List<ServerPlayerEntity> playersCopy = new java.util.ArrayList<>(server.getPlayerManager().getPlayerList());
        java.util.List<ServerPlayerEntity> playersToKick = new java.util.ArrayList<>();
        
        for (ServerPlayerEntity player : playersCopy) {
            UUID playerId = player.getUuid();
            PlayerAFKData data = playerData.get(playerId);
            
            if (data == null) {
                // Player just joined, initialize data
                data = new PlayerAFKData(player.getYaw(), player.getPitch(), currentTime);
                playerData.put(playerId, data);
                continue;
            }
            
            // Check if camera has moved
            float currentYaw = player.getYaw();
            float currentPitch = player.getPitch();
            
            boolean cameraMovement = Math.abs(currentYaw - data.lastYaw) > 0.1f || 
                                   Math.abs(currentPitch - data.lastPitch) > 0.1f;
            
            if (cameraMovement) {
                // Player is not AFK, update their data
                data.lastYaw = currentYaw;
                data.lastPitch = currentPitch;
                data.lastActivity = currentTime;
            } else {
                // Check if player has been AFK too long
                long afkTime = currentTime - data.lastActivity;
                
                // Only check for kick if not in grace period
                if (afkTime > 0 && afkTime >= afkTimeoutMs) {
                    // Mark player to be kicked after iteration
                    playersToKick.add(player);
                }
            }
        }
        
        // Safely kick players after iteration is complete
        for (ServerPlayerEntity player : playersToKick) {
            kickPlayerForAFK(player, config.getAFKTimeoutMinutes());
            playerData.remove(player.getUuid());
        }
    }
    
    private static void kickPlayerForAFK(ServerPlayerEntity player, int timeoutMinutes) {
        // Additional safety check: Don't kick players who haven't been online for at least 1 minute
        PlayerAFKData data = playerData.get(player.getUuid());
        if (data != null) {
            long timeSinceJoin = System.currentTimeMillis() - (data.lastActivity - 30000); // Subtract grace period
            if (timeSinceJoin < 60000) { // Less than 1 minute since join
                return;
            }
        }
        
        Text kickMessage = Text.literal("§c[FLOWFRAME] You have been kicked for being AFK for " + 
                                      timeoutMinutes + " minutes.");
        
        // Schedule kick on server thread to prevent concurrent modification issues
        player.getServer().execute(() -> {
            try {
                if (player.networkHandler != null && !player.isDisconnected()) {
                    player.networkHandler.disconnect(kickMessage);
                }
            } catch (Exception ignored) {
                // Silently handle any network errors during kick
            }
        });
        
        // Send message only to operators via direct chat message
        Text operatorMessage = Text.literal("§e[FLOWFRAME] " + player.getName().getString() + 
                                           " was kicked for being AFK (" + timeoutMinutes + " minutes)");
        
        try {
            // Send directly to operators only, avoiding any broadcast system
            for (ServerPlayerEntity onlinePlayer : player.getServer().getPlayerManager().getPlayerList()) {
                if (onlinePlayer.hasPermissionLevel(2)) { // Only operators (permission level 2+)
                    onlinePlayer.sendMessageToClient(operatorMessage, false);
                }
            }
        } catch (Exception ignored) {
            // Silently handle messaging errors
        }
    }
    
    /**
     * Update player activity when they perform camera movement
     * This method should be called from a mixin when camera rotation changes
     */
    public static void updatePlayerActivity(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerAFKData data = playerData.get(playerId);
        
        if (data != null) {
            data.lastYaw = player.getYaw();
            data.lastPitch = player.getPitch();
            data.lastActivity = System.currentTimeMillis();
        }
    }
    
    private static class PlayerAFKData {
        float lastYaw;
        float lastPitch;
        long lastActivity;
        
        PlayerAFKData(float yaw, float pitch, long activity) {
            this.lastYaw = yaw;
            this.lastPitch = pitch;
            this.lastActivity = activity;
        }
    }
}