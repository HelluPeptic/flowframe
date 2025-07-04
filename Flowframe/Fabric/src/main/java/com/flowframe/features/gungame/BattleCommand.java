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
import java.util.UUID;

public class BattleCommand {
    
    public static void register() {
        // Register the main battle commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("battle")
                    .then(CommandManager.literal("boot")
                        .requires(source -> hasLuckPermsPermission(source, "flowframe.command.battle.boot"))
                        .executes(BattleCommand::bootGameHere)
                        .then(CommandManager.argument("x", IntegerArgumentType.integer())
                            .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(BattleCommand::bootGame)))))
                    .then(CommandManager.literal("join")
                        .then(CommandManager.argument("team", StringArgumentType.string())
                            .executes(BattleCommand::joinTeam)))
                    .then(CommandManager.literal("start")
                        .executes(BattleCommand::startGame)
                        .then(CommandManager.argument("rounds", IntegerArgumentType.integer(1, 100))
                            .executes(BattleCommand::startGameWithRounds)))
                    .then(CommandManager.literal("leave")
                        .executes(BattleCommand::leaveGame))
                    .then(CommandManager.literal("kick")
                        .requires(source -> source.hasPermissionLevel(2) || hasLuckPermsPermission(source, "flowframe.command.battle.kick"))
                        .then(CommandManager.argument("player", StringArgumentType.string())
                            .executes(BattleCommand::kickPlayer)))
                    .then(CommandManager.literal("shutdown")
                        .executes(BattleCommand::shutdownGame))
                    .then(CommandManager.literal("status")
                        .executes(BattleCommand::getStatus))
                    .then(CommandManager.literal("togglenotifications")
                        .executes(BattleCommand::toggleNotifications))
                    .then(CommandManager.literal("giveup")
                        .executes(BattleCommand::giveUp)))
            );
        });
    }
    
    private static int bootGameHere(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            BlockPos playerPos = player.getBlockPos();
            
            if (Battle.getInstance().bootGame(playerPos, player.getUuid())) {
                source.sendFeedback(() -> Text.literal("Battle booted successfully at your location: " + playerPos)
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to boot battle. A game might already be running."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to boot battle. Please try again."));
            return 0;
        }
    }
    
    private static int bootGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            // If not a player (console), check for admin permissions
            if (!hasLuckPermsPermission(source, "flowframe.command.battle.boot")) {
                source.sendError(Text.literal("Insufficient permissions."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to boot battle. Please try again."));
            return 0;
        }
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            int x = IntegerArgumentType.getInteger(context, "x");
            int y = IntegerArgumentType.getInteger(context, "y");
            int z = IntegerArgumentType.getInteger(context, "z");
            
            BlockPos spawnPoint = new BlockPos(x, y, z);
            
            if (Battle.getInstance().bootGame(spawnPoint, player.getUuid())) {
                source.sendFeedback(() -> Text.literal("Battle booted successfully at " + spawnPoint)
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to boot battle. A game might already be running."));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Failed to boot battle. Please try again."));
            return 0;
        }
    }
    
    private static int startGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Battle game = Battle.getInstance();
        Battle.BattleState state = game.getState();
        
        // Check if player is the battle leader (unless they have admin permissions)
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (!game.isBattleLeader(player.getUuid()) && !hasLuckPermsPermission(source, "flowframe.command.battle.start")) {
                source.sendError(Text.literal("Only the battle leader can start the game. The battle leader is the player who booted the game."));
                return 0;
            }
        } catch (Exception e) {
            // If not a player (console), check for admin permissions
            if (!hasLuckPermsPermission(source, "flowframe.command.battle.start")) {
                source.sendError(Text.literal("Insufficient permissions."));
                return 0;
            }
        }
        
        if (state == Battle.BattleState.WAITING) {
            if (game.getAvailableTeams().size() < 2) {
                source.sendError(Text.literal("At least 2 teams are required to start the game."));
                return 0;
            }
            
            if (game.startGame()) {
                source.sendFeedback(() -> Text.literal("Battle starting!")
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to start battle."));
                return 0;
            }
        } else if (state == Battle.BattleState.WAITING_NEXT_ROUND) {
            if (game.nextRound()) {
                source.sendFeedback(() -> Text.literal("Starting next round!")
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to start next round. Need at least 2 teams with players."));
                return 0;
            }
        } else {
            source.sendError(Text.literal("Cannot start game right now. Make sure a battle is waiting for players."));
            return 0;
        }
    }
    
    private static int startGameWithRounds(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int rounds = IntegerArgumentType.getInteger(context, "rounds");
        
        Battle game = Battle.getInstance();
        Battle.BattleState state = game.getState();
        
        // Check if player is the battle leader (unless they have admin permissions)
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (!game.isBattleLeader(player.getUuid()) && !hasLuckPermsPermission(source, "flowframe.command.battle.start")) {
                source.sendError(Text.literal("Only the battle leader can start the game. The battle leader is the player who booted the game."));
                return 0;
            }
        } catch (Exception e) {
            // If not a player (console), check for admin permissions
            if (!hasLuckPermsPermission(source, "flowframe.command.battle.start")) {
                source.sendError(Text.literal("Insufficient permissions."));
                return 0;
            }
        }
        
        if (state == Battle.BattleState.WAITING) {
            if (game.getAvailableTeams().size() < 2) {
                source.sendError(Text.literal("At least 2 teams are required to start the game."));
                return 0;
            }
            
            if (game.startGameWithRounds(rounds)) {
                source.sendFeedback(() -> Text.literal("Starting " + rounds + " round" + (rounds > 1 ? "s" : "") + " of battle!")
                    .formatted(Formatting.GREEN), true);
                return 1;
            } else {
                source.sendError(Text.literal("Failed to start battle."));
                return 0;
            }
        } else {
            source.sendError(Text.literal("Cannot start game. Current state: " + state.name()));
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
        
        if (Battle.getInstance().kickPlayer(targetPlayer.getUuid())) {
            source.sendFeedback(() -> Text.literal("Kicked " + playerName + " from the battle.")
                .formatted(Formatting.YELLOW), true);
            return 1;
        } else {
            source.sendError(Text.literal(playerName + " is not in the battle."));
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
        
        Battle game = Battle.getInstance();
        if (game.getState() != Battle.BattleState.WAITING && game.getState() != Battle.BattleState.WAITING_NEXT_ROUND) {
            source.sendError(Text.literal("No battle is currently accepting players."));
            return 0;
        }
        
        // Check if the color is valid
        if (!game.getAvailableTeamColors().contains(teamColor)) {
            source.sendError(Text.literal("Invalid team color! Available colors: " + 
                String.join(", ", game.getAvailableTeamColors())));
            return 0;
        }
        
        if (game.joinTeam(player, teamColor)) {
            // Get the team the player was assigned to
            BattleTeam team = game.getPlayerTeam(player.getUuid());
            if (team != null) {
                source.sendFeedback(() -> Text.literal("Joined the " + team.getDisplayName() + " team!")
                    .formatted(Formatting.GREEN), false);
            } else {
                source.sendFeedback(() -> Text.literal("Joined a team!")
                    .formatted(Formatting.GREEN), false);
            }
            return 1;
        } else {
            source.sendError(Text.literal("Failed to join team. The battle might be full or not accepting players."));
            return 0;
        }
    }
    
    private static int getStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Battle game = Battle.getInstance();
        
        source.sendFeedback(() -> Text.literal("Battle Status: " + game.getState().name())
            .formatted(Formatting.YELLOW), false);
        
        if (game.getState() != Battle.BattleState.INACTIVE) {
            // Show battle leader info
            UUID leaderId = game.getBattleLeader();
            if (leaderId != null) {
                ServerPlayerEntity leader = source.getServer().getPlayerManager().getPlayer(leaderId);
                String leaderName = leader != null ? leader.getName().getString() : "Unknown";
                source.sendFeedback(() -> Text.literal("Battle Leader: " + leaderName)
                    .formatted(Formatting.GOLD), false);
            }
            
            // Show round information
            if (game.getCurrentRound() > 0) {
                source.sendFeedback(() -> Text.literal("Round: " + game.getCurrentRound() + " of " + game.getTotalRounds())
                    .formatted(Formatting.AQUA), false);
            }
            
            source.sendFeedback(() -> Text.literal("Teams: " + game.getAvailableTeams().size())
                .formatted(Formatting.GRAY), false);
            
            for (String teamName : game.getAvailableTeams()) {
                BattleTeam team = game.getTeam(teamName);
                if (team != null) {
                    source.sendFeedback(() -> Text.literal("- ")
                        .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                        .append(Text.literal(" (" + team.getPlayerCount() + " players)"))
                        .formatted(Formatting.GRAY), false);
                }
            }
            
            // Show available colors for joining if battle is waiting
            if (game.getState() == Battle.BattleState.WAITING || game.getState() == Battle.BattleState.WAITING_NEXT_ROUND) {
                java.util.List<String> unusedColors = game.getUnusedTeamColors();
                if (!unusedColors.isEmpty()) {
                    source.sendFeedback(() -> Text.literal("Available team colors: " + String.join(", ", unusedColors))
                        .formatted(Formatting.GREEN), false);
                }
            }
        } else {
            source.sendFeedback(() -> Text.literal("No battle is currently running.")
                .formatted(Formatting.GRAY), false);
        }
        
        return 1;
    }
    
    private static int shutdownGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Battle game = Battle.getInstance();
        
        if (game.getState() == Battle.BattleState.INACTIVE) {
            source.sendError(Text.literal("No battle is currently running."));
            return 0;
        }
        
        // Check if player is the battle leader (unless they have admin permissions)
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (!game.isBattleLeader(player.getUuid()) && !hasLuckPermsPermission(source, "flowframe.command.battle.shutdown")) {
                source.sendError(Text.literal("Only the battle leader can shutdown the game. The battle leader is the player who booted the game."));
                return 0;
            }
        } catch (Exception e) {
            // If not a player (console), check for admin permissions
            if (!hasLuckPermsPermission(source, "flowframe.command.battle.shutdown")) {
                source.sendError(Text.literal("Insufficient permissions."));
                return 0;
            }
        }
        
        if (game.shutdownGame()) {
            source.sendFeedback(() -> Text.literal("Battle has been shut down. All players have been reset.")
                .formatted(Formatting.YELLOW), true);
            return 1;
        } else {
            source.sendError(Text.literal("Failed to shutdown battle."));
            return 0;
        }
    }
    
    private static int leaveGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player;
        
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }
        
        Battle game = Battle.getInstance();
        
        if (!game.isPlayerInGame(player.getUuid())) {
            source.sendError(Text.literal("You are not in a battle."));
            return 0;
        }
        
        if (game.leaveGame(player.getUuid())) {
            source.sendFeedback(() -> Text.literal("You have left the battle.")
                .formatted(Formatting.YELLOW), false);
            return 1;
        } else {
            source.sendError(Text.literal("Cannot leave the battle right now. You can only leave during waiting periods."));
            return 0;
        }
    }

    private static int toggleNotifications(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player;
        
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can toggle battle notifications."));
            return 0;
        }
        
        boolean newSetting = BattleNotificationManager.getInstance().toggleNotifications(player.getUuid());
        
        if (newSetting) {
            source.sendFeedback(() -> Text.literal("✓ Battle notifications enabled! You will receive action bar messages when battles start.")
                .formatted(Formatting.GREEN), false);
        } else {
            source.sendFeedback(() -> Text.literal("✗ Battle notifications disabled! You will no longer receive action bar messages when battles start.")
                .formatted(Formatting.YELLOW), false);
        }
        
        return 1;
    }

    private static int giveUp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the source is a player
        if (source.getPlayer() == null) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }
        
        ServerPlayerEntity player = source.getPlayer();
        UUID playerId = player.getUuid();
        Battle battle = Battle.getInstance();
        
        // Check if player is in a battle
        if (!battle.isPlayerInGame(playerId)) {
            source.sendError(Text.literal("You are not in a battle."));
            return 0;
        }
        
        // Check if battle is in a state where giving up is allowed
        Battle.BattleState state = battle.getState();
        if (state != Battle.BattleState.ACTIVE) {
            source.sendError(Text.literal("You can only give up during an active battle."));
            return 0;
        }
        
        // Check if player is already a spectator
        if (battle.isSpectator(playerId)) {
            source.sendError(Text.literal("You are already a spectator."));
            return 0;
        }
        
        // Execute the elimination (same as taking lethal damage)
        battle.eliminatePlayer(playerId);
        
        // Send confirmation message
        if (state == Battle.BattleState.ACTIVE) {
        player.sendMessage(Text.literal("You have given up and are now spectating the battle.")
            .formatted(Formatting.YELLOW), false);
            return 1;
        }
        return 1;
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        try {
            return Permissions.check(source, permission, 2);
        } catch (Exception e) {
            return source.hasPermissionLevel(2);
        }
    }
}
