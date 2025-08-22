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
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import com.flowframe.features.chatformat.TablistUtil;
import com.flowframe.features.gungame.DeathTrackingUtil;
import com.flowframe.FlowframeMod;

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
    private BattleMode battleMode = BattleMode.ELIMINATION; // Current battle mode
    private CaptureTheFlagManager ctfManager; // CTF manager for CTF mode
    private VillagerDefenseManager villagerDefenseManager; // Villager Defense manager
    
    // Health enforcement system
    private static final double BATTLE_BASE_HEALTH = 20.0;
    private static final UUID BATTLE_HEALTH_MODIFIER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final Map<UUID, Float> originalMaxHealth = new HashMap<>();
    
    // Speed enforcement system
    private static final double BATTLE_BASE_SPEED = 0.1; // Default player movement speed
    private static final UUID BATTLE_SPEED_MODIFIER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
    private final Map<UUID, Double> originalSpeed = new HashMap<>();
    
    // Jump boost enforcement system (using status effects)
    private static final int BATTLE_BASE_JUMP_BOOST = 0; // Default jump boost level
    private final Map<UUID, Integer> originalJumpBoost = new HashMap<>();
    
    private ScheduledExecutorService healthEnforcementService;
    
    // Static storage for CTF bases that persist across battle restarts
    private static final Map<String, BlockPos> persistentCTFBases = new ConcurrentHashMap<>();
    // Static storage for Villager Defense bases that persist across battle restarts
    private static final Map<String, BlockPos> persistentVillagerBases = new ConcurrentHashMap<>();
    
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
        return bootGame(spawnPoint, battleLeaderId, BattleMode.ELIMINATION);
    }
    
    public boolean bootGame(BlockPos spawnPoint, UUID battleLeaderId, BattleMode mode) {
        return bootGame(spawnPoint, battleLeaderId, mode, CTFMode.TIME, 5);
    }
    
    public boolean bootGame(BlockPos spawnPoint, UUID battleLeaderId, BattleMode mode, CTFMode ctfMode, int targetScore) {
        if (state != BattleState.INACTIVE) {
            return false;
        }
        
        // CRITICAL: Clean up any existing managers before creating new ones
        if (this.ctfManager != null) {
            this.ctfManager.cleanup();  // This stops all particles and cleans up everything
            this.ctfManager = null;
        }
        if (this.villagerDefenseManager != null) {
            this.villagerDefenseManager.shutdownAndClearAll();
            this.villagerDefenseManager = null;
        }
        
        this.gameSpawnPoint = spawnPoint;
        this.state = BattleState.WAITING;
        this.pvpEnabled = false;
        this.battleLeader = battleLeaderId; // Set the battle leader
        this.battleMode = mode;
        
        // Initialize CTF manager if needed
        if (mode == BattleMode.CAPTURE_THE_FLAG) {
            this.ctfManager = new CaptureTheFlagManager(this);
            this.ctfManager.setCTFMode(ctfMode);
            if (ctfMode == CTFMode.SCORE) {
                this.ctfManager.setTargetScore(targetScore);
            }
        }
        
        // Initialize Villager Defense manager if needed
        if (mode == BattleMode.VILLAGER_DEFENSE) {
            this.villagerDefenseManager = new VillagerDefenseManager(this);
        }
        
        // Clear any existing data
        playerTeams.clear();
        teams.clear();
        spectators.clear();
        originalGameModes.clear();
        originalPositions.clear();
        resetColorAssignments();
        
        // Broadcast game start announcement using action bar messages
        // First message: "A [Mode] battle has started!" - shows for 4 seconds
        broadcastActionBarToAll(Text.literal("A " + mode.getDisplayName() + " battle has started!")
            .formatted(Formatting.YELLOW));
        
        // Second message with mode description and join instructions
        scheduler.schedule(() -> {
            broadcastActionBarToAll(Text.literal(mode.getDescription())
                .formatted(Formatting.GOLD));
        }, 4000L, TimeUnit.MILLISECONDS);
        
        scheduler.schedule(() -> {
            broadcastActionBarToAll(Text.literal("Use /flowframe battle join <team> to join a team!")
                .formatted(Formatting.GREEN));
        }, 8000L, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    public boolean joinTeam(ServerPlayerEntity player, String teamColor) {
        if (state != BattleState.WAITING && state != BattleState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        // Special validation for CTF mode - only allow Red and Blue teams
        if (battleMode == BattleMode.CAPTURE_THE_FLAG) {
            if (!teamColor.equalsIgnoreCase("red") && !teamColor.equalsIgnoreCase("blue")) {
                return false;
            }
        } else {
            // Validate that the team color is in our available colors list for other modes
            if (!availableColors.contains(teamColor.toLowerCase())) {
                return false;
            }
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
        
        // Apply uniform attributes if enabled
        BattleSettings settings = BattleSettings.getInstance();
        if (settings.isUniformSpeed() || settings.isUniformJumpBoost() || settings.isUniformAttributes()) {
            // Apply attributes to this specific player
            if (settings.isUniformSpeed()) {
                enforceUniformSpeed(player);
            }
            
            if (settings.isUniformJumpBoost()) {
                player.removeStatusEffect(StatusEffects.JUMP_BOOST);
            }
            
            // Apply health enforcement if either uniformAttributes or uniformHealth is enabled
            if (settings.isUniformHealth() || settings.isUniformAttributes()) {
                enforceUniformHealth(player);
            }
            
            if (settings.isUniformAttributes()) {
                // Apply comprehensive attribute override including aggressive health enforcement
                enforceUniformHealth(player);
                player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(1.0);
                player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED).setBaseValue(4.0);
                player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1);
                
                // Clear all combat-affecting status effects
                player.removeStatusEffect(StatusEffects.SPEED);
                player.removeStatusEffect(StatusEffects.SLOWNESS);
                player.removeStatusEffect(StatusEffects.JUMP_BOOST);
                player.removeStatusEffect(StatusEffects.STRENGTH);
                player.removeStatusEffect(StatusEffects.WEAKNESS);
                player.removeStatusEffect(StatusEffects.RESISTANCE);
                player.removeStatusEffect(StatusEffects.REGENERATION);
                player.removeStatusEffect(StatusEffects.ABSORPTION);
            }
        }
        
        // Broadcast join message only to players in the battle
        broadcastToGamePlayers(Text.literal("[" + team.getDisplayName() + "] " + player.getName().getString() + " joined the game!"));
        
        // Immediately teleport player to battle location when they join a team
        teleportPlayerToBattleLocation(player, team);
        
        return true;
    }
    
    /**
     * Teleport a player to the appropriate battle location based on game mode and team
     */
    private void teleportPlayerToBattleLocation(ServerPlayerEntity player, BattleTeam team) {
        if (gameSpawnPoint == null) {
            return; // No battle location set
        }
        
        ServerWorld world = player.getServerWorld();
        
        // If joining after first match has ended (WAITING_NEXT_ROUND), always teleport to battle origin
        if (state == BattleState.WAITING_NEXT_ROUND) {
            player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("Teleported to battle location!")
                .formatted(Formatting.GREEN), false);
            return;
        }
        
        // For CTF mode in initial waiting state, try to teleport to team base if available
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null && state == BattleState.WAITING) {
            BlockPos teamBase = ctfManager.getTeamBase(team.getName());
            
            if (teamBase != null) {
                // Teleport to team base
                player.teleport(world, teamBase.getX() + 0.5, teamBase.getY() + 1.0, 
                    teamBase.getZ() + 0.5, player.getYaw(), player.getPitch());
                
                player.sendMessage(Text.literal("Teleported to " + team.getName() + " base!")
                    .formatted(Formatting.GREEN), false);
                return;
            }
        }
        
        // For other game modes or if no team base is set, teleport to battle spawn point
        player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
            gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
        
        player.sendMessage(Text.literal("Teleported to battle location!")
            .formatted(Formatting.GREEN), false);
    }
    
    public BattleMode getBattleMode() {
        return battleMode;
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
        
        // For CTF mode, validate that both Red and Blue bases are set
        // REMOVED: Skip validation - just let the game start and teleport to bases if available
          totalRounds = 1;
        currentRound = 1;
        state = BattleState.COUNTDOWN;
        
        // Initialize CTF if needed
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            ctfManager.initializeCTF(teams.keySet());
            // Note: Do not auto-setup bases here - let players set them manually with /flowframe battle ctf setbase
            // setupCTFBases(); // REMOVED: This was overriding manually set bases
        }
        
        // Initialize Villager Defense if needed
        if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
            villagerDefenseManager.initializeVillagerDefense(teams.keySet());
        }
        
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
        
        // Initialize CTF if needed
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            ctfManager.initializeCTF(teams.keySet());
            // Note: Do not auto-setup bases here - let players set them manually with /flowframe battle ctf setbase
            // setupCTFBases(); // REMOVED: This was overriding manually set bases
        }
        
        // Initialize Villager Defense if needed
        if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
            villagerDefenseManager.initializeVillagerDefense(teams.keySet());
        }
        
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
        
        // Teleport all players to their team bases immediately after countdown (before grace period)
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                BattleTeam playerTeam = playerTeams.get(playerId);
                if (playerTeam != null) {
                    // For CTF mode, teleport to team base if available
                    if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
                        BlockPos teamBase = ctfManager.getTeamBase(playerTeam.getName());
                        if (teamBase != null) {
                            ServerWorld world = player.getServerWorld();
                            player.teleport(world, teamBase.getX() + 0.5, teamBase.getY() + 1.0, 
                                teamBase.getZ() + 0.5, player.getYaw(), player.getPitch());
                            continue; // Skip battle spawn teleport
                        }
                    }
                    
                    // For Villager Defense mode, teleport to villager base if available
                    if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
                        BlockPos villagerBase = villagerDefenseManager.getVillagerBase(playerTeam.getName());
                        if (villagerBase != null) {
                            ServerWorld world = player.getServerWorld();
                            player.teleport(world, villagerBase.getX() + 0.5, villagerBase.getY() + 1.0, 
                                villagerBase.getZ() + 0.5, player.getYaw(), player.getPitch());
                            continue; // Skip battle spawn teleport
                        }
                    }
                    
                    // Fallback to battle spawn point for other game modes or if no base set
                    if (gameSpawnPoint != null) {
                        ServerWorld world = player.getServerWorld();
                        player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                            gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                    }
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
        
        // Remove villager protection when PvP starts
        if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
            villagerDefenseManager.setVillagerProtection(false);
        }
        
        // Apply uniform attributes if any are enabled
        BattleSettings settings = BattleSettings.getInstance();
        if (settings.isUniformSpeed() || settings.isUniformJumpBoost() || settings.isUniformAttributes()) {
            applyUniformAttributes();
        }
        
        // Start aggressive health and speed enforcement
        if (settings.isUniformHealth() || settings.isUniformSpeed() || settings.isUniformAttributes()) {
            enforceUniformHealthAll();
            enforceUniformSpeedAll();
            startAttributeEnforcement();
        }
        
        // Enforce health if uniform health is enabled
        if (settings.isUniformHealth()) {
            enforceUniformHealthAll();
        }
        
        // Start CTF timer if in CTF mode and ensure particles are active
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            ctfManager.startRoundTimer();
            // Ensure all base particles are active at game start
            ctfManager.ensureBaseParticlesActive();
        }
        
        // Start Villager Defense timer if in Villager Defense mode
        if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
            villagerDefenseManager.startTimedRound();
            // Protect villagers during grace period
            villagerDefenseManager.setVillagerProtection(true);
        }
        
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
        // Check for CTF win first
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            String ctfWinner = ctfManager.getWinningTeam();
            if (ctfWinner != null) {
                endGame(teams.get(ctfWinner));
                return;
            }
        }
        
        // Check for elimination win
        List<BattleTeam> aliveTeams = teams.values().stream()
            .filter(team -> !team.isEmpty())
            .toList();
        
        if (aliveTeams.size() <= 1) {
            endGame(aliveTeams.isEmpty() ? null : aliveTeams.get(0));
        }
    }
    
    public void endGame(BattleTeam winningTeam) {
        state = BattleState.ENDING;
        
        // Stop aggressive health and speed enforcement
        stopAttributeEnforcement();
        
        // Add a small delay to ensure enforcement is completely stopped before restoration
        scheduler.schedule(() -> {
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
        }, 500L, TimeUnit.MILLISECONDS); // 500ms delay to ensure enforcement service is stopped
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
        
        // Clear only flag carrier effects when battle series ends (keep bases and particles)
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            ctfManager.resetButKeepBases(); // This preserves bases AND particles
        }
        
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
        // Stop aggressive health and speed enforcement FIRST
        stopAttributeEnforcement();
        
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
                
                // Reset player speed to original (with retry logic)
                restoreOriginalSpeed(player);
                // Force another speed restoration after a small delay
                scheduler.schedule(() -> restoreOriginalSpeed(player), 100L, TimeUnit.MILLISECONDS);
                
                // Restore original health
                restoreOriginalHealth(player);
                
                // Restore original jump boost
                restoreOriginalJumpBoost(player);
                
                // Clear titles
                player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
                
                // CRITICAL: Remove from scoreboard teams to clear nametag colors
                removePlayerFromAllScoreboardTeams(player);
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
        originalMaxHealth.clear(); // Clear health tracking
        originalSpeed.clear(); // Clear speed tracking
        originalJumpBoost.clear(); // Clear jump boost tracking
        gameSpawnPoint = null;
        resetColorAssignments();
        
        // Clear persistent spectators since the battle has ended
        SpectatorPersistence.getInstance().clearAllSpectators();
        
        // Reset CTF if it was a CTF game (full shutdown - clear everything)
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            // For shutdown, completely clear everything including particles and bases
            ctfManager.shutdownAndClearAll();
            ctfManager = null;
            // Clear persistent bases too since battle is being shut down
            clearPersistentCTFBases();
        }
        
        // Reset Villager Defense if it was a Villager Defense game
        if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
            villagerDefenseManager.shutdownAndClearAll();
            villagerDefenseManager = null;
            clearPersistentVillagerBases();
        }
        
        // Clear team prefixes from tablist to prevent persistence
        TablistUtil.clearTeamPrefixes(server);
        
        // Clean up death tracking to prevent memory leaks
        DeathTrackingUtil.cleanupAllDeathTracking();
        
        broadcastToAll(Text.literal("Battle has ended. All players have been reset.")
            .formatted(Formatting.GREEN));
    }
    
    public boolean kickPlayer(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return false;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        BattleTeam team = playerTeams.get(playerId);
        
        // Check if player was a spectator (before removing them)
        boolean wasSpectator = spectators.contains(playerId);
        
        // Remove from team
        team.removePlayer(playerId);
        playerTeams.remove(playerId);
        spectators.remove(playerId);
        
        // Handle CTF cleanup if needed
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null && player != null) {
            ctfManager.handlePlayerLeave(player);
        }
        
        // Clean up death tracking for this player
        DeathTrackingUtil.cleanupDeathTracking(playerId);

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
            
            // Reset player speed to original
            restoreOriginalSpeed(player);
            
            // Restore original health
            restoreOriginalHealth(player);
            
            // Restore original jump boost
            restoreOriginalJumpBoost(player);
            
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            player.sendMessage(Text.literal("You have been kicked from the battle.")
                .formatted(Formatting.RED), false);
        }
        
        // Clean up data
        originalGameModes.remove(playerId);
        originalPositions.remove(playerId);
        originalMaxHealth.remove(playerId); // Clean up health tracking
        originalSpeed.remove(playerId); // Clean up speed tracking
        originalJumpBoost.remove(playerId); // Clean up jump boost tracking
        
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
    
    // Getter methods
    public BattleState getState() {
        return state;
    }
    
    public boolean isPlayerInGame(UUID playerId) {
        return playerTeams.containsKey(playerId);
    }
    
    public BattleTeam getPlayerTeam(UUID playerId) {
        return playerTeams.get(playerId);
    }
    
    public boolean isBattleLeader(UUID playerId) {
        return battleLeader != null && battleLeader.equals(playerId);
    }
    
    public UUID getBattleLeader() {
        return battleLeader;
    }
    
    public int getCurrentRound() {
        return currentRound;
    }
    
    public int getTotalRounds() {
        return totalRounds;
    }
    
    public List<String> getAvailableTeams() {
        return new ArrayList<>(teams.keySet());
    }
    
    public BattleTeam getTeam(String teamName) {
        return teams.get(teamName);
    }
    
    public List<String> getAvailableTeamColors() {
        return new ArrayList<>(availableColors);
    }
    
    public List<String> getUnusedTeamColors() {
        return availableColors.stream()
            .filter(color -> !usedColors.contains(color))
            .collect(java.util.stream.Collectors.toList());
    }
    
    public boolean isSpectator(UUID playerId) {
        return spectators.contains(playerId);
    }
    
    public Set<UUID> getSpectators() {
        return spectators;
    }
    
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }
    
    public MinecraftServer getServer() {
        return server;
    }
    
    public Set<UUID> getGamePlayers() {
        return new HashSet<>(playerTeams.keySet());
    }
    
    public CaptureTheFlagManager getCTFManager() {
        return ctfManager;
    }
    
    public VillagerDefenseManager getVillagerDefenseManager() {
        return villagerDefenseManager;
    }
    
    public Map<UUID, BattleTeam> getPlayerTeams() {
        return playerTeams;
    }

    // Utility methods
    private void resetColorAssignments() {
        usedColors.clear();
    }
    
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
            case "lime":
                return Formatting.GREEN;
            case "brown":
                return Formatting.GOLD;
            case "magenta":
                return Formatting.LIGHT_PURPLE;
            case "cyan":
                return Formatting.DARK_AQUA;
            default:
                return Formatting.WHITE;
        }
    }
    
    private String getColorFromFormatting(Formatting formatting) {
        switch (formatting) {
            case RED:
                return "red";
            case BLUE:
                return "blue";
            case GREEN:
                return "green";
            case YELLOW:
                return "yellow";
            case GOLD:
                return "orange";
            case LIGHT_PURPLE:
                return "pink";
            case DARK_PURPLE:
                return "purple";
            case AQUA:
                return "aqua";
            case DARK_AQUA:
                return "cyan";
            default:
                return null;
        }
    }
    
    // Broadcasting methods
    public void broadcastActionBarToAll(Text message) {
        if (server == null) return;
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new OverlayMessageS2CPacket(message));
        }
    }
    
    public void broadcastToAll(Text message) {
        if (server == null) return;
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(message, false);
        }
    }
    
    public void broadcastToGamePlayers(Text message) {
        if (server == null) return;
        
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message, false);
            }
        }
        
        // Also send to spectators
        for (UUID spectatorId : spectators) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(spectatorId);
            if (player != null) {
                player.sendMessage(message, false);
            }
        }
    }
    
    // Scoreboard team management
    private void createScoreboardTeam(BattleTeam team) {
        if (server == null) return;
        
        String teamName = "battle_" + team.getName().toLowerCase();
        Scoreboard scoreboard = server.getScoreboard();
        
        Team scoreboardTeam = scoreboard.getTeam(teamName);
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.addTeam(teamName);
        }
        
        // Set team color
        scoreboardTeam.setColor(team.getFormatting());
        scoreboardTeam.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboardTeam.setShowFriendlyInvisibles(true);
    }
    
    private void addPlayerToScoreboardTeam(ServerPlayerEntity player, BattleTeam team) {
        if (server == null) return;
        
        String teamName = "battle_" + team.getName().toLowerCase();
        Scoreboard scoreboard = server.getScoreboard();
        Team scoreboardTeam = scoreboard.getTeam(teamName);
        
        if (scoreboardTeam != null) {
            scoreboard.addPlayerToTeam(player.getEntityName(), scoreboardTeam);
        }
    }
    
    private void removePlayerFromAllScoreboardTeams(ServerPlayerEntity player) {
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        Team playerTeam = scoreboard.getPlayerTeam(player.getEntityName());
        
        if (playerTeam != null) {
            scoreboard.removePlayerFromTeam(player.getEntityName(), playerTeam);
        }
    }
    
    // Game management methods
    public boolean shutdownGame() {
        if (state == BattleState.INACTIVE) {
            return false;
        }
        
        resetAllPlayers();
        return true;
    }
    
    public boolean leaveGame(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return false;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        BattleTeam team = playerTeams.get(playerId);
        
        // Remove from persistent spectators if they were on a team
        if (team != null) {
            SpectatorPersistence.getInstance().removeSpectator(playerId);
        }
        
        // Remove from team
        team.removePlayer(playerId);
        playerTeams.remove(playerId);
        spectators.remove(playerId);
        
        // Handle CTF cleanup if needed
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null && player != null) {
            ctfManager.handlePlayerLeave(player);
        }
        
        // Clean up death tracking for this player
        DeathTrackingUtil.cleanupDeathTracking(playerId);

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
            
            // Reset player speed to original
            restoreOriginalSpeed(player);
            
            // Restore original health
            restoreOriginalHealth(player);
            
            // Restore original jump boost
            restoreOriginalJumpBoost(player);
            
            player.networkHandler.sendPacket(new ClearTitleS2CPacket(true));
            player.sendMessage(Text.literal("You have left the battle.")
                .formatted(Formatting.YELLOW), false);
        }
        
        // Clean up data
        originalGameModes.remove(playerId);
        originalPositions.remove(playerId);
        originalMaxHealth.remove(playerId); // Clean up health tracking
        originalSpeed.remove(playerId); // Clean up speed tracking
        originalJumpBoost.remove(playerId); // Clean up jump boost tracking
        
        // Remove empty team
        if (team.isEmpty()) {
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
    
    public void eliminatePlayer(UUID playerId) {
        if (!playerTeams.containsKey(playerId)) {
            return;
        }
        
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        BattleTeam team = playerTeams.get(playerId);
        
        if (team != null) {
            // Add to persistent spectators since they were on a team
            SpectatorPersistence.getInstance().addSpectator(playerId);
            
            // Remove from team and add to spectators
            team.removePlayer(playerId);
            spectators.add(playerId);
            
            // Set to spectator mode
            if (player != null) {
                player.changeGameMode(GameMode.SPECTATOR);
            }
            
            // Handle CTF specific elimination
            if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
                ctfManager.handlePlayerElimination(playerId);
            }
            
            // Update tablist to reflect team changes (spectator status)
            TablistUtil.updateTablistDisplayNamesForAll(server);
            
            // Check if game should end
            if (state == BattleState.ACTIVE) {
                checkGameEnd();
            }
        }
    }
    
    public boolean nextRound() {
        if (state != BattleState.WAITING_NEXT_ROUND) {
            return false;
        }
        
        // For CTF mode, skip validation - just start the next round
        // REMOVED: Base validation that was preventing rounds from starting
        
        startNextRound();
        return true;
    }
    
    private void startNextRound() {
        currentRound++;
        state = BattleState.COUNTDOWN;
        
        // Reset game mode specific managers
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            ctfManager.reset();
        } else if (battleMode == BattleMode.VILLAGER_DEFENSE && villagerDefenseManager != null) {
            villagerDefenseManager.reset(); // This will respawn villagers at bases
        }
        
        // Reset all players to survival mode and teleport to spawn
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.changeGameMode(GameMode.SURVIVAL);
                
                // For CTF mode, teleport players to their team bases if set, otherwise to spawn point
                if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
                    BattleTeam playerTeam = playerTeams.get(playerId);
                    if (playerTeam != null) {
                        // Try to get the team's base position
                        BlockPos teamBase = ctfManager.getTeamBase(playerTeam.getName());
                        ServerWorld world = player.getServerWorld();
                        
                        if (teamBase != null) {
                            // Teleport to team base
                            player.teleport(world, teamBase.getX() + 0.5, teamBase.getY() + 1.0, 
                                teamBase.getZ() + 0.5, player.getYaw(), player.getPitch());
                        } else {
                            // Fallback to game spawn point if base not set
                            player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                                gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                        }
                    }
                } else {
                    // For other game modes, teleport to game spawn point
                    if (gameSpawnPoint != null) {
                        ServerWorld world = player.getServerWorld();
                        player.teleport(world, gameSpawnPoint.getX() + 0.5, gameSpawnPoint.getY(), 
                            gameSpawnPoint.getZ() + 0.5, player.getYaw(), player.getPitch());
                    }
                }
            }
        }
        
        // Reset team alive status for next round
        for (BattleTeam team : teams.values()) {
            team.resetForNextRound();
        }
        spectators.clear();
        
        // Reset CTF for new round if applicable
        if (battleMode == BattleMode.CAPTURE_THE_FLAG && ctfManager != null) {
            ctfManager.resetForNewRound();
        }
        
        // Clear persistent spectators for new round
        SpectatorPersistence.getInstance().clearAllSpectators();
        
        // Update tablist
        TablistUtil.updateTablistDisplayNamesForAll(server);
        
        // Start countdown
        startCountdown();
    }
    
    /**
     * Set up CTF team bases automatically based on spawn point
     */
    private void setupCTFBases() {
        if (ctfManager == null || gameSpawnPoint == null) return;
        
        // For now, create simple bases around the spawn point
        // Red team base to the north, Blue team base to the south
        int baseDistance = 50; // Distance from spawn point
        
        BlockPos redBase = new BlockPos(gameSpawnPoint.getX(), gameSpawnPoint.getY(), gameSpawnPoint.getZ() - baseDistance);
        BlockPos blueBase = new BlockPos(gameSpawnPoint.getX(), gameSpawnPoint.getY(), gameSpawnPoint.getZ() + baseDistance);
        
        ctfManager.setFlagBase("Red", redBase);
        ctfManager.setFlagBase("Blue", blueBase);
        
        // Broadcast base locations to all players
        broadcastToGamePlayers(
            Text.literal("CTF Bases Set! Red: " + redBase.getX() + "," + redBase.getY() + "," + redBase.getZ() + 
                        " | Blue: " + blueBase.getX() + "," + blueBase.getY() + "," + blueBase.getZ())
                .formatted(Formatting.GOLD)
        );
    }

    public static boolean boot(String gameMode, BlockPos position, CTFMode ctfMode, int targetScore) {
        BattleMode mode = BattleMode.fromString(gameMode);
        if (mode == null) return false;
        
        // Use the existing UUID from the previous command context - for now just use a dummy leader
        UUID dummyLeader = UUID.randomUUID(); // TODO: Get from command context
        return getInstance().bootGame(position, dummyLeader, mode, ctfMode, targetScore);
    }



    public BlockPos getGameSpawnPoint() {
        return gameSpawnPoint;
    }

    public static void saveCTFBases(Map<String, BlockPos> bases) {
        persistentCTFBases.clear();
        persistentCTFBases.putAll(bases);
    }
    
    public static Map<String, BlockPos> getPersistentCTFBases() {
        return new HashMap<>(persistentCTFBases);
    }
    
    public static void clearPersistentCTFBases() {
        persistentCTFBases.clear();
    }

    public static void clearPersistentVillagerBases() {
        persistentVillagerBases.clear();
    }

    /**
     * Refresh player's team color to prevent nametag color persistence bugs
     * Should be called whenever player dies to ensure color stays correct
     */
    public void refreshPlayerTeamColor(ServerPlayerEntity player, BattleTeam team) {
        if (server == null || player == null || team == null) return;
        
        // Remove player from all scoreboard teams first
        removePlayerFromAllScoreboardTeams(player);
        
        // Re-add to the correct team
        addPlayerToScoreboardTeam(player, team);
        
        // Update tablist for all players to reflect the change
        TablistUtil.updateTablistDisplayNamesForAll(server);
    }
    
    /**
     * Apply uniform speed to all battle players
     */
    public void applyUniformSpeed() {
        if (server == null) return;
        
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Set movement speed to a standard value (0.1 is default)
                player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                    .setBaseValue(0.1);
            }
        }
    }
    
    /**
     * Reset player speeds to their original values
     */
    public void resetPlayerSpeeds() {
        if (server == null) return;
        
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Reset to default movement speed
                player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                    .setBaseValue(0.1); // 0.1 is the default player speed
            }
        }
    }
    
    private void resetSinglePlayerSpeed(ServerPlayerEntity player) {
        if (player != null) {
            player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                .setBaseValue(0.1); // 0.1 is the default player speed
        }
    }
    
    /**
     * Apply uniform attributes to all players based on current settings
     */
    public void applyUniformAttributes() {
        if (server == null) return;
        
        BattleSettings settings = BattleSettings.getInstance();
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Apply speed uniformity
                if (settings.isUniformSpeed()) {
                    player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                        .setBaseValue(0.1); // Standard speed
                }
                
                // Apply jump boost uniformity
                if (settings.isUniformJumpBoost()) {
                    player.removeStatusEffect(StatusEffects.JUMP_BOOST);
                    // No jump boost - everyone at base level
                }
                
                // Apply comprehensive attribute overrides
                if (settings.isUniformAttributes()) {
                    // Override all combat and movement attributes
                    player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                        .setBaseValue(20.0); // Standard vanilla health
                    player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                        .setBaseValue(1.0); // Standard attack damage
                    player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)
                        .setBaseValue(4.0); // Standard attack speed
                    player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                        .setBaseValue(0.1); // Standard movement speed
                    
                    // Set current health to match max health
                    player.setHealth(20.0f);
                    
                    // Clear all status effects that could affect combat
                    player.removeStatusEffect(StatusEffects.SPEED);
                    player.removeStatusEffect(StatusEffects.SLOWNESS);
                    player.removeStatusEffect(StatusEffects.JUMP_BOOST);
                    player.removeStatusEffect(StatusEffects.STRENGTH);
                    player.removeStatusEffect(StatusEffects.WEAKNESS);
                    player.removeStatusEffect(StatusEffects.RESISTANCE);
                    player.removeStatusEffect(StatusEffects.REGENERATION);
                    player.removeStatusEffect(StatusEffects.ABSORPTION);
                }
            }
        }
    }
    
    /**
     * Reset all player attributes to defaults (called when settings are disabled)
     */
    public void resetPlayerAttributes() {
        if (server == null) return;
        
        for (UUID playerId : playerTeams.keySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                // Reset to default values - this allows skill tree bonuses to take effect again
                player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                    .setBaseValue(0.1);
                player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                    .setBaseValue(1.0);
                player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)
                    .setBaseValue(4.0);
                player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)
                    .setBaseValue(20.0);
                
                // Heal player to full health after resetting max health
                player.setHealth(player.getMaxHealth());
                
                // Note: We don't restore status effects as those should be re-applied by skill trees
            }
        }
    }
    
    /**
     * Get team base location based on current game mode
     */
    public BlockPos getTeamBase(String teamName) {
        switch (battleMode) {
            case CAPTURE_THE_FLAG:
                return persistentCTFBases.get(teamName.toLowerCase());
            case VILLAGER_DEFENSE:
                return persistentVillagerBases.get(teamName.toLowerCase());
            default:
                return gameSpawnPoint; // Default to game spawn for elimination mode
        }
    }
    
    /**
     * Aggressive enforcement of uniform speed for a single player
     */
    public void enforceUniformSpeed(ServerPlayerEntity player) {
        try {
            BattleSettings settings = BattleSettings.getInstance();
            if (!settings.isUniformSpeed() && !settings.isUniformAttributes()) {
                return;
            }
            
            // Store original speed if not already stored
            if (!originalSpeed.containsKey(player.getUuid())) {
                EntityAttributeInstance speedAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                if (speedAttribute != null) {
                    originalSpeed.put(player.getUuid(), speedAttribute.getBaseValue());
                }
            }
            
            // Get speed attribute
            EntityAttributeInstance speedAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttribute != null) {
                // Remove all existing speed modifiers except our battle one
                List<EntityAttributeModifier> modifiers = new ArrayList<>(speedAttribute.getModifiers());
                for (EntityAttributeModifier modifier : modifiers) {
                    if (!modifier.getId().equals(BATTLE_SPEED_MODIFIER_ID)) {
                        speedAttribute.removeModifier(modifier.getId());
                    }
                }
                
                // Set base value to default
                speedAttribute.setBaseValue(BATTLE_BASE_SPEED);
                
                // Add our modifier to ensure it stays at default
                boolean hasOurModifier = speedAttribute.getModifiers().stream()
                    .anyMatch(mod -> mod.getId().equals(BATTLE_SPEED_MODIFIER_ID));
                if (!hasOurModifier) {
                    EntityAttributeModifier battleModifier = new EntityAttributeModifier(
                        BATTLE_SPEED_MODIFIER_ID,
                        "flowframe_battle_speed",
                        0.0, // No additional modification, base value handles it
                        EntityAttributeModifier.Operation.ADDITION
                    );
                    speedAttribute.addPersistentModifier(battleModifier);
                }
                
                // Force speed to exactly the base value
                player.setMovementSpeed((float) BATTLE_BASE_SPEED);
                
                // Remove speed-affecting status effects
                player.removeStatusEffect(StatusEffects.SPEED);
                player.removeStatusEffect(StatusEffects.SLOWNESS);
            }
            
        } catch (Exception e) {
            System.out.println("[FLOWFRAME] Failed to enforce uniform speed for player " + player.getName().getString() + ": " + e.getMessage());
        }
    }
    
    /**
     * Enforce uniform speed for all battle participants
     */
    public void enforceUniformSpeedAll() {
        for (BattleTeam team : teams.values()) {
            for (UUID playerId : team.getAllPlayers()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    enforceUniformSpeed(player);
                }
            }
        }
    }
    
    /**
     * Restore original speed for a single player
     */
    public void restoreOriginalSpeed(ServerPlayerEntity player) {
        try {
            EntityAttributeInstance speedAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttribute != null) {
                // Remove our battle modifier
                speedAttribute.removeModifier(BATTLE_SPEED_MODIFIER_ID);
                
                // Restore original speed
                Double originalSpeedValue = originalSpeed.get(player.getUuid());
                if (originalSpeedValue != null) {
                    speedAttribute.setBaseValue(originalSpeedValue);
                } else {
                    // Fallback to default
                    speedAttribute.setBaseValue(BATTLE_BASE_SPEED);
                }
            }
            
        } catch (Exception e) {
            System.out.println("[FLOWFRAME] Failed to restore original speed for player " + player.getName().getString() + ": " + e.getMessage());
        }
    }
    
    /**
     * Restore original speed for all battle participants
     */
    public void restoreOriginalSpeedAll() {
        for (BattleTeam team : teams.values()) {
            for (UUID playerId : team.getAllPlayers()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    restoreOriginalSpeed(player);
                }
            }
        }
    }

    /**
     * Aggressive enforcement of uniform jump boost for a single player (using status effects)
     */
    public void enforceUniformJumpBoost(ServerPlayerEntity player) {
        try {
            BattleSettings settings = BattleSettings.getInstance();
            if (!settings.isUniformJumpBoost() && !settings.isUniformAttributes()) {
                return;
            }
            
            // Store original jump boost if not already stored
            if (!originalJumpBoost.containsKey(player.getUuid())) {
                StatusEffectInstance jumpEffect = player.getStatusEffect(StatusEffects.JUMP_BOOST);
                int originalLevel = jumpEffect != null ? jumpEffect.getAmplifier() : -1; // -1 means no effect
                originalJumpBoost.put(player.getUuid(), originalLevel);
            }
            
            // Remove existing jump boost effect
            player.removeStatusEffect(StatusEffects.JUMP_BOOST);
            
            // Apply battle jump boost level (0 means no jump boost)
            if (BATTLE_BASE_JUMP_BOOST > 0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, StatusEffectInstance.INFINITE, BATTLE_BASE_JUMP_BOOST - 1, false, false));
            }
            
        } catch (Exception e) {
            System.out.println("[FLOWFRAME] Failed to enforce uniform jump boost for player " + player.getName().getString() + ": " + e.getMessage());
        }
    }
    
    /**
     * Enforce uniform jump boost for all battle participants
     */
    public void enforceUniformJumpBoostAll() {
        for (BattleTeam team : teams.values()) {
            for (UUID playerId : team.getAllPlayers()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    enforceUniformJumpBoost(player);
                }
            }
        }
    }
    
    /**
     * Restore original jump boost for a single player (using status effects)
     */
    public void restoreOriginalJumpBoost(ServerPlayerEntity player) {
        try {
            // Remove current jump boost effect
            player.removeStatusEffect(StatusEffects.JUMP_BOOST);
            
            // Restore original jump boost
            Integer originalJumpLevel = originalJumpBoost.get(player.getUuid());
            if (originalJumpLevel != null && originalJumpLevel >= 0) {
                // Restore the original jump boost effect
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, StatusEffectInstance.INFINITE, originalJumpLevel, false, false));
            }
            // If originalJumpLevel is -1 or null, we don't add any effect (no jump boost)
            
        } catch (Exception e) {
            System.out.println("[FLOWFRAME] Failed to restore original jump boost for player " + player.getName().getString() + ": " + e.getMessage());
        }
    }
    
    /**
     * Restore original jump boost for all provided players
     */
    public void restoreOriginalJumpBoostAll(Collection<ServerPlayerEntity> playersToRestore) {
        for (ServerPlayerEntity player : playersToRestore) {
            restoreOriginalJumpBoost(player);
        }
    }

    /**
     * Aggressively enforce uniform health for a specific player
     */
    public void enforceUniformHealth(ServerPlayerEntity player) {
        BattleSettings settings = BattleSettings.getInstance();
        if (!settings.isUniformHealth() && !settings.isUniformAttributes()) {
            return;
        }
        
        try {
            // Store original health if not already stored
            if (!originalMaxHealth.containsKey(player.getUuid())) {
                originalMaxHealth.put(player.getUuid(), player.getMaxHealth());
            }
            
            // Remove all existing health modifiers
            EntityAttributeInstance healthAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (healthAttribute != null) {
                // Remove all modifiers except our battle modifier
                List<EntityAttributeModifier> modifiers = new ArrayList<>(healthAttribute.getModifiers());
                for (EntityAttributeModifier modifier : modifiers) {
                    if (!modifier.getId().equals(BATTLE_HEALTH_MODIFIER_ID)) {
                        healthAttribute.removeModifier(modifier.getId());
                    }
                }
                
                // Set base value to 20
                healthAttribute.setBaseValue(BATTLE_BASE_HEALTH);
                
                // Add our modifier to ensure it stays at 20
                boolean hasOurModifier = healthAttribute.getModifiers().stream()
                    .anyMatch(mod -> mod.getId().equals(BATTLE_HEALTH_MODIFIER_ID));
                if (!hasOurModifier) {
                    EntityAttributeModifier battleModifier = new EntityAttributeModifier(
                        BATTLE_HEALTH_MODIFIER_ID,
                        "flowframe_battle_health",
                        0.0, // No additional modification, base value handles it
                        EntityAttributeModifier.Operation.ADDITION
                    );
                    healthAttribute.addPersistentModifier(battleModifier);
                }
            }
            
            // Force current health to 20
            player.setHealth(20.0f);
            
            // Remove any health-affecting status effects
            player.removeStatusEffect(StatusEffects.HEALTH_BOOST);
            player.removeStatusEffect(StatusEffects.ABSORPTION);
            player.removeStatusEffect(StatusEffects.WITHER);
            player.removeStatusEffect(StatusEffects.POISON);
            player.removeStatusEffect(StatusEffects.INSTANT_HEALTH);
            player.removeStatusEffect(StatusEffects.INSTANT_DAMAGE);
            
        } catch (Exception e) {
            System.out.println("[FLOWFRAME] Failed to enforce uniform health for player " + player.getName().getString() + ": " + e.getMessage());
        }
    }
    
    /**
     * Enforce uniform health for all battle participants
     */
    public void enforceUniformHealthAll() {
        for (BattleTeam team : teams.values()) {
            for (UUID playerId : team.getAllPlayers()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    enforceUniformHealth(player);
                }
            }
        }
    }
    
    /**
     * Restore original health for a specific player
     */
    public void restoreOriginalHealth(ServerPlayerEntity player) {
        try {
            EntityAttributeInstance healthAttribute = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (healthAttribute != null) {
                // Remove our battle modifier
                healthAttribute.removeModifier(BATTLE_HEALTH_MODIFIER_ID);
                
                // Restore original max health if we have it stored
                Float originalHealth = originalMaxHealth.get(player.getUuid());
                if (originalHealth != null) {
                    healthAttribute.setBaseValue(originalHealth);
                    // Set current health to not exceed the restored max
                    player.setHealth(Math.min(player.getHealth(), originalHealth.floatValue()));
                }
            }
        } catch (Exception e) {
            System.out.println("[FLOWFRAME] Failed to restore original health for player " + player.getName().getString() + ": " + e.getMessage());
        }
    }
    
    /**
     * Restore original health for all battle participants
     */
    public void restoreOriginalHealthAll() {
        for (BattleTeam team : teams.values()) {
            for (UUID playerId : team.getAllPlayers()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null) {
                    restoreOriginalHealth(player);
                }
            }
        }
    }
    
    /**
     * Check if a player is currently in a battle
     */
    /**
     * Start aggressive attribute enforcement (health and speed)
     */
    private void startAttributeEnforcement() {
        if (healthEnforcementService == null || healthEnforcementService.isShutdown()) {
            healthEnforcementService = Executors.newSingleThreadScheduledExecutor();
            healthEnforcementService.scheduleAtFixedRate(() -> {
                BattleSettings settings = BattleSettings.getInstance();
                if (settings.isUniformHealth() || settings.isUniformAttributes()) {
                    enforceUniformHealthAll();
                }
                if (settings.isUniformSpeed() || settings.isUniformAttributes()) {
                    enforceUniformSpeedAll();
                }
                if (settings.isUniformJumpBoost() || settings.isUniformAttributes()) {
                    enforceUniformJumpBoostAll();
                }
            }, 0, 500, TimeUnit.MILLISECONDS); // Run every 500ms for aggressive enforcement
            System.out.println("[FLOWFRAME] Started aggressive health, speed, and jump boost enforcement (every 500ms)");
        }
    }
    
    /**
     * Stop aggressive attribute enforcement
     */
    private void stopAttributeEnforcement() {
        if (healthEnforcementService != null && !healthEnforcementService.isShutdown()) {
            healthEnforcementService.shutdown();
            try {
                if (!healthEnforcementService.awaitTermination(1, TimeUnit.SECONDS)) {
                    healthEnforcementService.shutdownNow();
                }
            } catch (InterruptedException e) {
                healthEnforcementService.shutdownNow();
            }
            System.out.println("[FLOWFRAME] Stopped aggressive health, speed, and jump boost enforcement");
        }
    }

    public boolean isPlayerInBattle(ServerPlayerEntity player) {
        return playerTeams.containsKey(player.getUuid()) && state != BattleState.INACTIVE;
    }
}
