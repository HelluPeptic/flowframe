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
import com.flowframe.features.chatformat.TablistUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GunGame {
    private static GunGame instance;
    private final Map<UUID, GunGameTeam> playerTeams = new ConcurrentHashMap<>();
    private final Map<String, GunGameTeam> teams = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, GameMode> originalGameModes = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> originalPositions = new ConcurrentHashMap<>();
    
    private GunGameState state = GunGameState.INACTIVE;
    private BlockPos gameSpawnPoint;
    private MinecraftServer server;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean pvpEnabled = false;
    
    public enum GunGameState {
        INACTIVE,      // No game running
        WAITING,       // Game booted, waiting for players to join teams
        COUNTDOWN,     // Start countdown in progress
        GRACE_PERIOD,  // 1 minute grace period
        ACTIVE,        // Game is active with PvP
        ENDING,        // Game ending sequence
        WAITING_NEXT_ROUND  // Waiting between rounds for host to start next round
    }
    
    public static GunGame getInstance() {
        if (instance == null) {
            instance = new GunGame();
        }
        return instance;
    }
    
    private GunGame() {}
    
    public void initialize(MinecraftServer server) {
        this.server = server;
    }
    
    public boolean bootGame(BlockPos spawnPoint) {
        if (state != GunGameState.INACTIVE) {
            return false;
        }
        
        this.gameSpawnPoint = spawnPoint;
        this.state = GunGameState.WAITING;
        this.pvpEnabled = false;
        
        // Clear any existing data
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        
        // Broadcast game start announcement
        broadcastToAll(Text.literal("A gun game has started! Use ")
            .formatted(Formatting.YELLOW)
            .append(Text.literal("/flowframe gungame join <team>")
                .formatted(Formatting.GREEN))
            .append(Text.literal(" to join a team!")
                .formatted(Formatting.YELLOW)));
        
        return true;
    }
    
    public boolean joinTeam(ServerPlayerEntity player, String teamColor) {
        if (state != GunGameState.WAITING && state != GunGameState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        // Validate team color (can't be purple or aqua)
        if (teamColor.equalsIgnoreCase("purple") || teamColor.equalsIgnoreCase("aqua")) {
            return false;
        }
        
        UUID playerId = player.getUuid();
        
        // Only store original game mode and position if this is the first time joining (initial waiting state)
        if (state == GunGameState.WAITING) {
            originalGameModes.put(playerId, player.interactionManager.getGameMode());
            originalPositions.put(playerId, player.getBlockPos());
        }
        
        // Remove from previous team if any
        if (playerTeams.containsKey(playerId)) {
            GunGameTeam oldTeam = playerTeams.get(playerId);
            oldTeam.removePlayer(playerId);
            if (oldTeam.getPlayerCount() == 0) {
                teams.remove(oldTeam.getColor());
            }
        }
        
        // Get or create team
        GunGameTeam team = teams.computeIfAbsent(teamColor.toLowerCase(), 
            color -> new GunGameTeam(color, getFormattingForColor(color)));
        
        // Add player to team
        team.addPlayer(playerId, player.getName().getString());
        playerTeams.put(playerId, team);
        
        // Update tablist for all players to show team changes
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast join message
        broadcastToAll(Text.literal("[")
            .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
            .append(Text.literal("] " + player.getName().getString() + " joined the game!")));
        
        return true;
    }
    
    public boolean startGame() {
        if ((state != GunGameState.WAITING && state != GunGameState.WAITING_NEXT_ROUND) || teams.size() < 2) {
            return false;
        }
        
        // Check if we have at least 2 teams with players
        List<GunGameTeam> availableTeams = teams.values().stream()
            .filter(team -> !team.getAlivePlayers().isEmpty())
            .toList();
        
        if (availableTeams.size() < 2) {
            return false; // Need at least 2 teams with players
        }
        
        state = GunGameState.COUNTDOWN;
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
        state = GunGameState.GRACE_PERIOD;
        
        // Teleport all players to spawn point
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                ServerWorld world = player.getServerWorld(); // Use player's current world
                player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                    gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                
                // Show grace period title
                Text graceText = Text.literal("Grace Period")
                    .formatted(Formatting.GREEN, Formatting.BOLD);
                Text subtitleText = Text.literal("1 minute to prepare")
                    .formatted(Formatting.YELLOW);
                
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                player.networkHandler.sendPacket(new TitleS2CPacket(graceText));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleText));
            }
        }
        
        // Start game after grace period
        scheduler.schedule(this::startActiveGame, 60000L, TimeUnit.MILLISECONDS);
    }
    
    private void startActiveGame() {
        state = GunGameState.ACTIVE;
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
    
    public void handlePlayerDeath(ServerPlayerEntity player) {
        if (state != GunGameState.ACTIVE) return;
        
        UUID playerId = player.getUuid();
        if (!playerTeams.containsKey(playerId)) return;
        
        System.out.println("[GUNGAME DEBUG] Player " + player.getName().getString() + " died");
        
        GunGameTeam team = playerTeams.get(playerId);
        team.eliminatePlayer(playerId);
        spectators.add(playerId);
        
        System.out.println("[GUNGAME DEBUG] Player eliminated from team " + team.getDisplayName());
        
        // Set to spectator mode
        player.changeGameMode(GameMode.SPECTATOR);
        
        // Broadcast elimination
        broadcastToGamePlayers(Text.literal("[")
            .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
            .append(Text.literal("] " + player.getName().getString() + " has been eliminated!")));
        
        // Check if team is eliminated
        if (team.isEmpty()) {
            System.out.println("[GUNGAME DEBUG] Team " + team.getDisplayName() + " is now eliminated!");
            broadcastToGamePlayers(Text.literal("Team ")
                .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                .append(Text.literal(" has been eliminated!")).formatted(Formatting.RED));
        }
        
        // Check for game end
        checkGameEnd();
    }
    
    private void checkGameEnd() {
        // Debug: Print detailed team states
        debugTeamStates();
        
        List<GunGameTeam> aliveTeams = teams.values().stream()
            .filter(team -> !team.isEmpty())
            .toList();
        
        // Debug: Log team status
        System.out.println("[GUNGAME DEBUG] Checking game end:");
        for (GunGameTeam team : teams.values()) {
            System.out.println("  Team " + team.getDisplayName() + ": " + team.getPlayerCount() + " total, " + team.getAlivePlayerCount() + " alive, isEmpty: " + team.isEmpty());
        }
        System.out.println("  Alive teams: " + aliveTeams.size());
        
        if (aliveTeams.size() <= 1) {
            System.out.println("[GUNGAME DEBUG] Game ending! Winner: " + (aliveTeams.isEmpty() ? "None" : aliveTeams.get(0).getDisplayName()));
            endGame(aliveTeams.isEmpty() ? null : aliveTeams.get(0));
        } else {
            System.out.println("[GUNGAME DEBUG] Game continues with " + aliveTeams.size() + " teams alive");
        }
    }
    
    private void endGame(GunGameTeam winningTeam) {
        state = GunGameState.ENDING;
        
        // Announce winner
        if (winningTeam != null) {
            Text winMessage = Text.literal("Team ")
                .append(Text.literal(winningTeam.getDisplayName()).formatted(winningTeam.getFormatting()))
                .append(Text.literal(" wins!")).formatted(Formatting.GOLD, Formatting.BOLD);
            
            broadcastToAll(winMessage);
            
            // Show title to all game participants
            for (UUID playerId : playerTeams.keySet()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
                    player.networkHandler.sendPacket(new TitleS2CPacket(winMessage));
                }
            }
        } else {
            broadcastToAll(Text.literal("Game ended in a draw!").formatted(Formatting.YELLOW));
        }
        
        // Wait 3 seconds then go to waiting for next round
        scheduler.schedule(this::startWaitingForNextRound, 3000L, TimeUnit.MILLISECONDS);
    }
    
    private void startWaitingForNextRound() {
        state = GunGameState.WAITING_NEXT_ROUND;
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
        for (GunGameTeam team : teams.values()) {
            team.resetForNextRound();
        }
        spectators.clear();
        
        // Update tablist
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast waiting message
        Text waitingMessage = Text.literal("Round ended! Waiting for host to start the next round...")
            .formatted(Formatting.YELLOW);
        broadcastToGamePlayers(waitingMessage);
        broadcastToGamePlayers(Text.literal("Host can use '/flowframe gungame start' to start the next round")
            .formatted(Formatting.GRAY));
        broadcastToGamePlayers(Text.literal("Players can use '/flowframe gungame leave' to leave the game")
            .formatted(Formatting.GRAY));
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
        state = GunGameState.INACTIVE;
        pvpEnabled = false;
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        gameSpawnPoint = null;
        
        broadcastToAll(Text.literal("Gun game has ended. All players have been reset.")
            .formatted(Formatting.GREEN));
    }
    
    public boolean kickPlayer(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return false;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        GunGameTeam team = playerTeams.get(playerId);
        
        // Remove from team
        team.removePlayer(playerId);
        playerTeams.remove(playerId);
        spectators.remove(playerId);
        
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
            player.sendMessage(Text.literal("You have been kicked from the gun game.")
                .formatted(Formatting.RED), false);
        }
        
        // Clean up data
        originalGameModes.remove(playerId);
        originalPositions.remove(playerId);
        
        // Remove empty team
        if (team.isEmpty()) {
            teams.remove(team.getColor());
        }
        
        // Update tablist for all players
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Check if game should end
        if (state == GunGameState.ACTIVE) {
            checkGameEnd();
        }
        
        return true;
    }
    
    public boolean nextRound() {
        if (state != GunGameState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        // Check if we have enough teams with players
        List<GunGameTeam> availableTeams = teams.values().stream()
            .filter(team -> !team.getAlivePlayers().isEmpty())
            .toList();
        
        if (availableTeams.size() < 2) {
            return false; // Need at least 2 teams
        }
        
        // Start the next round (grace period then countdown)
        startGracePeriod();
        return true;
    }
    
    public boolean leaveGame(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return false;
        }
        
        // Only allow leaving during waiting periods or when game is not active
        if (state != GunGameState.WAITING && state != GunGameState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        GunGameTeam team = playerTeams.get(playerId);
        
        // Remove from team
        team.removePlayer(playerId);
        playerTeams.remove(playerId);
        spectators.remove(playerId);
        
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
            player.sendMessage(Text.literal("You have left the gun game.")
                .formatted(Formatting.YELLOW), false);
        }
        
        // Clean up data
        originalGameModes.remove(playerId);
        originalPositions.remove(playerId);
        
        // Remove empty team
        if (team.getPlayerCount() == 0) {
            teams.remove(team.getColor());
        }
        
        // Update tablist for all players
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Broadcast leave message
        broadcastToGamePlayers(Text.literal(player != null ? player.getName().getString() : "Player" + " has left the game.")
            .formatted(Formatting.GRAY));
        
        return true;
    }
    
    public boolean shutdownGame() {
        if (state == GunGameState.INACTIVE) {
            return false;
        }
        
        // Set state to ending to prevent further game actions
        state = GunGameState.ENDING;
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
                
                // Send shutdown message to player
                player.sendMessage(Text.literal("Gun game has been shut down. You have been returned to your original position.")
                    .formatted(Formatting.YELLOW), false);
            }
        }
        
        // Reset game state
        state = GunGameState.INACTIVE;
        pvpEnabled = false;
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        gameSpawnPoint = null;
        
        // Update tablist for all players to remove team prefixes
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        broadcastToAll(Text.literal("Gun game has been shut down and all players have been reset.")
            .formatted(Formatting.GREEN));
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }
    
    public boolean isPlayerInGame(UUID playerId) {
        return playerTeams.containsKey(playerId);
    }
    
    public GunGameState getState() {
        return state;
    }
    
    public Set<String> getAvailableTeams() {
        return teams.keySet();
    }
    
    public GunGameTeam getPlayerTeam(UUID playerId) {
        return playerTeams.get(playerId);
    }
    
    private void broadcastToAll(Text message) {
        if (server != null) {
            server.getPlayerManager().broadcast(message, false);
        }
    }
    
    private void broadcastToGamePlayers(Text message) {
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message, false);
            }
        }
    }
    
    private Formatting getFormattingForColor(String color) {
        return switch (color.toLowerCase()) {
            case "red" -> Formatting.RED;
            case "blue" -> Formatting.BLUE;
            case "green" -> Formatting.GREEN;
            case "yellow" -> Formatting.YELLOW;
            case "orange" -> Formatting.GOLD;
            case "pink" -> Formatting.LIGHT_PURPLE;
            case "white" -> Formatting.WHITE;
            case "black" -> Formatting.BLACK;
            case "gray", "grey" -> Formatting.GRAY;
            case "brown" -> Formatting.DARK_RED;
            case "lime" -> Formatting.GREEN;
            case "cyan" -> Formatting.DARK_AQUA;
            default -> Formatting.WHITE;
        };
    }

    // Debug method to print all team states
    public void debugTeamStates() {
        System.out.println("[GUNGAME DEBUG] === Team States ===");
        for (GunGameTeam team : teams.values()) {
            System.out.println("Team " + team.getDisplayName() + ":");
            System.out.println("  Total players: " + team.getPlayerCount());
            System.out.println("  Alive players: " + team.getAlivePlayerCount());
            System.out.println("  Is empty: " + team.isEmpty());
            System.out.println("  All players: " + team.getAllPlayers());
            System.out.println("  Alive players: " + team.getAlivePlayers());
        }
        System.out.println("=========================");
    }
}
