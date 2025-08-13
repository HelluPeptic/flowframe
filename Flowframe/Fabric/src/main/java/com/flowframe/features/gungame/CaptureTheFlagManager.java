package com.flowframe.features.gungame;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.text.Style;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CaptureTheFlagManager {
    private final Map<String, BlockPos> flagBases = new ConcurrentHashMap<>();
    private final Map<String, UUID> flagCarriers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> flagsAtBase = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> teamFlags = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamScores = new ConcurrentHashMap<>();
    private final Map<UUID, String> carrierEffectTasks = new ConcurrentHashMap<>(); // Track effect tasks for each carrier
    private final Map<UUID, Long> respawnDelayTasks = new ConcurrentHashMap<>(); // Track respawn delays for players
    private final Set<UUID> playersInSpectatorMode = ConcurrentHashMap.newKeySet(); // Players temporarily in spectator mode
    private final Map<String, ScheduledFuture<?>> particleTasks = new ConcurrentHashMap<>(); // Track particle tasks per team
    private final int roundTimeMinutes = 10; // CTF rounds last 10 minutes
    private final int respawnDelaySeconds = 10; // Respawn delay in seconds
    private ScheduledExecutorService particleScheduler;
    private ScheduledExecutorService timerScheduler;
    private boolean roundTimerActive = false;
    private ScheduledFuture<?> flagCheckTask; // Task to check for missing flags
    
    // CTF game mode settings
    private CTFMode ctfMode = CTFMode.TIME; // Default to time-based mode
    private int targetScore = 5; // Default target score for score-based mode
    
    // CTF only supports Red and Blue teams
    private final List<String> allowedTeams = Arrays.asList("Red", "Blue");
    
    private final Battle battle;
    
    public CaptureTheFlagManager(Battle battle) {
        this.battle = battle;
        this.particleScheduler = Executors.newScheduledThreadPool(1);
        this.timerScheduler = Executors.newScheduledThreadPool(1);
        
        // Restore any persistent CTF bases that were saved from previous battles
        Map<String, BlockPos> persistentBases = Battle.getPersistentCTFBases();
        if (!persistentBases.isEmpty()) {
            flagBases.putAll(persistentBases);
            // Start particle effects for all restored bases
            for (Map.Entry<String, BlockPos> entry : persistentBases.entrySet()) {
                String teamName = entry.getKey();
                BlockPos basePos = entry.getValue();
                startBaseParticles(teamName, basePos);
                flagsAtBase.put(teamName, true); // Flag starts at base
            }
        }
    }
    
    /**
     * Initialize CTF for the given teams (only Red and Blue allowed)
     */
    public void initializeCTF(Collection<String> teamNames) {
        // DON'T clear flagBases - they should persist when manually set by players
        // flagBases.clear(); // REMOVED: This was clearing manually set bases!
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        // Initialize scores only if they don't exist yet
        if (teamScores.isEmpty()) {
            for (String teamName : teamNames) {
                teamScores.put(teamName, 0);
            }
        }
        
        // Use existing teams that match allowed teams, ensuring consistent naming
        List<String> validTeams = new ArrayList<>();
        for (String teamName : teamNames) {
            // Check if this team matches our allowed teams (case-insensitive)
            for (String allowedTeam : allowedTeams) {
                if (teamName.equalsIgnoreCase(allowedTeam)) {
                    validTeams.add(teamName); // Use the actual team name from Battle
                    break;
                }
            }
        }
        
        // Ensure we have exactly Red and Blue teams
        if (validTeams.size() != 2) {
            // If teams don't exist properly, something is wrong with initialization
            // Warn if not exactly Red/Blue teams but continue with CTF anyway
            // Use the team names as they exist in Battle, not hardcoded values
            validTeams.clear();
            for (String teamName : teamNames) {
                if (teamName.equalsIgnoreCase("red") || teamName.equalsIgnoreCase("blue")) {
                    validTeams.add(teamName);
                }
            }
        }
        
        // Initialize each team
        for (String teamName : validTeams) {
            flagsAtBase.put(teamName, true);
            teamScores.put(teamName, 0);
            
            // Create colored banner for team flag
            BattleTeam team = battle.getTeam(teamName);
            if (team != null) {
                ItemStack flag = new ItemStack(Items.WHITE_BANNER);
                
                // Set custom name for the flag
                NbtCompound nbt = flag.getOrCreateNbt();
                nbt.putString("display.Name", "{\"text\":\"" + teamName + " Flag\",\"color\":\"" + teamName.toLowerCase() + "\"}");
                
                teamFlags.put(teamName, flag);
            }
        }
        
        // Start periodic task to check for missing flags and respawn them
        if (flagCheckTask != null) {
            flagCheckTask.cancel(false);
        }
        flagCheckTask = timerScheduler.scheduleAtFixedRate(
            this::checkAndFixMissingFlags,
            10, // Initial delay of 10 seconds
            15, // Run every 15 seconds
            TimeUnit.SECONDS
        );
    }
    /**
     * Set flag base location for a team and start particle effects
     * Can only be set by team leader and only during non-active game states
     */
    public boolean setFlagBase(String teamName, BlockPos basePos, UUID playerId) {
        // Check if game is active (bases can't be moved during active games)
        if (battle.getState() == Battle.BattleState.ACTIVE) {
            return false; // Can't set base during active game
        }
        
        // Check if player is team leader
        BattleTeam team = battle.getTeam(teamName);
        if (team == null || !team.isTeamLeader(playerId)) {
            return false; // Only team leader can set base
        }
        
        // Normalize team name to match Battle's naming convention (capitalize first letter)
        String normalizedTeamName = teamName.substring(0, 1).toUpperCase() + teamName.substring(1).toLowerCase();
        
        // Stop existing particle effects for this team if they exist
        stopBaseParticles(normalizedTeamName);
        
        // Check if base already exists (replacing existing base)
        boolean isReplacing = flagBases.containsKey(normalizedTeamName);
        
        // Store with normalized name
        flagBases.put(normalizedTeamName, basePos);
        
        // CRITICAL: Always start new particles for the new base position
        startBaseParticles(normalizedTeamName, basePos);
        
        return true; // Successfully set base
    }
    
    /**
     * Legacy method for backwards compatibility - should be updated to use new method
     */
    public void setFlagBase(String teamName, BlockPos basePos) {
        // This method is kept for backwards compatibility but should be updated
        String normalizedTeamName = teamName.substring(0, 1).toUpperCase() + teamName.substring(1).toLowerCase();
        
        // Stop existing particle effects for this team if they exist
        stopBaseParticles(normalizedTeamName);
        
        flagBases.put(normalizedTeamName, basePos);
        startBaseParticles(normalizedTeamName, basePos);
    }

    /**
     * Get flag base location for a team
     */
    public BlockPos getTeamBase(String teamName) {
        // Normalize team name to match Battle's naming convention (capitalize first letter)
        String normalizedTeamName = teamName.substring(0, 1).toUpperCase() + teamName.substring(1).toLowerCase();
        
        BlockPos base = flagBases.get(normalizedTeamName);
        return base;
    }
    
    /**
     * Start continuous particle effects for a team base
     */
    private void startBaseParticles(String teamName, BlockPos basePos) {
        // Get particle color based on team
        DustParticleEffect particleEffect = getTeamParticleEffect(teamName);
        
        // Schedule repeating particle effects and store the task
        ScheduledFuture<?> particleTask = particleScheduler.scheduleAtFixedRate(() -> {
            try {
                // Get the world from a player in the battle (fallback to overworld)
                ServerWorld world = battle.getServer().getOverworld();
                
                // Try to get world from a battle participant
                for (UUID playerId : battle.getPlayerTeams().keySet()) {
                    ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        world = player.getServerWorld();
                        break;
                    }
                }
                
                // Create a 4x4 area of particles around the base
                for (int x = -2; x <= 1; x++) {
                    for (int z = -2; z <= 1; z++) {
                        double particleX = basePos.getX() + x + 0.5;
                        double particleY = basePos.getY() + 1.0;
                        double particleZ = basePos.getZ() + z + 0.5;
                        
                        // Spawn particles in a circular pattern
                        world.spawnParticles(particleEffect, 
                            particleX, particleY, particleZ,
                            2, // particle count
                            0.2, 0.1, 0.2, // spread
                            0.0 // speed
                        );
                    }
                }
            } catch (Exception e) {
                // Silently ignore errors (battle might have ended)
            }
        }, 0, 1, TimeUnit.SECONDS); // Repeat every second
        
        // Store the task so we can cancel it later
        particleTasks.put(teamName, particleTask);
    }
    
    /**
     * Stop particle effects for a specific team
     */
    private void stopBaseParticles(String teamName) {
        ScheduledFuture<?> task = particleTasks.remove(teamName);
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
        }
    }
    
    /**
     * Get colored particle effect for team
     */
    private DustParticleEffect getTeamParticleEffect(String teamName) {
        Vector3f color;
        switch (teamName.toLowerCase()) {
            case "red":
                color = new Vector3f(1.0f, 0.0f, 0.0f); // Red
                break;
            case "blue":
                color = new Vector3f(0.0f, 0.0f, 1.0f); // Blue
                break;
            default:
                color = new Vector3f(1.0f, 1.0f, 1.0f); // White fallback
        }
        return new DustParticleEffect(color, 1.0f);
    }
    
    /**
     * Handle player attempting to pick up a flag
     */
    public boolean tryPickupFlag(ServerPlayerEntity player, String flagTeam) {
        UUID playerId = player.getUuid();
        
        // CRITICAL: Prevent spectators from picking up flags
        if (player.isSpectator() || playersInSpectatorMode.contains(playerId)) {
            return false;
        }
        
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        
        if (playerTeam == null) return false;
        
        String playerTeamName = playerTeam.getName();
        
        // Can't pick up own flag
        if (playerTeamName.equals(flagTeam)) {
            // Unless it's to return it to base
            if (!flagsAtBase.get(flagTeam) && !flagCarriers.containsKey(flagTeam)) {
                returnFlagToBase(flagTeam);
                battle.broadcastToGamePlayers(
                    Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal(playerTeamName).formatted(playerTeam.getFormatting()))
                        .append(Text.literal("] " + player.getName().getString() + " returned their flag!"))
                        .formatted(Formatting.YELLOW)
                );
                return true;
            }
            return false;
        }
        
        // Check if flag is at base
        if (!flagsAtBase.get(flagTeam)) {
            player.sendMessage(Text.literal("The flag is not at the base!").formatted(Formatting.RED), false);
            return false;
        }
        
        // Check if player is already carrying a flag
        if (isPlayerCarryingFlag(playerId)) {
            player.sendMessage(Text.literal("You are already carrying a flag!").formatted(Formatting.RED), false);
            return false;
        }
        
        // Pick up the flag
        flagCarriers.put(flagTeam, playerId);
        flagsAtBase.put(flagTeam, false);
        
        // Don't give flag item to player - only glowing and text indicate flag possession
        
        // Announce flag pickup
        battle.broadcastToGamePlayers(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal(playerTeamName).formatted(playerTeam.getFormatting()))
                .append(Text.literal("] " + player.getName().getString() + " picked up the "))
                .append(Text.literal(flagTeam + " flag!").formatted(battle.getTeam(flagTeam).getFormatting()))
                .formatted(Formatting.YELLOW)
        );
        
        // Send personal message to the flag picker
        player.sendMessage(
            Text.literal("✓ You picked up the " + flagTeam + " flag! Return it to your base to score!")
                .formatted(Formatting.GREEN, Formatting.BOLD),
            true // Action bar
        );
        
        // Start visual effects for flag carrier
        startFlagCarrierEffects(player, flagTeam);
        
        // Play sound effect for flag pickup
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.5f);
        
        return true;
    }
    
    /**
     * Handle player attempting to capture a flag at their base
     */
    public boolean tryCapture(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        
        if (playerTeam == null) return false;
        
        String playerTeamName = playerTeam.getName();
        
        // Check if player is at their base
        BlockPos playerPos = player.getBlockPos();
        BlockPos teamBase = flagBases.get(playerTeamName);
        
        if (teamBase == null || playerPos.getManhattanDistance(teamBase) > 2) { // Reduced range to 2x2
            player.sendMessage(Text.literal("You must be at your base to capture!").formatted(Formatting.RED), false);
            return false;
        }
        
        // Check if their own flag is at base
        if (!flagsAtBase.get(playerTeamName)) {
            player.sendMessage(Text.literal("Your flag must be at your base to capture!").formatted(Formatting.RED), false);
            return false;
        }
        
        // Find which flag they are carrying
        String carriedFlag = null;
        for (Map.Entry<String, UUID> entry : flagCarriers.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                carriedFlag = entry.getKey();
                break;
            }
        }
        
        if (carriedFlag == null) {
            player.sendMessage(Text.literal("You are not carrying a flag!").formatted(Formatting.RED), false);
            return false;
        }
        
        // Capture the flag!
        flagCarriers.remove(carriedFlag);
        flagsAtBase.put(carriedFlag, true);
        
        // No need to remove flag from inventory since no banner item is given
        
        // Increase score
        int newScore = teamScores.get(playerTeamName) + 1;
        teamScores.put(playerTeamName, newScore);
        
        // Announce capture
        battle.broadcastToGamePlayers(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal(playerTeamName).formatted(playerTeam.getFormatting()))
                .append(Text.literal("] " + player.getName().getString() + " captured the "))
                .append(Text.literal(carriedFlag + " flag!").formatted(battle.getTeam(carriedFlag).getFormatting()))
                .append(Text.literal(" Score: " + newScore))
                .formatted(Formatting.GOLD)
        );
        
        // No win condition - game continues until timer expires
        return false;
    }
    
    /**
     * Return a flag to its base
     */
    private void returnFlagToBase(String flagTeam) {
        flagsAtBase.put(flagTeam, true);
        flagCarriers.remove(flagTeam);
    }
    
    /**
     * Check if player is carrying any flag
     */
    private boolean isPlayerCarryingFlag(UUID playerId) {
        return flagCarriers.containsValue(playerId);
    }
    
    /**
     * Get team scores
     */
    public Map<String, Integer> getTeamScores() {
        return new HashMap<>(teamScores);
    }
    
    /**
     * Get winning team (null if no winner yet)
     * Behavior depends on CTF mode: score-based checks target score, time-based waits for timer
     */
    public String getWinningTeam() {
        if (ctfMode == CTFMode.SCORE) {
            // Score-based mode: check if any team reached target score
            for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
                if (entry.getValue() >= targetScore) {
                    return entry.getKey(); // This team wins
                }
            }
        }
        // Time-based mode or no team reached target yet
        return null;
    }
    
    /**
     * Reset CTF state for new round
     */
    public void resetForNewRound() {
        // Stop any existing timer
        stopRoundTimer();
        
        // Return all flags to base
        for (String team : flagBases.keySet()) {
            flagsAtBase.put(team, true);
        }
        flagCarriers.clear();
        
        // Clear flag carrier effects
        clearAllFlagCarrierEffects();
        
        // Clear respawn delays and restore players from spectator mode
        clearAllRespawnDelays();
        
        // Restart particle effects for all existing bases
        restartAllBaseParticles();
        
        // Don't reset scores - they persist across rounds
        
        // Start new round timer when battle is active
        if (battle.getState() == Battle.BattleState.ACTIVE) {
            startRoundTimer();
        }
    }
    
    /**
     * Reset CTF state completely
     */
    public void reset() {
        // Stop all particle effects
        stopAllParticles();
        
        // Clear all flag carrier effects
        clearAllFlagCarrierEffects();
        
        // Clear respawn delays and restore players from spectator mode
        clearAllRespawnDelays();
        
        // Clear ALL CTF state including bases (unlike reset() which preserves bases)
        flagBases.clear();
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
        
        // Clean up any remaining CTF glow teams from scoreboard
        cleanupCTFGlowTeams();
        
        // Shutdown schedulers
        if (particleScheduler != null && !particleScheduler.isShutdown()) {
            particleScheduler.shutdownNow();
        }
        if (timerScheduler != null && !timerScheduler.isShutdown()) {
            timerScheduler.shutdownNow();
        }
    }
    
    /**
     * Reset CTF state but preserve bases and their particles (for battle series end)
     */
    public void resetButKeepBases() {
        // Clear all flag carrier effects
        clearAllFlagCarrierEffects();
        
        // Clear respawn delays and restore players from spectator mode
        clearAllRespawnDelays();
        
        // DON'T stop particles or clear bases - they should persist
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
        
        // Return all flags to their bases
        for (String team : flagBases.keySet()) {
            flagsAtBase.put(team, true);
        }
    }
    
    /**
     * Stop all particle effects
     */
    public void stopAllParticles() {
        // Cancel all individual particle tasks
        for (ScheduledFuture<?> task : particleTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel(false);
            }
        }
        particleTasks.clear();
        
        // Shutdown and recreate the scheduler
        if (particleScheduler != null && !particleScheduler.isShutdown()) {
            particleScheduler.shutdownNow();
            particleScheduler = Executors.newScheduledThreadPool(1);
        }
    }
    
    /**
     * Clear all flag carrier effects for all players
     */
    private void clearAllFlagCarrierEffects() {
        // Clear flag carrier effects for all players
        for (UUID playerId : new HashSet<>(carrierEffectTasks.keySet())) {
            ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null) {
                stopFlagCarrierEffects(player);
            }
        }
    }
    
    /**
     * Clear all respawn delays and restore players from spectator mode
     */
    private void clearAllRespawnDelays() {
        // Restore all players from spectator mode
        for (UUID playerId : new HashSet<>(playersInSpectatorMode)) {
            ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null && player.isSpectator()) {
                player.changeGameMode(GameMode.SURVIVAL);
            }
        }
        
        // Clear tracking data
        respawnDelayTasks.clear();
        playersInSpectatorMode.clear();
    }
    
    /**
     * Get allowed teams for CTF
     */
    public List<String> getAllowedTeams() {
        return new ArrayList<>(allowedTeams);
    }
    
    /**
     * Get flag status for display
     */
    public String getFlagStatus() {
        StringBuilder status = new StringBuilder();
        for (String team : flagBases.keySet()) {
            BattleTeam battleTeam = battle.getTeam(team);
            if (battleTeam != null) {
                status.append("[").append(team).append(": ");
                if (flagsAtBase.get(team)) {
                    status.append("At Base");
                } else {
                    UUID carrier = flagCarriers.get(team);
                    if (carrier != null) {
                        ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(carrier);
                        if (player != null) {
                            String playerName = player.getName().getString();
                            status.append("Carried by ").append(playerName);
                        } else {
                            // Player is offline/disconnected but still marked as carrier
                            status.append("Missing (fixing...)");
                            respawnMissingFlag(team);
                        }
                    } else {
                        // Flag is neither at base nor carried - it's missing
                        status.append("Missing (fixing...)");
                        respawnMissingFlag(team);
                    }
                }
                status.append("] ");
            }
        }
        return status.toString();
    }
    
    /**
     * Check for and fix missing flags automatically
     */
    public void checkAndFixMissingFlags() {
        for (String team : flagBases.keySet()) {
            if (!flagsAtBase.getOrDefault(team, true)) {
                UUID carrier = flagCarriers.get(team);
                if (carrier != null) {
                    // Check if the carrier is still online and in the game
                    ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(carrier);
                    if (player == null || !battle.isPlayerInGame(carrier)) {
                        // Player is offline or not in game, respawn flag
                        respawnMissingFlag(team);
                    }
                } else {
                    // No carrier but flag not at base - respawn it
                    respawnMissingFlag(team);
                }
            }
        }
    }
    
    /**
     * Respawn a missing flag back to its base
     */
    private void respawnMissingFlag(String flagTeam) {
        flagsAtBase.put(flagTeam, true);
        flagCarriers.remove(flagTeam);
        
        // Notify all players
        BattleTeam team = battle.getTeam(flagTeam);
        if (team != null) {
            battle.broadcastToGamePlayers(
                Text.literal("[CTF] ").formatted(Formatting.GRAY)
                    .append(Text.literal(flagTeam).formatted(team.getFormatting()))
                    .append(Text.literal(" flag has been returned to base!"))
                    .formatted(Formatting.YELLOW)
            );
        }
    }
    
    /**
     * Handle player movement - check for automatic flag pickup and capture
     */
    public void handlePlayerMovement(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        
        // CRITICAL: Prevent spectators from any CTF interactions
        if (player.isSpectator() || playersInSpectatorMode.contains(playerId)) {
            return;
        }
        
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        if (playerTeam == null) return;
        
        String playerTeamName = playerTeam.getName();
        BlockPos playerPos = player.getBlockPos();
        
        // Check for automatic flag capture at own base
        if (isPlayerCarryingFlag(playerId)) {
            BlockPos ownBase = flagBases.get(playerTeamName);
            if (ownBase != null) {
                double distance = Math.sqrt(playerPos.getSquaredDistance(ownBase));
                
                
                if (distance <= 2.0) { // Reduced capture range to match pickup range
                    if (tryAutoCapture(player)) {
                        return; // Successfully captured, no need to check pickup
                    }
                }
            }
        }
        
        // Check for automatic flag pickup at enemy bases
        for (String teamName : flagBases.keySet()) {
            if (!teamName.equals(playerTeamName)) {
                BlockPos enemyBase = flagBases.get(teamName);
                if (enemyBase != null) {
                    double distance = Math.sqrt(playerPos.getSquaredDistance(enemyBase));
                    
                    if (distance <= 2.0) { // Reduced pickup range to 2x2 as requested
                        if (tryAutoPickup(player, teamName)) {
                            break; // Only try one pickup per tick
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Automatic flag pickup when player is near enemy base
     */
    private boolean tryAutoPickup(ServerPlayerEntity player, String flagTeam) {
        UUID playerId = player.getUuid();
        
        // CRITICAL: Prevent spectators from auto-picking up flags
        if (player.isSpectator() || playersInSpectatorMode.contains(playerId)) {
            return false;
        }
        
        // Check conditions
        boolean flagAtBase = flagsAtBase.getOrDefault(flagTeam, true);
        boolean playerCarryingFlag = isPlayerCarryingFlag(playerId);
        
        // Check if flag is at base and player can pick it up
        if (!flagAtBase || playerCarryingFlag) {
            return false;
        }
        
        // Pick up the flag
        flagCarriers.put(flagTeam, playerId);
        flagsAtBase.put(flagTeam, false);
        
        // Don't give flag item to player - only glowing and text indicate flag possession
        
        // Get player team for announcement
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        if (playerTeam != null) {
            // Announce flag pickup
            battle.broadcastToGamePlayers(
                Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal(playerTeam.getName()).formatted(playerTeam.getFormatting()))
                    .append(Text.literal("] " + player.getName().getString() + " picked up the "))
                    .append(Text.literal(flagTeam + " flag!").formatted(battle.getTeam(flagTeam).getFormatting()))
                    .formatted(Formatting.YELLOW)
            );
            
            // Send personal message
            player.sendMessage(
                Text.literal("✓ You picked up the " + flagTeam + " flag! Return it to your base to score!")
                    .formatted(Formatting.GREEN, Formatting.BOLD),
                true // Action bar
            );
        }
        
        // Start visual effects for flag carrier
        startFlagCarrierEffects(player, flagTeam);
        
        return true;
    }
    
    /**
     * Automatic flag capture when player returns to base with enemy flag
     */
    private boolean tryAutoCapture(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        
        // CRITICAL: Prevent spectators from capturing flags
        if (player.isSpectator() || playersInSpectatorMode.contains(playerId)) {
            return false;
        }
        
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        if (playerTeam == null) return false;
        
        String playerTeamName = playerTeam.getName();
        
        // Check if their own flag is at base
        if (!flagsAtBase.get(playerTeamName)) {
            player.sendMessage(
                Text.literal("Your flag must be at your base to capture!")
                    .formatted(Formatting.RED),
                true // Action bar
            );
            return false;
        }
        
        // Find which flag they are carrying
        String carriedFlag = null;
        for (Map.Entry<String, UUID> entry : flagCarriers.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                carriedFlag = entry.getKey();
                break;
            }
        }
        
        if (carriedFlag == null) return false;
        
        // Capture the flag!
        flagCarriers.remove(carriedFlag);
        flagsAtBase.put(carriedFlag, true);
        
        
        // No need to remove flag from inventory since no banner item is given
        
        // Stop visual effects
        stopFlagCarrierEffects(player);
        
        // Increase score
        int newScore = teamScores.get(playerTeamName) + 1;
        teamScores.put(playerTeamName, newScore);
        
        // Announce capture
        battle.broadcastToGamePlayers(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal(playerTeamName).formatted(playerTeam.getFormatting()))
                .append(Text.literal("] " + player.getName().getString() + " captured the "))
                .append(Text.literal(carriedFlag + " flag!").formatted(battle.getTeam(carriedFlag).getFormatting()))
                .append(Text.literal(" Score: " + newScore))
                .formatted(Formatting.GOLD, Formatting.BOLD)
        );
        
        // Send personal message
        player.sendMessage(
            Text.literal("FLAG CAPTURED! +" + 1 + " point for " + playerTeamName + " team!")
                .formatted(Formatting.GOLD, Formatting.BOLD),
            true // Action bar
        );
        
        // Play sound effect for flag capture
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.2f);
        
        // Update scoreboard for all players
        updateScoreboardForAllPlayers();
        
        // Check for immediate win in score-based mode
        if (ctfMode == CTFMode.SCORE && newScore >= targetScore) {
            // This team reached the target score - they win!
            BattleTeam team = battle.getTeam(playerTeamName);
            if (team != null) {
                battle.broadcastToGamePlayers(
                    Text.literal("🏆 ")
                        .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                        .append(Text.literal(" wins by reaching " + targetScore + " points!"))
                        .formatted(Formatting.GOLD, Formatting.BOLD)
                );
                
                // End the game immediately
                battle.endGame(team);
                return true; // Game ended
            }
        }
        
        // Game continues (time-based mode or target not reached yet)
        return false;
    }
      /**
     * Start visual effects for flag carrier
     */
    private void startFlagCarrierEffects(ServerPlayerEntity player, String flagTeam) {
        UUID playerId = player.getUuid();
        
        // Only stop effects if player is already being tracked, to avoid removing effects we just added
        if (carrierEffectTasks.containsKey(playerId)) {
            stopFlagCarrierEffects(player);
        }

        DustParticleEffect particleEffect = getTeamParticleEffect(flagTeam);
        
        // Add glowing effect using a temporary scoreboard team with flag color
        // This creates colored glowing based on the flag the player is carrying
        addColoredGlowing(player, flagTeam);
        
        String taskId = "carrier_" + playerId.toString();
        ScheduledFuture<?> task = particleScheduler.scheduleAtFixedRate(() -> {
            try {
                if (player.isRemoved() || !flagCarriers.containsKey(flagTeam) || 
                    !flagCarriers.get(flagTeam).equals(playerId)) {
                    stopFlagCarrierEffects(player);
                    return;
                }
                
                // Refresh glowing effect to prevent it from expiring 
                // Re-apply glowing effect every few seconds to ensure it stays active
                addColoredGlowing(player, flagTeam);
                
                ServerWorld world = player.getServerWorld();
                Vec3d pos = player.getPos();
                
                // Create enhanced colored particle effects around the player to indicate flag color
                for (int i = 0; i < 12; i++) { // More particles for better visibility
                    double offsetX = (Math.random() - 0.5) * 1.5;
                    double offsetY = Math.random() * 2.0;
                    double offsetZ = (Math.random() - 0.5) * 1.5;
                    double x = pos.x + offsetX;
                    double y = pos.y + offsetY;
                    double z = pos.z + offsetZ;
                    world.spawnParticles(particleEffect, x, y, z, 1, 0.05, 0.05, 0.05, 0);
                }
                
                // Create a ring of colored particles around the player for better visibility
                double radius = 1.0;
                for (int i = 0; i < 8; i++) {
                    double angle = (i * Math.PI * 2) / 8;
                    double x = pos.x + Math.cos(angle) * radius;
                    double y = pos.y + 1.0;
                    double z = pos.z + Math.sin(angle) * radius;
                    world.spawnParticles(particleEffect, x, y, z, 1, 0.0, 0.0, 0.0, 0);
                }
                
                // Show carrier status to other players
                BattleTeam carrierTeam = battle.getPlayerTeam(playerId);
                if (carrierTeam != null) {
                    Text title = Text.literal(player.getName().getString())
                        .formatted(carrierTeam.getFormatting())
                        .append(Text.literal(" has the ").formatted(Formatting.WHITE))
                        .append(Text.literal(flagTeam + " flag!").formatted(battle.getTeam(flagTeam).getFormatting()));
                    
                    for (UUID otherPlayerId : battle.getGamePlayers()) {
                        if (!otherPlayerId.equals(playerId)) {
                            ServerPlayerEntity otherPlayer = player.getServer().getPlayerManager().getPlayer(otherPlayerId);
                            if (otherPlayer != null) {
                                otherPlayer.sendMessage(title, true);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Silently handle any errors to prevent task crashes
            }
        }, 0, 1, TimeUnit.SECONDS);
        
        carrierEffectTasks.put(playerId, taskId);
    }
    
    /**
     * Stop visual effects for flag carrier
     */
    private void stopFlagCarrierEffects(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        String taskId = carrierEffectTasks.remove(playerId);
        
        // Remove colored glowing effect by restoring original team assignment
        removeColoredGlowing(player);
        
        if (taskId != null) {
            // The task will stop itself when it detects the player is no longer carrying a flag
            // We just need to remove it from our tracking
        }
        
        // Clear action bar for all players
        for (UUID otherPlayerId : battle.getGamePlayers()) {
            ServerPlayerEntity otherPlayer = player.getServer().getPlayerManager().getPlayer(otherPlayerId);
            if (otherPlayer != null) {
                otherPlayer.sendMessage(Text.literal(""), true); // Clear action bar
            }
        }
    }
    
    /**
     * Handle player leaving the game (disconnect, elimination, etc.)
     */
    public void handlePlayerLeave(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        
        // Stop any visual effects
        stopFlagCarrierEffects(player);
        
        // Find if player was carrying a flag
        String carriedFlag = null;
        for (Map.Entry<String, UUID> entry : flagCarriers.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                carriedFlag = entry.getKey();
                break;
            }
        }
        
        if (carriedFlag != null) {
            // Return flag to base
            flagCarriers.remove(carriedFlag);
            flagsAtBase.put(carriedFlag, true);
            
            // No need to remove flag from inventory since no banner item is given
            
            // Announce flag return
            battle.broadcastToGamePlayers(
                Text.literal("The " + carriedFlag + " flag was returned to base!")
                    .formatted(battle.getTeam(carriedFlag).getFormatting())
            );
            
            // Personal message to returning player if they caused the return
            if (player != null) {
                player.sendMessage(
                    Text.literal("⚠ You dropped the " + carriedFlag + " flag! It has been returned to their base.")
                        .formatted(Formatting.YELLOW, Formatting.BOLD),
                    true // Action bar
                );
            }
        }
    }
    
    /**
     * Handle player elimination
     */
    public void handlePlayerElimination(UUID playerId) {
        ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
        if (player != null) {
            // CRITICAL: Handle flag dropping FIRST before respawn delay to prevent flag duplication
            handlePlayerLeave(player); // This will drop any carried flags
            
            // Use handlePlayerDeath to trigger the 10-second respawn delay
            handlePlayerDeath(player);
        }
    }
    
    /**
     * Handle player death and start respawn delay
     */
    public void handlePlayerDeath(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        
        if (playerTeam == null) return;
        
        // Drop any carried flag
        handlePlayerLeave(player);
        
        // Put player in spectator mode
        player.changeGameMode(GameMode.SPECTATOR);
        playersInSpectatorMode.add(playerId);
        
        // Start respawn delay
        long respawnTime = System.currentTimeMillis() + (respawnDelaySeconds * 1000L);
        respawnDelayTasks.put(playerId, respawnTime);
        
        // Schedule respawn
        timerScheduler.schedule(() -> {
            respawnPlayer(playerId);
        }, respawnDelaySeconds, TimeUnit.SECONDS);
        
        // Show respawn timer to player
        showRespawnTimer(player);
        
        // Announce death
        battle.broadcastToGamePlayers(
            Text.literal(player.getName().getString() + " was eliminated! Respawning in " + respawnDelaySeconds + " seconds...")
                .formatted(Formatting.GRAY)
        );
    }
    
    /**
     * Respawn a player at their team base
     */
    private void respawnPlayer(UUID playerId) {
        respawnDelayTasks.remove(playerId);
        playersInSpectatorMode.remove(playerId);
        
        ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
        if (player == null) return;
        
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        if (playerTeam == null) return;
        
        // Get team base
        BlockPos teamBase = getTeamBase(playerTeam.getName());
        if (teamBase == null) {
            // Fallback to game spawn if no base set
            BlockPos gameSpawn = battle.getGameSpawnPoint();
            if (gameSpawn != null) {
                teamBase = gameSpawn;
            } else {
                return; // No spawn point available
            }
        }
        
        // Change back to survival mode
        player.changeGameMode(GameMode.SURVIVAL);
        
        // Teleport to team base
        ServerWorld world = player.getServerWorld();
        player.teleport(world, teamBase.getX() + 0.5, teamBase.getY() + 1.0, 
            teamBase.getZ() + 0.5, player.getYaw(), player.getPitch());
        
        // Clear effects and heal player
        player.clearStatusEffects();
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
        
        // Announce respawn
        player.sendMessage(
            Text.literal("You have respawned at your team base!")
                .formatted(Formatting.GREEN, Formatting.BOLD),
            true // Action bar
        );
    }
    
    /**
     * Show respawn timer to player
     */
    private void showRespawnTimer(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        
        // Schedule timer updates every second
        for (int i = 1; i <= respawnDelaySeconds; i++) {
            final int secondsLeft = respawnDelaySeconds - i + 1;
            timerScheduler.schedule(() -> {
                // Check if player is still waiting to respawn
                if (respawnDelayTasks.containsKey(playerId)) {
                    player.sendMessage(
                        Text.literal("Respawning in " + secondsLeft + " second" + (secondsLeft != 1 ? "s" : "") + "...")
                            .formatted(Formatting.YELLOW, Formatting.BOLD),
                        true // Action bar
                    );
                }
            }, i, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Check if a player is in respawn delay
     */
    public boolean isPlayerInRespawnDelay(UUID playerId) {
        return respawnDelayTasks.containsKey(playerId);
    }
    
    /**
     * Get remaining respawn time for a player in seconds
     */
    public int getRemainingRespawnTime(UUID playerId) {
        Long respawnTime = respawnDelayTasks.get(playerId);
        if (respawnTime != null) {
            long remainingMillis = respawnTime - System.currentTimeMillis();
            return (int) Math.ceil(remainingMillis / 1000.0);
        }
        return 0;
    }
    
    /**
     * Start the round timer
     */
    public void startRoundTimer() {
        if (roundTimerActive) return;
        roundTimerActive = true;
        
        // Broadcast starting message
        battle.broadcastToGamePlayers(
            Text.literal("CTF Round starting! Flags will be dropped in " + roundTimeMinutes + " minutes.")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        );
        
        // Schedule flag drop
        timerScheduler.schedule(() -> {
            dropFlags();
        }, roundTimeMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Stop the round timer
     */
    private void stopRoundTimer() {
        roundTimerActive = false;
        
        // Cancel all scheduled tasks for the timer
        timerScheduler.shutdownNow();
        timerScheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Drop flags at the start of the round
     */
    private void dropFlags() {
        // Drop flags at each base location
        for (Map.Entry<String, BlockPos> entry : flagBases.entrySet()) {
            String team = entry.getKey();
            BlockPos basePos = entry.getValue();
            
            // Only drop flags for teams that are part of the battle
            if (teamScores.containsKey(team)) {
                flagsAtBase.put(team, false); // Flag is no longer at base
                
                // Play particle effect at the base location
                ServerWorld world = battle.getServer().getOverworld();
                DustParticleEffect particleEffect = getTeamParticleEffect(team);
                for (int i = 0; i < 10; i++) {
                    double x = basePos.getX() + Math.random();
                    double y = basePos.getY() + 1.0;
                    double z = basePos.getZ() + Math.random();
                    world.spawnParticles(particleEffect, x, y, z, 1, 0.0, 0.0, 0.0, 0);
                }
            }
        }
        
        // Announce flag drop
        // REMOVED: "CTF Flags have been dropped!" message as requested
        /*
        battle.broadcastToGamePlayers(
            Text.literal("CTF Flags have been dropped!")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        );
        */
    }
    
    /**
     * Update the scoreboard for all players
     */
    private void updateScoreboardForAllPlayers() {
        for (UUID playerId : battle.getGamePlayers()) {
            ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null) {
                updateScoreboardForPlayer(player);
            }
        }
    }
    
    /**
     * Update the scoreboard for a specific player
     */
    private void updateScoreboardForPlayer(ServerPlayerEntity player) {
        // Alternative implementation without scoreboard tags - use action bar instead
        StringBuilder scoreText = new StringBuilder("Scores: ");
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            String team = entry.getKey();
            int score = entry.getValue();
            scoreText.append(team).append(": ").append(score).append(" ");
        }
        
        // Send score as action bar message
        player.sendMessage(Text.literal(scoreText.toString().trim())
            .formatted(Formatting.AQUA), true);
    }
    
    /**
     * Set the CTF game mode
     */
    public void setCTFMode(CTFMode mode) {
        this.ctfMode = mode;
    }
    
    /**
     * Set the target score for score-based mode
     */
    public void setTargetScore(int score) {
        this.targetScore = score;
    }
    
    /**
     * Get the current CTF mode
     */
    public CTFMode getCTFMode() {
        return ctfMode;
    }
    
    /**
     * Get the target score for score-based mode
     */
    public int getTargetScore() {
        return targetScore;
    }
    
    /**
     * Cleanup respawn delays and spectator states
     */
    public void cleanup() {
        respawnDelayTasks.clear();
        for (UUID playerId : new HashSet<>(respawnDelayTasks.keySet())) {
            ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.changeGameMode(GameMode.SURVIVAL);
            }
        }
    }
    
    /**
     * Complete shutdown of CTF - clear everything including bases, particles, and team effects
     * Called when the battle system is shutting down entirely
     */
    public void shutdownAndClearAll() {
        // Stop all particle effects
        stopAllParticles();
        
        // Clear all flag carrier effects
        clearAllFlagCarrierEffects();
        
        // Clear respawn delays and restore players from spectator mode
        clearAllRespawnDelays();
        
        // Clear ALL CTF state including bases (unlike reset() which preserves bases)
        flagBases.clear();
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
        
        // Clean up any remaining CTF glow teams from scoreboard
        cleanupCTFGlowTeams();
        
        // Cancel flag check task before shutting down schedulers
        if (flagCheckTask != null && !flagCheckTask.isCancelled()) {
            flagCheckTask.cancel(false);
            flagCheckTask = null;
        }
        
        // Shutdown schedulers
        if (particleScheduler != null && !particleScheduler.isShutdown()) {
            particleScheduler.shutdownNow();
        }
        if (timerScheduler != null && !timerScheduler.isShutdown()) {
            timerScheduler.shutdownNow();
        }
    }
    
    /**
     * Clean up any CTF glow teams that may persist in the scoreboard
     */
    private void cleanupCTFGlowTeams() {
        if (battle != null) {
            MinecraftServer server = battle.getServer();
            if (server != null) {
                Scoreboard scoreboard = server.getScoreboard();
                
                // Remove all CTF glow teams
                Collection<Team> allTeams = scoreboard.getTeams();
                List<Team> teamsToRemove = new ArrayList<>();
                
                for (Team team : allTeams) {
                    if (team.getName().startsWith("ctf_glow_")) {
                        teamsToRemove.add(team);
                    }
                }
                
                for (Team team : teamsToRemove) {
                    scoreboard.removeTeam(team);
                }
            }
        }
    }

    /**
     * Ensure all base particles are active (public method for Battle to call)
     */
    public void ensureBaseParticlesActive() {
        restartAllBaseParticles();
    }
    
    // Methods for colored glow effects using scoreboard teams and glowing effect
    private void addColoredGlowing(ServerPlayerEntity player, String team) {
        // Add vanilla glowing effect
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));
        
        // Add player to temporary colored team for glow color
        Scoreboard scoreboard = player.getServer().getScoreboard();
        String glowTeamName = "ctf_glow_" + team.toLowerCase();
        
        Team glowTeam = scoreboard.getTeam(glowTeamName);
        if (glowTeam == null) {
            glowTeam = scoreboard.addTeam(glowTeamName);
            // Set team color based on flag team
            if ("red".equalsIgnoreCase(team)) {
                glowTeam.setColor(Formatting.RED);
            } else if ("blue".equalsIgnoreCase(team)) {
                glowTeam.setColor(Formatting.BLUE);
            } else {
                glowTeam.setColor(Formatting.YELLOW); // Default color for other teams
            }
            glowTeam.setShowFriendlyInvisibles(false);
            glowTeam.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        }
        
        // Add player to the glow team
        scoreboard.addPlayerToTeam(player.getEntityName(), glowTeam);
    }

    private void removeColoredGlowing(ServerPlayerEntity player) {
        // Remove glowing effect
        player.removeStatusEffect(StatusEffects.GLOWING);
        
        // Remove player from glow teams
        Scoreboard scoreboard = player.getServer().getScoreboard();
        
        // Remove from any CTF glow teams
        Team currentTeam = scoreboard.getPlayerTeam(player.getEntityName());
        if (currentTeam != null && currentTeam.getName().startsWith("ctf_glow_")) {
            scoreboard.removePlayerFromTeam(player.getEntityName(), currentTeam);
        }
        
        // Note: Original team restoration would need to be handled by the main battle system
        // when the CTF game ends, as we don't have direct access to Battle instance here
    }

    /**
     * Restart particle effects for all existing bases
     */
    private void restartAllBaseParticles() {
        for (Map.Entry<String, BlockPos> entry : flagBases.entrySet()) {
            String teamName = entry.getKey();
            BlockPos basePos = entry.getValue();
            startBaseParticles(teamName, basePos);
        }
    }
}
