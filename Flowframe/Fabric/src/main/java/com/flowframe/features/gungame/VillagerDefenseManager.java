package com.flowframe.features.gungame;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.village.VillagerProfession;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class VillagerDefenseManager {
    private final Map<String, BlockPos> villagerBases = new ConcurrentHashMap<>();
    private final Map<String, VillagerEntity> teamVillagers = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamScores = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> particleTasks = new ConcurrentHashMap<>();
    private final int roundTimeMinutes = 10; // Villager Defense rounds last 10 minutes
    private ScheduledExecutorService particleScheduler;
    private ScheduledExecutorService timerScheduler;
    private boolean roundTimerActive = false;
    private ScheduledFuture<?> villagerCheckTask; // Task to check for missing villagers
    
    private final Battle battle;
    private final Set<String> allowedTeams = Set.of("red", "blue"); // Only Red and Blue teams allowed
    
    public VillagerDefenseManager(Battle battle) {
        this.battle = battle;
        this.particleScheduler = Executors.newScheduledThreadPool(2);
        this.timerScheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Initialize Villager Defense with teams
     */
    public void initializeVillagerDefense(Collection<String> teamNames) {
        // Clear existing data (but preserve manually set bases)
        teamVillagers.clear();
        teamScores.clear();
        
        // Initialize scores for new teams
        if (teamScores.isEmpty()) {
            for (String teamName : teamNames) {
                teamScores.put(teamName, 0);
            }
        }
        
        // Use existing teams that match allowed teams
        List<String> validTeams = new ArrayList<>();
        for (String teamName : teamNames) {
            for (String allowedTeam : allowedTeams) {
                if (teamName.equalsIgnoreCase(allowedTeam)) {
                    validTeams.add(teamName);
                    break;
                }
            }
        }
        
        // Ensure we have exactly Red and Blue teams
        if (validTeams.size() != 2) {
            validTeams.clear();
            for (String teamName : teamNames) {
                if (teamName.equalsIgnoreCase("red") || teamName.equalsIgnoreCase("blue")) {
                    validTeams.add(teamName);
                }
            }
        }
        
        // Initialize each team
        for (String teamName : validTeams) {
            teamScores.put(teamName, 0);
        }
        
        // Start periodic villager health monitoring
        if (timerScheduler != null && !timerScheduler.isShutdown()) {
            villagerCheckTask = timerScheduler.scheduleAtFixedRate(
                this::checkVillagerHealth, 
                5, // Initial delay
                5, // Check every 5 seconds
                TimeUnit.SECONDS
            );
        }
    }
    
    /**
     * Set villager base location for a team and spawn the villager
     * Can only be set by team leader and only during non-active game states
     */
    public boolean setVillagerBase(String teamName, BlockPos basePos, UUID playerId) {
        // Check if game is active (bases can't be moved during active games)
        if (battle.getState() == Battle.BattleState.ACTIVE) {
            return false; // Can't set base during active game
        }
        
        // Check if player is team leader
        BattleTeam team = battle.getTeam(teamName);
        if (team == null || !team.getTeamLeader().equals(playerId)) {
            return false; // Only team leader can set bases
        }
        
        // Normalize team name for consistent storage
        String normalizedTeamName = teamName.toLowerCase();
        
        // Remove existing villager if it exists
        VillagerEntity existingVillager = teamVillagers.get(normalizedTeamName);
        if (existingVillager != null && !existingVillager.isRemoved()) {
            existingVillager.discard();
            teamVillagers.remove(normalizedTeamName); // Ensure it's removed from the map
        }
        
        // Stop existing particle effects for this team if they exist
        stopBaseParticles(normalizedTeamName);
        
        // Set the new base location
        villagerBases.put(normalizedTeamName, basePos);
        
        // Spawn new villager at the base
        spawnVillager(normalizedTeamName, basePos);
        
        // Start particle effects
        startBaseParticles(normalizedTeamName, basePos);
        
        return true;
    }
    
    /**
     * Set villager base location for a team (admin command version)
     */
    public void setVillagerBase(String teamName, BlockPos basePos) {
        String normalizedTeamName = teamName.toLowerCase();
        
        // Remove existing villager if it exists
        VillagerEntity existingVillager = teamVillagers.get(normalizedTeamName);
        if (existingVillager != null && !existingVillager.isRemoved()) {
            existingVillager.discard();
            teamVillagers.remove(normalizedTeamName); // Ensure it's removed from the map
        }
        
        // Stop existing particle effects for this team if they exist
        stopBaseParticles(normalizedTeamName);
        
        villagerBases.put(normalizedTeamName, basePos);
        
        // Spawn new villager at the base
        spawnVillager(normalizedTeamName, basePos);
        
        // Start particle effects
        startBaseParticles(normalizedTeamName, basePos);
    }
    
    /**
     * Spawn a villager at the specified base location
     */
    private void spawnVillager(String teamName, BlockPos basePos) {
        MinecraftServer server = battle.getServer();
        if (server == null) return;
        
        ServerWorld world = server.getOverworld();
        if (world == null) return;
        
        // Create villager entity
        VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, world);
        villager.refreshPositionAndAngles(
            basePos.getX() + 0.5, 
            basePos.getY(), 
            basePos.getZ() + 0.5, 
            0.0f, 
            0.0f
        );
        
        // Set villager properties
        villager.setPersistent();
        villager.setAiDisabled(true); // Disable AI so it doesn't move
        // villager.setProfession(VillagerProfession.NONE); // May not be needed
        villager.setNoGravity(true); // Prevent falling
        
        // Set maximum health to 1000
        Objects.requireNonNull(villager.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH)).setBaseValue(1000.0);
        villager.setHealth(1000.0f);
        
        // Add resistance and regeneration effects
        villager.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 2, false, false));
        villager.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, Integer.MAX_VALUE, 1, false, false));
        
        // Set custom name based on team
        String teamColor = teamName.equalsIgnoreCase("red") ? "red" : "blue";
        villager.setCustomName(Text.literal(teamName.substring(0, 1).toUpperCase() + teamName.substring(1) + " Team Villager").formatted(
            teamColor.equals("red") ? Formatting.RED : Formatting.BLUE
        ));
        villager.setCustomNameVisible(true);
        
        // Add NBT data to identify this as a team villager
        NbtCompound nbt = villager.writeNbt(new NbtCompound());
        nbt.putString("TeamName", teamName);
        nbt.putBoolean("IsTeamVillager", true);
        villager.readNbt(nbt);
        
        // Spawn the villager in the world
        world.spawnEntity(villager);
        
        // Store reference to the villager
        teamVillagers.put(teamName, villager);
        
        // Broadcast villager spawn message
        broadcastMessage(Text.literal("Villager spawned at " + teamName + " base!").formatted(Formatting.YELLOW));
    }
    
    /**
     * Start particle effects around a base
     */
    private void startBaseParticles(String teamName, BlockPos basePos) {
        if (particleScheduler == null || particleScheduler.isShutdown()) return;
        
        // Choose particle color based on team
        Vector3f color = teamName.equalsIgnoreCase("red") ? 
            new Vector3f(1.0f, 0.2f, 0.2f) : // Red
            new Vector3f(0.2f, 0.2f, 1.0f);   // Blue
        
        ScheduledFuture<?> task = particleScheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = battle.getServer();
            if (server != null) {
                ServerWorld world = server.getOverworld();
                if (world != null) {
                // Create a circular particle effect around the base
                double centerX = basePos.getX() + 0.5;
                double centerY = basePos.getY() + 1.0;
                double centerZ = basePos.getZ() + 0.5;
                
                for (int i = 0; i < 12; i++) {
                    double angle = (i * 2 * Math.PI) / 12;
                    double radius = 2.0;
                    double particleX = centerX + radius * Math.cos(angle);
                    double particleZ = centerZ + radius * Math.sin(angle);
                    
                    world.spawnParticles(
                        new DustParticleEffect(color, 1.0f),
                        particleX, centerY, particleZ,
                        1, 0, 0, 0, 0
                    );
                }
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
        
        particleTasks.put(teamName, task);
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
     * Check villager health and handle game win conditions
     */
    private void checkVillagerHealth() {
        for (Map.Entry<String, VillagerEntity> entry : teamVillagers.entrySet()) {
            String teamName = entry.getKey();
            VillagerEntity villager = entry.getValue();
            
            if (villager == null || villager.isRemoved() || villager.isDead()) {
                // Villager is dead - opposing team wins
                handleVillagerDeath(teamName);
                return;
            }
            
            // Check if villager health is critically low
            float health = villager.getHealth();
            if (health <= 100.0f) { // Less than 10% health
                broadcastMessage(Text.literal(teamName + " team's villager is critically injured! (" + 
                    Math.round(health) + "/1000 HP)").formatted(Formatting.RED));
            }
        }
    }
    
    /**
     * Handle when a team's villager dies
     */
    private void handleVillagerDeath(String deadTeam) {
        // Find the opposing team
        String winningTeam = null;
        for (String teamName : allowedTeams) {
            if (!teamName.equalsIgnoreCase(deadTeam)) {
                winningTeam = teamName;
                break;
            }
        }
        
        if (winningTeam != null) {
            // Announce victory
            broadcastMessage(Text.literal(winningTeam.toUpperCase() + " TEAM WINS!").formatted(
                winningTeam.equalsIgnoreCase("red") ? Formatting.RED : Formatting.BLUE
            ));
            broadcastMessage(Text.literal(deadTeam + " team's villager has been defeated!").formatted(Formatting.GRAY));
            
            // Play victory sound
            playVictorySound();
            
            // End the game
            BattleTeam team = battle.getTeam(winningTeam);
            if (team != null) {
                battle.endGame(team);
            }
        }
    }
    
    /**
     * Start timed round with specified duration
     */
    public void startTimedRound() {
        if (roundTimerActive) return;
        
        roundTimerActive = true;
        broadcastMessage(Text.literal("Villager Defense round started! Destroy the enemy villager to win!").formatted(Formatting.YELLOW));
        
        // Stop any existing timer
        stopRoundTimer();
        
        // Start new timer
        timerScheduler.schedule(() -> {
            if (roundTimerActive) {
                handleTimeExpired();
            }
        }, roundTimeMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Handle when round time expires
     */
    private void handleTimeExpired() {
        if (!roundTimerActive) return;
        
        roundTimerActive = false;
        
        // Find team with villager that has more health
        String winningTeam = null;
        float highestHealth = 0;
        
        for (Map.Entry<String, VillagerEntity> entry : teamVillagers.entrySet()) {
            VillagerEntity villager = entry.getValue();
            if (villager != null && !villager.isRemoved() && !villager.isDead()) {
                float health = villager.getHealth();
                if (health > highestHealth) {
                    highestHealth = health;
                    winningTeam = entry.getKey();
                }
            }
        }
        
        if (winningTeam != null) {
            broadcastMessage(Text.literal("Time expired! " + winningTeam.toUpperCase() + " TEAM WINS with " + 
                Math.round(highestHealth) + " HP remaining!").formatted(
                winningTeam.equalsIgnoreCase("red") ? Formatting.RED : Formatting.BLUE
            ));
            
            BattleTeam team = battle.getTeam(winningTeam);
            if (team != null) {
                battle.endGame(team);
            }
        } else {
            // Both villagers are dead - draw
            broadcastMessage(Text.literal("Time expired! Both villagers are dead - it's a draw!").formatted(Formatting.YELLOW));
        }
        
        playVictorySound();
    }
    
    /**
     * Stop the round timer
     */
    private void stopRoundTimer() {
        roundTimerActive = false;
    }
    
    /**
     * Reset game state but preserve bases
     */
    public void reset() {
        // Stop timers
        stopRoundTimer();
        
        // DON'T clear villager bases - they should persist
        
        // Clear game state
        teamScores.clear();
        
        // Respawn villagers at existing bases
        for (Map.Entry<String, BlockPos> entry : villagerBases.entrySet()) {
            String teamName = entry.getKey();
            BlockPos basePos = entry.getValue();
            spawnVillager(teamName, basePos);
        }
    }
    
    /**
     * Complete shutdown - clear everything including bases
     */
    public void shutdownAndClearAll() {
        // Stop all particle effects
        stopAllParticles();
        
        // Remove all villagers
        for (VillagerEntity villager : teamVillagers.values()) {
            if (villager != null && !villager.isRemoved()) {
                villager.discard();
            }
        }
        
        // Clear all state including bases
        villagerBases.clear();
        teamVillagers.clear();
        teamScores.clear();
        
        // Cancel villager check task before shutting down schedulers
        if (villagerCheckTask != null && !villagerCheckTask.isCancelled()) {
            villagerCheckTask.cancel(false);
            villagerCheckTask = null;
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
     * Stop all particle effects
     */
    public void stopAllParticles() {
        for (ScheduledFuture<?> task : particleTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel(false);
            }
        }
        particleTasks.clear();
        
        // Shutdown and recreate the scheduler
        if (particleScheduler != null && !particleScheduler.isShutdown()) {
            particleScheduler.shutdownNow();
        }
        particleScheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Play victory sound for all players
     */
    private void playVictorySound() {
        MinecraftServer server = battle.getServer();
        if (server == null) return;
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }
    
    /**
     * Broadcast message to all players in the battle
     */
    private void broadcastMessage(Text message) {
        MinecraftServer server = battle.getServer();
        if (server == null) return;
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (battle.getPlayerTeam(player.getUuid()) != null) {
                player.sendMessage(message, false);
            }
        }
    }
    
    // Getter methods for access from commands and other systems
    public Map<String, BlockPos> getVillagerBases() { return new HashMap<>(villagerBases); }
    public Map<String, VillagerEntity> getTeamVillagers() { return new HashMap<>(teamVillagers); }
    public Map<String, Integer> getTeamScores() { return new HashMap<>(teamScores); }
    public boolean isRoundTimerActive() { return roundTimerActive; }
    
    /**
     * Check if damage to a villager should be prevented (during grace period)
     */
    public boolean shouldPreventVillagerDamage(VillagerEntity villager) {
        // Check if this is a team villager
        if (!isTeamVillager(villager)) {
            return false; // Not our villager, don't interfere
        }
        
        // Prevent damage during grace period (when PvP is disabled)
        return !battle.isPvpEnabled();
    }
    
    /**
     * Check if a villager is a team villager managed by this system
     */
    public boolean isTeamVillager(VillagerEntity villager) {
        return teamVillagers.containsValue(villager);
    }
    
    /**
     * Get villager health for a team
     */
    public float getVillagerHealth(String teamName) {
        VillagerEntity villager = teamVillagers.get(teamName.toLowerCase());
        if (villager != null && !villager.isRemoved() && !villager.isDead()) {
            return villager.getHealth();
        }
        return 0.0f;
    }
    
    /**
     * Get villager max health for a team
     */
    public float getVillagerMaxHealth(String teamName) {
        VillagerEntity villager = teamVillagers.get(teamName.toLowerCase());
        if (villager != null && !villager.isRemoved()) {
            return villager.getMaxHealth();
        }
        return 1000.0f; // Default max health
    }
    
    /**
     * Set villager health for all existing villagers
     */
    public void setVillagerHealth(int newHealth) {
        for (VillagerEntity villager : teamVillagers.values()) {
            if (villager != null && !villager.isRemoved()) {
                villager.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(newHealth);
                villager.setHealth(newHealth);
            }
        }
    }
}
