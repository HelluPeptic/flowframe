package com.flowframe.features.gungame;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class GunGameCommand {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("gungame")
                    .then(CommandManager.literal("boot")
                        .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gungame.boot"))
                        .executes(GunGameCommand::bootGameHere)
                        .then(CommandManager.argument("x", IntegerArgumentType.integer())
                            .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(GunGameCommand::bootGame)))))
                    .then(CommandManager.literal("join")
                        .then(CommandManager.argument("team", StringArgumentType.string())
                            .executes(GunGameCommand::joinTeam)))
                    .then(CommandManager.literal("start")
                        .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gungame.start"))
                        .executes(GunGameCommand::startGame))
                    .then(CommandManager.literal("kick")
                        .requires(source -> source.hasPermissionLevel(2) || hasLuckPermsPermission(source, "flowframe.command.gungame.kick"))
                        .then(CommandManager.argument("player", StringArgumentType.string())
                            .executes(GunGameCommand::kickPlayer)))
                    .then(CommandManager.literal("shutdown")
                        .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gungame.shutdown"))
                        .executes(GunGameCommand::shutdownGame))
                    .then(CommandManager.literal("status")
                        .executes(GunGameCommand::getStatus)))
                .then(CommandManager.literal("join")
                    .then(CommandManager.argument("team", StringArgumentType.string())
                        .executes(GunGameCommand::joinTeam)))
            );
        });
    }
    
    private static int bootGameHere(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            BlockPos playerPos = player.getBlockPos();
            
            if (GunGame.getInstance().bootGame(playerPos)) {
                source.sendFeedback(() -> Text.literal("Gun game booted successfully at your location: " + playerPos)
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to boot gun game. A game might already be running."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Error booting gun game: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int bootGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            int x = IntegerArgumentType.getInteger(context, "x");
            int y = IntegerArgumentType.getInteger(context, "y");
            int z = IntegerArgumentType.getInteger(context, "z");
            
            BlockPos spawnPoint = new BlockPos(x, y, z);
            
            if (GunGame.getInstance().bootGame(spawnPoint)) {
                source.sendFeedback(() -> Text.literal("Gun game booted successfully at " + spawnPoint)
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to boot gun game. A game might already be running."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Error booting gun game: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int startGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        GunGame game = GunGame.getInstance();
        if (game.getState() != GunGame.GunGameState.WAITING) {
            source.sendError(Text.literal("No gun game is waiting to start."));
            return 0;
        }
        
        if (game.getAvailableTeams().size() < 2) {
            source.sendError(Text.literal("At least 2 teams are required to start the game."));
            return 0;
        }
        
        if (game.startGame()) {
            source.sendFeedback(() -> Text.literal("Gun game starting!")
                .formatted(Formatting.GREEN), true);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to start gun game."));
            return 0;
        }
    }
    
    private static int kickPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String playerName = StringArgumentType.getString(context, "player");
        
        ServerPlayerEntity targetPlayer = source.getServer().getPlayerManager().getPlayer(playerName);
        if (targetPlayer == null) {
            source.sendError(Text.literal("Player not found: " + playerName));
            return 0;
        }
        
        if (GunGame.getInstance().kickPlayer(targetPlayer.getUuid())) {
            source.sendFeedback(() -> Text.literal("Kicked " + playerName + " from the gun game.")
                .formatted(Formatting.YELLOW), true);
            return 1;
        } else {
            source.sendError(Text.literal(playerName + " is not in the gun game."));
            return 0;
        }
    }
    
    private static int joinTeam(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String teamColor = StringArgumentType.getString(context, "team").toLowerCase();
        
        ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can join teams."));
            return 0;
        }
        
        GunGame game = GunGame.getInstance();
        if (game.getState() != GunGame.GunGameState.WAITING) {
            source.sendError(Text.literal("No gun game is currently accepting players."));
            return 0;
        }
        
        // Validate color
        if (teamColor.equals("purple") || teamColor.equals("aqua")) {
            source.sendError(Text.literal("Purple and aqua teams are not allowed."));
            return 0;
        }
        
        if (game.joinTeam(player, teamColor)) {
            source.sendFeedback(() -> Text.literal("Joined team " + teamColor + "!")
                .formatted(Formatting.GREEN), false);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to join team. Make sure a game is running and accepting players."));
            return 0;
        }
    }
    
    private static int getStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        GunGame game = GunGame.getInstance();
        
        source.sendFeedback(() -> Text.literal("Gun Game Status: " + game.getState().name())
            .formatted(Formatting.YELLOW), false);
        
        if (game.getState() != GunGame.GunGameState.INACTIVE) {
            source.sendFeedback(() -> Text.literal("Teams: " + game.getAvailableTeams().size())
                .formatted(Formatting.GRAY), false);
            
            for (String teamColor : game.getAvailableTeams()) {
                source.sendFeedback(() -> Text.literal("- " + teamColor)
                    .formatted(Formatting.GRAY), false);
            }
        }
        
        return 1;
    }
    
    private static int shutdownGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        GunGame game = GunGame.getInstance();
        
        if (game.getState() == GunGame.GunGameState.INACTIVE) {
            source.sendError(Text.literal("No gun game is currently running."));
            return 0;
        }
        
        if (game.shutdownGame()) {
            source.sendFeedback(() -> Text.literal("Gun game has been shut down. All players have been reset.")
                .formatted(Formatting.YELLOW), true);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to shutdown gun game."));
            return 0;
        }
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        try {
            return Permissions.check(source, permission, 2);
        } catch (Exception e) {
            return source.hasPermissionLevel(2);
        }
    }
}
