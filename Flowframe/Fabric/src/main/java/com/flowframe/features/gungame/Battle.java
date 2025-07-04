package com.flowframe.features.gungame;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.AbstractTeam;
import com.flowframe.features.chatformat.TablistUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Battle {
    private static Battle instance;
    private final Map<UUID, BattleTeam> playerTeams = new ConcurrentHashMap<>();
    private final Map<String, BattleTeam> teams = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GameMode> originalGameModes = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> originalPositions = new ConcurrentHashMap<>();
    
    // Available team colors (excluding black, white, dark gray)
    private final List<String> availableColors = Arrays.asList(
        "red", "blue", "green", "yellow", "orange", "pink", 
        "purple", "aqua", "lime", "brown", "magenta", "cyan"
    );
    private final Set<String> usedColors = ConcurrentHashMap.newKeySet();
    
    private BattleState state = BattleState.INACTIVE;
    private BlockPos gameSpawnPoint;
    private MinecraftServer server;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean pvpEnabled = false;
    private UUID battleLeader; // The player who started the battle
    private int totalRounds = 1; // Total number of rounds to play
    private int currentRound = 0; // Current round number (0 = not started)
    
    public enum BattleState {
        INACTIVE,      // No game running
        WAITING,       // Game booted, waiting for players to join teams
        COUNTDOWN,     // Start countdown in progress
        GRACE_PERIOD,  // 30 second grace period
        ACTIVE,        // Game is active with PvP
        ENDING,        // Game ending sequence
        WAITING_NEXT_ROUND  // Waiting between rounds for host to start next round
    }
    
    public static Battle getInstance() {
        if (instance == null) {
            instance = new Battle();
        }
        return instance;
    }
    
    private Battle() {}
    
    public void initialize(MinecraftServer server) {
        this.server = server;
        BattleNotificationManager.getInstance().initialize(server);
    }
    
    public boolean bootGame(BlockPos spawnPoint, UUID battleLeaderId) {
        if (state != BattleState.INACTIVE) {
            return false;
        }
        
        this.gameSpawnPoint = spawnPoint;
        this.state = BattleState.WAITING;
        this.pvpEnabled = false;
        this.battleLeader = battleLeaderId; // Set the battle leader
        
        // Clear any existing data
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        resetColorAssignments();
        
        // Broadcast game start announcement using action bar messages
        // First message: "A battle has started!" - shows for 4 seconds
        broadcastActionBarToAll(Text.literal("A battle has started!")
            .formatted(Formatting.YELLOW));
        
        // Second message: "Use /flowframe battle join <team> to join a team!" - shows for 7 seconds after the first
        scheduler.schedule(() -> {
            broadcastActionBarToAll(Text.literal("Use /flowframe battle join <team> to join a team!")
                .formatted(Formatting.GREEN));
        }, 4000L, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    public boolean joinTeam(ServerPlayerEntity player, String teamColor) {
        if (state != BattleState.WAITING && state != BattleState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        // Validate that the team color is in our available colors list
        if (!availableColors.contains(teamColor.toLowerCase())) {
            return false;
        }
        
        UUID playerId = player.getUuid();
        
        // Only store original game mode and position if this is the first time joining (initial waiting state)
        if (state == BattleState.WAITING) {
            originalGameModes.put(playerId, player.interactionManager.getGameMode());
            originalPositions.put(playerId, player.getBlockPos());
        }
        
        // Remove from previous team if any
        if (playerTeams.containsKey(playerId)) {
            BattleTeam oldTeam = playerTeams.get(playerId);
            
            // Remove player from scoreboard team first
            removePlayerFromAllScoreboardTeams(player);
            
            oldTeam.removePlayer(playerId);
            if (oldTeam.getPlayerCount() == 0) {
                // Free up the color when team is removed
                String colorToFree = getColorFromFormatting(oldTeam.getFormatting());
                if (colorToFree != null) {
                    usedColors.remove(colorToFree);
                }
                teams.remove(oldTeam.getName());
                
                // Clean up the empty team's scoreboard team
                String teamName = "battle_" + oldTeam.getName().toLowerCase();
                if (server != null) {
                    Team scoreboardTeam = server.getScoreboard().getTeam(teamName);
                    if (scoreboardTeam != null) {
                        server.getScoreboard().removeTeam(scoreboardTeam);
                    }
                }
            }
        }
        
        // Normalize the team color (capitalize first letter)
        String normalizedColor = teamColor.toLowerCase();
        String teamName = normalizedColor.substring(0, 1).toUpperCase() + normalizedColor.substring(1);
        
        // Get or create team with the specified color name and matching formatting
        BattleTeam team = teams.computeIfAbsent(teamName, name -> {
            Formatting teamFormatting = getFormattingForColor(normalizedColor);
            usedColors.add(normalizedColor);
            return new BattleTeam(name, teamFormatting);
        });
        
        // Add player to team
        team.addPlayer(playerId, player.getName().getString());
        playerTeams.put(playerId, team);
        
        // Create/update scoreboard team for nametag colorization
        createScoreboardTeam(team);
        addPlayerToScoreboardTeam(player, team);
        
        // Update tablist for all players to show team changes
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast join message only to players in the battle
        broadcastToGamePlayers(Text.literal("[" + team.getDisplayName() + "] " + player.getName().getString() + " joined the game!"));
        
        return true;
    }
    
    public boolean startGame() {
        if ((state != BattleState.WAITING && state != BattleState.WAITING_NEXT_ROUND) || teams.size() < 2) {
            return false;
        }
        
        // Check if we have at least 2 teams with players
        List<BattleTeam> availableTeams = teams.values().stream()
            .filter(team -> !team.getAlivePlayers().isEmpty())
            .toList();
        
        if (availableTeams.size() < 2) {
            return false; // Need at least 2 teams with players
        }
        
        totalRounds = 1;
        currentRound = 1;
        state = BattleState.COUNTDOWN;
        startCountdown();
        return true;
    }
    
    public boolean startGameWithRounds(int rounds) {
        if ((state != BattleState.WAITING && state != BattleState.WAITING_NEXT_ROUND) || teams.size() < 2) {
            return false;
        }
        
        // Check if we have at least 2 teams with players
        List<BattleTeam> availableTeams = teams.values().stream()
            .filter(team -> !team.getAlivePlayers().isEmpty())
            .toList();
        
        if (availableTeams.size() < 2) {
            return false; // Need at least 2 teams with players
        }
        
        totalRounds = rounds;
        currentRound = 1;
        state = BattleState.COUNTDOWN;
        startCountdown();
        return true;
    }
    
    private void startCountdown() {
        for (int i = 10; i >= 1; i--) {
            final int countdown = i;
            scheduler.schedule(() -> {
                Text countdownText = Text.literal(String.valueOf(countdown))
                    .formatted(Formatting.GOLD, Formatting.BOLD);
                
                for (UUID playerId : playerTeams.keySet()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                        player.networkHandler.sendPacket(new TitleS2CPacket(countdownText));
                        player.networkHandler.sendPacket(new SubtitleS2CPacket(
                            Text.literal("Game starting...").formatted(Formatting.YELLOW)));
                    }
                }
            }, (10 - i) * 1000L, TimeUnit.MILLISECONDS);
        }
        
        // Start grace period after countdown
        scheduler.schedule(this::startGracePeriod, 10000L, TimeUnit.MILLISECONDS);
    }
    
    private void startGracePeriod() {
        state = BattleState.GRACE_PERIOD;
        
        // Only teleport players if this is the first round (original game start)
        // For subsequent rounds, players are already teleported in startNextRound
        if (currentRound == 1) {
            // Teleport all players to spawn point
            for (UUID playerId : playerTeams.keySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    ServerWorld world = player.getServerWorld(); // Use player's current world
                    player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                        gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
            }
        }
        
        // Show grace period title to all players
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Show grace period title
                Text graceText = Text.literal("Grace Period")
                    .formatted(Formatting.GREEN, Formatting.BOLD);
                Text subtitleText = Text.literal("30 seconds to prepare")
                    .formatted(Formatting.YELLOW);
                
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                player.networkHandler.sendPacket(new TitleS2CPacket(graceText));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleText));
            }
        }
        
        // Start game after grace period
        scheduler.schedule(this::startActiveGame, 30000L, TimeUnit.MILLISECONDS);
    }
    
    private void startActiveGame() {
        state = BattleState.ACTIVE;
        pvpEnabled = true;
        
        // Notify all players that PvP is now active
        Text gameStartText = Text.literal("FIGHT!")
            .formatted(Formatting.RED, Formatting.BOLD);
        
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                player.networkHandler.sendPacket(new TitleS2CPacket(gameStartText));
                
                player.sendMessage(Text.literal("PvP is now active! Good luck!")
                    .formatted(Formatting.RED, Formatting.BOLD), false);
            }
        }
    }
    
    /**
     * Public method to handle player elimination and check for game end.
     * Called from mixin when a player is eliminated in battle.
     */
    public void handlePlayerElimination() {
        if (state == BattleState.ACTIVE) {
            checkGameEnd();
        }
    }
    
    private void checkGameEnd() {
        List<BattleTeam> aliveTeams = teams.values().stream()
            .filter(team -> !team.isEmpty())
            .toList();
        
        if (aliveTeams.size() <= 1) {
            endGame(aliveTeams.isEmpty() ? null : aliveTeams.get(0));
        }
    }
    
    private void endGame(BattleTeam winningTeam) {
        state = BattleState.ENDING;
        
        // Announce winner
        if (winningTeam != null) {
            Text winMessage = Text.literal("Team ")
                .append(Text.literal(winningTeam.getDisplayName()).formatted(winningTeam.getFormatting()))
                .append(Text.literal(" wins round " + currentRound + "!"))
                .formatted(Formatting.GOLD, Formatting.BOLD);
            
            // Create a more centered title message with manual spacing
            Text titleMessage = Text.literal("")
                .append(Text.literal(winningTeam.getDisplayName()).formatted(winningTeam.getFormatting()))
                .append(Text.literal(" Wins!"))
                .formatted(Formatting.GOLD, Formatting.BOLD);
            
            broadcastToAll(winMessage);
            
            // Show title to all game participants
            for (UUID playerId : playerTeams.keySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                    player.networkHandler.sendPacket(new TitleS2CPacket(titleMessage));
                }
            }
        } else {
            broadcastToAll(Text.literal("Round " + currentRound + " ended in a draw!").formatted(Formatting.YELLOW));
        }
        
        // Check if we should continue to next round or end the game
        if (currentRound < totalRounds) {
            // Wait 3 seconds then start next round
            scheduler.schedule(this::startNextRound, 3000L, TimeUnit.MILLISECONDS);
        } else {
            // All rounds complete - wait for next game
            scheduler.schedule(this::completeBattleSeries, 3000L, TimeUnit.MILLISECONDS);
        }
    }
    
    private void completeBattleSeries() {
        state = BattleState.WAITING; // Back to waiting state for a new battle
        pvpEnabled = false;
        
        // Reset all players to survival mode and teleport to game spawn
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.changeGameMode(GameMode.SURVIVAL);
                
                // Teleport to game spawn point
                if (gameSpawnPoint != null) {
                    ServerWorld world = player.getServerWorld();
                    player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                        gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
            }
        }
        
        // Reset team alive status for next round
        for (BattleTeam team : teams.values()) {
            team.resetForNextRound();
        }
        spectators.clear();
        
        // Update tablist
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast completion message
        Text completionMessage = Text.literal("All " + totalRounds + " rounds completed! Battle series finished.")
            .formatted(Formatting.GREEN, Formatting.BOLD);
        broadcastToGamePlayers(completionMessage);
        broadcastToGamePlayers(Text.literal("Host can start a new battle or use '/flowframe battle shutdown' to end")
            .formatted(Formatting.GRAY));
        broadcastToGamePlayers(Text.literal("Players can use '/flowframe battle leave' to leave the game")
            .formatted(Formatting.GRAY));
        
        // Reset round counters for potential new game
        currentRound = 0;
        totalRounds = 1;
    }
    
    private void resetAllPlayers() {
        // Teleport all players back to their original positions and reset game modes
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Reset game mode
                GameMode originalMode = originalGameModes.getOrDefault(playerId, GameMode.SURVIVAL);
                player.changeGameMode(originalMode);
                
                // Teleport to original position if available, otherwise to game spawn
                BlockPos originalPos = originalPositions.get(playerId);
                if (originalPos != null) {
                    ServerWorld world = player.getServerWorld();
                    player.teleport(world, originalPos.getX() + 0.5, originalPos.getY(), 
                        originalPos.getZ() + 0.5, player.getYaw(), player.getPitch());
                } else if (gameSpawnPoint != null) {
                    // Fallback to game spawn
                    ServerWorld world = player.getServerWorld();
                    player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                        gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
                
                // Clear titles
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            }
        }
          // Reset game state
        state = BattleState.INACTIVE;
        pvpEnabled = false;
        battleLeader = null; // Reset battle leader when game ends
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        gameSpawnPoint = null;

        broadcastToAll(Text.literal("Battle has ended. All players have been reset.")
            .formatted(Formatting.GREEN));
    }
    
    public boolean kickPlayer(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return false;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        BattleTeam team = playerTeams.get(playerId);
        
        // Remove from team
        team.removePlayer(playerId);
        playerTeams.remove(playerId);
        spectators.remove(playerId);
        
        // Remove from scoreboard team
        if (player != null) {
            removePlayerFromAllScoreboardTeams(player);
        }
        
        // Reset player
        if (player != null) {
            GameMode originalMode = originalGameModes.getOrDefault(playerId, GameMode.SURVIVAL);
            player.changeGameMode(originalMode);
            
            BlockPos originalPos = originalPositions.get(playerId);
            if (originalPos != null) {
                ServerWorld world = player.getServerWorld();
                player.teleport(world, originalPos.getX() + 0.5, originalPos.getY(), 
                    originalPos.getZ() + 0.5, player.getYaw(), player.getPitch());
            }
            
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            player.sendMessage(Text.literal("You have been kicked from the battle.")
                .formatted(Formatting.RED), false);
        }
        
        // Clean up data
        originalGameModes.remove(playerId);
        originalPositions.remove(playerId);
        
        // Remove empty team
        if (team.isEmpty()) {
            // Free up the color when team is removed
            String colorToFree = getColorFromFormatting(team.getFormatting());
            if (colorToFree != null) {
                usedColors.remove(colorToFree);
            }
            teams.remove(team.getName());
            
            // Clean up the empty team's scoreboard team
            String teamName = "battle_" + team.getName().toLowerCase();
            if (server != null) {
                Team scoreboardTeam = server.getScoreboard().getTeam(teamName);
                if (scoreboardTeam != null) {
                    server.getScoreboard().removeTeam(scoreboardTeam);
                }
            }
        }
        
        // Update tablist for all players
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Check if game should end
        if (state == BattleState.ACTIVE) {
            checkGameEnd();
        }
        
        return true;
    }
    
    public boolean nextRound() {
        if (state != BattleState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        // Check if we have at least 2 teams with players
        List<BattleTeam> availableTeams = teams.values().stream()
            .filter(team -> !team.getAlivePlayers().isEmpty())
            .toList();
        
        if (availableTeams.size() < 2) {
            return false;
        }
        
        // Start the game again
        state = BattleState.COUNTDOWN;
        startCountdown();
        return true;
    }

    public boolean leaveGame(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return false;
        }
        
        // Only allow leaving during waiting periods or when game is not active
        if (state != BattleState.WAITING && state != BattleState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        BattleTeam team = playerTeams.get(playerId);
        
        // Remove from team
        team.removePlayer(playerId);
        playerTeams.remove(playerId);
        spectators.remove(playerId);
        
        // Remove from scoreboard team
        if (player != null) {
            removePlayerFromAllScoreboardTeams(player);
        }
        
        // Reset player to original state only if we're in the first waiting period or have their original position
        if (player != null) {
            GameMode originalMode = originalGameModes.getOrDefault(playerId, GameMode.SURVIVAL);
            player.changeGameMode(originalMode);
            
            // Only teleport to original position if it exists (from first round)
            BlockPos originalPos = originalPositions.get(playerId);
            if (originalPos != null) {
                ServerWorld world = player.getServerWorld();
                player.teleport(world, originalPos.getX() + 0.5, originalPos.getY(), 
                    originalPos.getZ() + 0.5, player.getYaw(), player.getPitch());
            }
            
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            player.sendMessage(Text.literal("You have left the battle.")
                .formatted(Formatting.YELLOW), false);
        }
        
        // Clean up data
        originalGameModes.remove(playerId);
        originalPositions.remove(playerId);
        
        // Remove empty team
        if (team.getPlayerCount() == 0) {
            // Free up the color when team is removed
            String colorToFree = getColorFromFormatting(team.getFormatting());
            if (colorToFree != null) {
                usedColors.remove(colorToFree);
            }
            teams.remove(team.getName());
            
            // Clean up the empty team's scoreboard team
            String teamName = "battle_" + team.getName().toLowerCase();
            if (server != null) {
                Team scoreboardTeam = server.getScoreboard().getTeam(teamName);
                if (scoreboardTeam != null) {
                    server.getScoreboard().removeTeam(scoreboardTeam);
                }
            }
        }
        
        // Update tablist for all players
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast leave message
        broadcastToGamePlayers(Text.literal(player != null ? player.getName().getString() : "Player" + " has left the game.")
            .formatted(Formatting.GRAY));
        
        return true;
    }
    
    public boolean shutdownGame() {
        if (state == BattleState.INACTIVE) {
            return false;
        }
        
        // Set state to ending to prevent further game actions
        state = BattleState.ENDING;
        pvpEnabled = false;
        
        // Broadcast shutdown message
        broadcastToGamePlayers(Text.literal("Game has been shut down by an administrator!")
            .formatted(Formatting.RED, Formatting.BOLD));
        
        // Reset all players immediately
        resetAllPlayersToOriginalPositions();
        
        return true;
    }
    
    private void resetAllPlayersToOriginalPositions() {
        // Reset all players back to their original positions and game modes
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Reset game mode first
                GameMode originalMode = originalGameModes.getOrDefault(playerId, GameMode.SURVIVAL);
                player.changeGameMode(originalMode);
                
                // Teleport to original position if available
                BlockPos originalPos = originalPositions.get(playerId);
                if (originalPos != null) {
                    ServerWorld world = player.getServerWorld();
                    player.teleport(world, originalPos.getX() + 0.5, originalPos.getY(), 
                        originalPos.getZ() + 0.5, player.getYaw(), player.getPitch());
                } else {
                    // Fallback to game spawn if original position is not available
                    if (gameSpawnPoint != null) {
                        ServerWorld world = player.getServerWorld();
                        player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                            gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                    }
                }
                
                // Clear titles
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            }
        }
        
        // Reset game state
        state = BattleState.INACTIVE;
        pvpEnabled = false;
        battleLeader = null; // Reset battle leader when game ends
        
        // Clean up scoreboard teams before clearing data
        cleanupScoreboardTeams();
        
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        gameSpawnPoint = null;
        resetColorAssignments();
        
        // Update tablist for all players to remove team prefixes
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        broadcastToAll(Text.literal("Battle has been shut down and all players have been reset.")
            .formatted(Formatting.GREEN));
    }

    // Missing getter methods
    public BattleState getState() {
        return state;
    }
    
    public boolean isPlayerInGame(UUID playerId) {
        return playerTeams.containsKey(playerId);
    }
    
    public BattleTeam getPlayerTeam(UUID playerId) {
        return playerTeams.get(playerId);
    }
    
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }
    
    public Set<String> getAvailableTeams() {
        return teams.keySet();
    }
    
    /**
     * Get a team by name
     * @param teamName The team name
     * @return The BattleTeam object, or null if not found
     */
    public BattleTeam getTeam(String teamName) {
        return teams.get(teamName);
    }
    
    /**
     * Check if the given player is the battle leader
     * @param playerId The UUID of the player to check
     * @return true if the player is the battle leader, false otherwise
     */
    public boolean isBattleLeader(UUID playerId) {
        return battleLeader != null && battleLeader.equals(playerId);
    }
    
    /**
     * Get the UUID of the battle leader
     * @return The battle leader's UUID, or null if no battle is active
     */
    public UUID getBattleLeader() {
        return battleLeader;
    }
    
    public int getCurrentRound() {
        return currentRound;
    }
    
    public int getTotalRounds() {
        return totalRounds;
    }
    
    /**
     * Get the set of spectators
     * @return The set of spectator UUIDs
     */
    public Set<UUID> getSpectators() {
        return spectators;
    }
    
    /**
     * Get the game spawn point
     * @return The game spawn point, or null if no game is active
     */
    public BlockPos getGameSpawnPoint() {
        return gameSpawnPoint;
    }
    
    // Missing broadcast methods
    private void broadcastToAll(Text message) {
        if (server != null) {
            server.getPlayerManager().broadcast(message, false);
        }
    }
    
    public void broadcastToGamePlayers(Text message) {
        if (server != null) {
            for (UUID playerId : playerTeams.keySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(message, false);
                }
            }
        }
    }
    
    // Broadcast action bar message to all players on the server who have notifications enabled
    private void broadcastActionBarToAll(Text message) {
        if (server != null) {
            BattleNotificationManager notificationManager = BattleNotificationManager.getInstance();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (notificationManager.shouldReceiveNotifications(player.getUuid())) {
                    player.networkHandler.sendPacket(new OverlayMessageS2CPacket(message));
                }
            }
        }
    }
    
    // Missing formatting method
    private Formatting getFormattingForColor(String color) {
        switch (color.toLowerCase()) {
            case "red":
                return Formatting.RED;
            case "blue":
                return Formatting.BLUE;
            case "green":
                return Formatting.GREEN;
            case "yellow":
                return Formatting.YELLOW;
            case "orange":
                return Formatting.GOLD;
            case "pink":
                return Formatting.LIGHT_PURPLE;
            case "purple":
                return Formatting.DARK_PURPLE;
            case "aqua":
                return Formatting.AQUA;
            case "white":
                return Formatting.WHITE;
            case "black":
                return Formatting.DARK_GRAY;
            case "brown":
                return Formatting.DARK_RED;
            case "lime":
                return Formatting.GREEN;
            case "gray":
                return Formatting.GRAY;
            case "light_blue":
                return Formatting.AQUA;
            case "magenta":
                return Formatting.LIGHT_PURPLE;
            case "cyan":
                return Formatting.DARK_AQUA;
            default:
                return Formatting.WHITE;
        }
    }

    /**
     * Reset color assignments for a new game
     */
    private void resetColorAssignments() {
        usedColors.clear();
    }

    private void startNextRound() {
        currentRound++;
        
        // Check if this was the last round
        if (currentRound > totalRounds) {
            // All rounds complete - end the battle series
            completeBattleSeries();
            return;
        }
        
        state = BattleState.WAITING_NEXT_ROUND;
        pvpEnabled = false;
        
        // Reset all players to survival mode and teleport to game spawn
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.changeGameMode(GameMode.SURVIVAL);
                
                // Teleport to game spawn point
                if (gameSpawnPoint != null) {
                    ServerWorld world = player.getServerWorld();
                    player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                        gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
            }
        }
        
        // Reset team alive status for next round
        for (BattleTeam team : teams.values()) {
            team.resetForNextRound();
        }
        spectators.clear();
        
        // Update tablist
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast next round message and auto-start
        broadcastToGamePlayers(Text.literal("Starting round " + currentRound + " of " + totalRounds + " in 5 seconds...")
            .formatted(Formatting.YELLOW));
        
        // Auto-start next round after 5 seconds
        scheduler.schedule(() -> {
            if (state == BattleState.WAITING_NEXT_ROUND) {
                state = BattleState.COUNTDOWN;
                startCountdown();
            }
        }, 5000L, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Get the color name from a Formatting enum
     * @param formatting The formatting to convert
     * @return The color name, or null if not found
     */
    private String getColorFromFormatting(Formatting formatting) {
        if (formatting == Formatting.RED) return "red";
        if (formatting == Formatting.BLUE) return "blue";
        if (formatting == Formatting.GREEN) return "green";
        if (formatting == Formatting.YELLOW) return "yellow";
        if (formatting == Formatting.GOLD) return "orange";
        if (formatting == Formatting.LIGHT_PURPLE) return "pink";
        if (formatting == Formatting.DARK_PURPLE) return "purple";
        if (formatting == Formatting.AQUA || formatting == Formatting.DARK_AQUA) return "aqua";
        if (formatting == Formatting.DARK_GREEN) return "lime";
        if (formatting == Formatting.DARK_RED) return "brown";
        if (formatting == Formatting.DARK_AQUA) return "cyan";
        return null;
    }
    
    /**
     * Get the list of available team colors
     * @return List of available color names
     */
    public List<String> getAvailableTeamColors() {
        return new ArrayList<>(availableColors);
    }
    
    /**
     * Get the list of available team colors that are not yet used
     * @return List of unused color names
     */
    public List<String> getUnusedTeamColors() {
        List<String> unused = new ArrayList<>(availableColors);
        unused.removeAll(usedColors);
        return unused;
    }
    
    // ============= SCOREBOARD TEAM MANAGEMENT FOR NAMETAGS =============
    
    /**
     * Create or update a scoreboard team for a battle team
     * @param battleTeam The battle team to create a scoreboard team for
     */
    private void createScoreboardTeam(BattleTeam battleTeam) {
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        String teamName = "battle_" + battleTeam.getName().toLowerCase();
        
        // Remove existing team if it exists
        Team existing = scoreboard.getTeam(teamName);
        if (existing != null) {
            scoreboard.removeTeam(existing);
        }
        
        // Create new team
        Team scoreboardTeam = scoreboard.addTeam(teamName);
        if (scoreboardTeam != null) {
            // Set team color for nametags
            scoreboardTeam.setColor(battleTeam.getFormatting());
            
            // Optional: Set team display name
            scoreboardTeam.setDisplayName(Text.literal(battleTeam.getDisplayName())
                .formatted(battleTeam.getFormatting()));
            
            // Optional: Set team prefix/suffix for additional formatting
            scoreboardTeam.setPrefix(Text.literal("").formatted(battleTeam.getFormatting()));
            scoreboardTeam.setSuffix(Text.literal(""));
            
            // Set team options
            scoreboardTeam.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
            scoreboardTeam.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.ALWAYS);
        }
    }
    
    /**
     * Add a player to their battle team's scoreboard team
     * @param player The player to add
     * @param battleTeam The battle team they belong to
     */
    private void addPlayerToScoreboardTeam(ServerPlayerEntity player, BattleTeam battleTeam) {
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        String teamName = "battle_" + battleTeam.getName().toLowerCase();
        Team scoreboardTeam = scoreboard.getTeam(teamName);
        
        if (scoreboardTeam != null) {
            // Remove player from any existing team first
            removePlayerFromAllScoreboardTeams(player);
            
            // Add player to the new team
            scoreboard.addPlayerToTeam(player.getName().getString(), scoreboardTeam);
        }
    }
    
    /**
     * Remove a player from all scoreboard teams
     * @param player The player to remove
     */
    private void removePlayerFromAllScoreboardTeams(ServerPlayerEntity player) {
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        String playerName = player.getName().getString();
        
        // Remove from current team if any
        Team currentTeam = scoreboard.getPlayerTeam(playerName);
        if (currentTeam != null) {
            scoreboard.removePlayerFromTeam(playerName, currentTeam);
        }
    }
    
    /**
     * Clean up all battle-related scoreboard teams
     */
    private void cleanupScoreboardTeams() {
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        
        // Remove all battle teams
        for (BattleTeam battleTeam : teams.values()) {
            String teamName = "battle_" + battleTeam.getName().toLowerCase();
            Team scoreboardTeam = scoreboard.getTeam(teamName);
            if (scoreboardTeam != null) {
                scoreboard.removeTeam(scoreboardTeam);
            }
        }
    }
    
    /**
     * Update scoreboard teams for all players in the battle
     */
    private void updateAllScoreboardTeams() {
        if (server == null) return;
        
        // Create scoreboard teams for all battle teams
        for (BattleTeam battleTeam : teams.values()) {
            createScoreboardTeam(battleTeam);
        }
        
        // Add all players to their respective scoreboard teams
        for (Map.Entry<UUID, BattleTeam> entry : playerTeams.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                addPlayerToScoreboardTeam(player, entry.getValue());
            }
        }
    }
    
    /**
     * Check if a player is currently a spectator
     * @param playerId The player's UUID
     * @return True if the player is a spectator, false otherwise
     */
    public boolean isSpectator(UUID playerId) {
        return spectators.contains(playerId);
    }
    
    /**
     * Manually eliminate a player from the battle (used for give up command)
     * @param playerId The player's UUID
     */
    public void eliminatePlayer(UUID playerId) {
        if (server == null) return;
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) return;
        
        // Use the same logic as handleBattleDeath but without damage checks
        if (state != Battle.BattleState.ACTIVE) return;
        
        if (!isPlayerInGame(playerId)) return;
        
        BattleTeam team = getPlayerTeam(playerId);
        if (team == null) return;
        
        // Add to spectators and eliminate from team
        team.eliminatePlayer(playerId);
        spectators.add(playerId);
        
        // Immediately set to spectator mode
        player.changeGameMode(GameMode.SPECTATOR);
        
        // Heal the player to full health
        player.setHealth(player.getMaxHealth());
        
        // No teleportation - let the player stay where they are as a spectator
        
        // Send elimination message
        player.sendMessage(Text.literal("You have given up and are now spectating the battle.")
            .formatted(Formatting.GRAY), false);
        
        // Broadcast elimination to all battle players
        broadcastToGamePlayers(Text.literal("[")
            .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
            .append(Text.literal("] " + player.getName().getString() + " has given up!")));
        
        // Check if team is eliminated
        if (team.isEmpty()) {
            broadcastToGamePlayers(Text.literal("Team ")
                .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                .append(Text.literal(" has been eliminated!")).formatted(Formatting.RED));
        }
        
        // Check for game end
        handlePlayerElimination();
    }
}
