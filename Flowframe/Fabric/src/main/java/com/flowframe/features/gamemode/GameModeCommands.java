package com.flowframe.features.gamemode;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class GameModeCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Register /gm 0 (survival)
            dispatcher.register(CommandManager.literal("gm")
                .then(CommandManager.literal("0")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gm0"))
                    .executes(ctx -> executeGameModeCommand(ctx, GameMode.SURVIVAL)))
            );
            
            // Register /gm 1 (creative)
            dispatcher.register(CommandManager.literal("gm")
                .then(CommandManager.literal("1")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gm1"))
                    .executes(ctx -> executeGameModeCommand(ctx, GameMode.CREATIVE)))
            );
            
            // Register /gm 2 (adventure)
            dispatcher.register(CommandManager.literal("gm")
                .then(CommandManager.literal("2")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gm2"))
                    .executes(ctx -> executeGameModeCommand(ctx, GameMode.ADVENTURE)))
            );
            
            // Register /gm 3 (spectator)
            dispatcher.register(CommandManager.literal("gm")
                .then(CommandManager.literal("3")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gm3"))
                    .executes(ctx -> executeGameModeCommand(ctx, GameMode.SPECTATOR)))
            );
        });
    }

    private static int executeGameModeCommand(CommandContext<ServerCommandSource> context, GameMode gameMode) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player;
        
        try {
            player = source.getPlayer();
            if (player == null) {
                source.sendError(Text.literal("Only players can use this command."));
                return 1;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command."));
            return 1;
        }
        
        // Set the player's game mode
        player.changeGameMode(gameMode);
        
        // Send feedback message
        String gameModeName = getGameModeName(gameMode);
        source.sendFeedback(() -> Text.literal("Game mode changed to " + gameModeName), false);
        
        return 1;
    }
    
    private static String getGameModeName(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "Survival";
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
        };
    }

    // LuckPerms/Fabric Permissions API check helper
    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        // Allow if player has permission or is an operator (permission level 2+)
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
