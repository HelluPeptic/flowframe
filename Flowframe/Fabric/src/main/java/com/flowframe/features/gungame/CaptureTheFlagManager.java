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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CaptureTheFlagManager {
    private final Map<String, BlockPos> flagBases = new ConcurrentHashMap<>();
    private final Map<String, UUID> flagCarriers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> flagsAtBase = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> teamFlags = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamScores = new ConcurrentHashMap<>();
    private final Map<UUID, String> carrierEffectTasks = new ConcurrentHashMap<>(); // Track effect tasks for each carrier
    private final int roundTimeMinutes = 10; // CTF rounds last 10 minutes
    private ScheduledExecutorService particleScheduler;
    private ScheduledExecutorService timerScheduler;
    private boolean roundTimerActive = false;
    
    // CTF only supports Red and Blue teams
    private final List<String> allowedTeams = Arrays.asList("Red", "Blue");
    
    private final Battle battle;
    
    public CaptureTheFlagManager(Battle battle) {
        this.battle = battle;
        this.particleScheduler = Executors.newScheduledThreadPool(1);
        this.timerScheduler = Executors.newScheduledThreadPool(1);
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
            System.err.println("CTF Warning: Expected exactly 2 teams (Red/Blue), found: " + validTeams);
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
    }
      /**
     * Set flag base location for a team and start particle effects
     */
    public void setFlagBase(String teamName, BlockPos basePos) {
        // Normalize team name to match Battle's naming convention (capitalize first letter)
        String normalizedTeamName = teamName.substring(0, 1).toUpperCase() + teamName.substring(1).toLowerCase();
        
        System.out.println("CTF DEBUG: Setting base for team '" + teamName + "' (normalized: '" + normalizedTeamName + "') at position " + basePos);
        
        // Store with normalized name
        flagBases.put(normalizedTeamName, basePos);
        
        // Debug: Print all current bases
        System.out.println("CTF DEBUG: All bases after setting:");
        for (Map.Entry<String, BlockPos> entry : flagBases.entrySet()) {
            System.out.println("  Team '" + entry.getKey() + "' -> " + entry.getValue());
        }
        
        startBaseParticles(normalizedTeamName, basePos);
    }

    /**
     * Get flag base location for a team
     */
    public BlockPos getTeamBase(String teamName) {
        // Normalize team name to match Battle's naming convention (capitalize first letter)
        String normalizedTeamName = teamName.substring(0, 1).toUpperCase() + teamName.substring(1).toLowerCase();
        
        BlockPos base = flagBases.get(normalizedTeamName);
        System.out.println("CTF DEBUG: Getting base for team '" + teamName + "' (normalized: '" + normalizedTeamName + "') -> " + base);
        System.out.println("CTF DEBUG: Available bases: " + flagBases.keySet());
        return base;
    }
    
    /**
     * Start continuous particle effects for a team base
     */
    private void startBaseParticles(String teamName, BlockPos basePos) {
        // Get particle color based on team
        DustParticleEffect particleEffect = getTeamParticleEffect(teamName);
        
        // Schedule repeating particle effects
        particleScheduler.scheduleAtFixedRate(() -> {
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
                
                // Create a 3x3 area of particles around the base
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
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
        
        if (teamBase == null || playerPos.getManhattanDistance(teamBase) > 5) {
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
     * In timer-based CTF, there's no score limit - winner is determined when timer expires
     */
    public String getWinningTeam() {
        // No automatic win condition based on score
        // Winner is determined by timer expiration in endRoundByTimer()
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
        
        // DON'T clear flagBases - they should persist with particles
        // flagBases.clear(); // REMOVED: bases should persist
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
    }
    
    /**
     * Stop all particle effects
     */
    public void stopAllParticles() {
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
                        String playerName = player != null ? player.getName().getString() : "Unknown";
                        status.append("Carried by ").append(playerName);
                    } else {
                        status.append("Missing");
                    }
                }
                status.append("] ");
            }
        }
        return status.toString();
    }
    
    /**
     * Handle player movement - check for automatic flag pickup and capture
     */
    public void handlePlayerMovement(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        BattleTeam playerTeam = battle.getPlayerTeam(playerId);
        if (playerTeam == null) return;
        
        String playerTeamName = playerTeam.getName();
        BlockPos playerPos = player.getBlockPos();
        
        // Debug: Show basic info every 20 ticks (1 second) to reduce spam
        if (player.age % 20 == 0) {
            player.sendMessage(
                Text.literal("CTF: " + playerTeamName + " at " + playerPos.getX() + "," + playerPos.getY() + "," + playerPos.getZ())
                    .formatted(Formatting.DARK_GRAY),
                true // Action bar
            );
        }
        
        // Check for automatic flag capture at own base
        if (isPlayerCarryingFlag(playerId)) {
            BlockPos ownBase = flagBases.get(playerTeamName);
            if (ownBase != null) {
                double distance = Math.sqrt(playerPos.getSquaredDistance(ownBase));
                
                // Debug: Show distance to own base when carrying flag
                if (player.age % 10 == 0) { // More frequent when important
                    player.sendMessage(
                        Text.literal("Distance to " + playerTeamName + " base: " + String.format("%.1f", distance) + " (need ≤5.0)")
                            .formatted(Formatting.YELLOW),
                        true // Action bar
                    );
                }
                
                if (distance <= 5.0) {
                    if (tryAutoCapture(player)) {
                        return; // Successfully captured, no need to check pickup
                    }
                }
            } else {
                // Debug: Show if own base is missing
                if (player.age % 20 == 0) {
                    player.sendMessage(
                        Text.literal("Warning: " + playerTeamName + " base not found!")
                            .formatted(Formatting.RED),
                        true
                    );
                }
            }
        }
        
        // Check for automatic flag pickup at enemy bases
        for (String teamName : flagBases.keySet()) {
            if (!teamName.equals(playerTeamName)) {
                BlockPos enemyBase = flagBases.get(teamName);
                if (enemyBase != null) {
                    double distance = Math.sqrt(playerPos.getSquaredDistance(enemyBase));
                    
                    // Debug: Show when near enemy base
                    if (distance <= 8.0 && player.age % 10 == 0) {
                        boolean flagAtBase = flagsAtBase.getOrDefault(teamName, true);
                        boolean canPickup = !isPlayerCarryingFlag(playerId);
                        
                        player.sendMessage(
                            Text.literal("Near " + teamName + " base: " + String.format("%.1f", distance) + 
                                " | Flag at base: " + flagAtBase + " | Can pickup: " + canPickup)
                                .formatted(distance <= 5.0 ? Formatting.GREEN : Formatting.YELLOW),
                            true // Action bar
                        );
                    }
                    
                    if (distance <= 5.0) {
                        if (tryAutoPickup(player, teamName)) {
                            break; // Only try one pickup per tick
                        }
                    }
                } else {
                    // Debug: Show if enemy base is missing
                    if (player.age % 40 == 0) {
                        player.sendMessage(
                            Text.literal("Warning: " + teamName + " base not found!")
                                .formatted(Formatting.RED),
                            true
                        );
                    }
                }
            }
        }
        
        // Debug: Show all team bases occasionally
        if (player.age % 100 == 0) { // Every 5 seconds
            StringBuilder bases = new StringBuilder("Bases: ");
            for (Map.Entry<String, BlockPos> entry : flagBases.entrySet()) {
                bases.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
            player.sendMessage(
                Text.literal(bases.toString()).formatted(Formatting.GRAY),
                false // Chat
            );
        }
    }
    
    /**
     * Automatic flag pickup when player is near enemy base
     */
    private boolean tryAutoPickup(ServerPlayerEntity player, String flagTeam) {
        UUID playerId = player.getUuid();
        
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
        
        // No win condition - game continues until timer expires
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
        particleScheduler.scheduleAtFixedRate(() -> {
            try {
                if (player.isRemoved() || !flagCarriers.containsKey(flagTeam) || 
                    !flagCarriers.get(flagTeam).equals(playerId)) {
                    stopFlagCarrierEffects(player);
                    return;
                }
                
                // Refresh glowing effect to prevent it from expiring (only if still using vanilla glowing)
                // With colored glowing via scoreboard teams, this is handled by the team membership
                
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
            handlePlayerLeave(player);
        }
    }
    
    /**
     * Update scoreboard for all players with CTF scores
     */
    private void updateScoreboardForAllPlayers() {
        if (battle == null) return;
        
        // Create a simple score display message
        StringBuilder scoreMessage = new StringBuilder();
        scoreMessage.append("CTF Scores: ");
        
        boolean first = true;
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            if (!first) scoreMessage.append(" | ");
            
            String teamName = entry.getKey();
            int score = entry.getValue();
            
            // Get team formatting
            BattleTeam team = battle.getTeam(teamName);
            if (team != null) {
                scoreMessage.append(teamName).append(": ").append(score);
            }
            first = false;
        }
        
        // Send to all game players as action bar
        Text scoreText = Text.literal(scoreMessage.toString()).formatted(Formatting.GOLD);
        for (UUID playerId : battle.getGamePlayers()) {
            ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(scoreText, true); // Action bar
            }
        }
    }
    
    /**
     * Clean up all CTF resources
     */
    public void cleanup() {
        // Stop round timer
        stopRoundTimer();
        
        // Stop all particle effects
        if (particleScheduler != null && !particleScheduler.isShutdown()) {
            particleScheduler.shutdown();
            try {
                if (!particleScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    particleScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                particleScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Stop timer scheduler
        if (timerScheduler != null && !timerScheduler.isShutdown()) {
            timerScheduler.shutdown();
            try {
                if (!timerScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    timerScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                timerScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Stop all flag carrier effects
        clearAllFlagCarrierEffects();
        
        // Clear all data
        flagBases.clear();
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
        carrierEffectTasks.clear();
    }
    
    /**
     * Start the CTF round timer
     */
    public void startRoundTimer() {
        if (roundTimerActive) return;
        
        roundTimerActive = true;
        int totalSeconds = roundTimeMinutes * 60;
        
        // Show timer warnings at specific intervals
        int[] warningTimes = {600, 300, 120, 60, 30, 10, 5, 4, 3, 2, 1}; // 10min, 5min, 2min, 1min, 30s, 10s, 5-1s
        
        for (int warningTime : warningTimes) {
            if (warningTime < totalSeconds) {
                timerScheduler.schedule(() -> {
                    if (!roundTimerActive) return;
                    
                    String timeMessage;
                    if (warningTime >= 60) {
                        int minutes = warningTime / 60;
                        timeMessage = minutes + " minute" + (minutes > 1 ? "s" : "") + " remaining";
                    } else {
                        timeMessage = warningTime + " second" + (warningTime > 1 ? "s" : "") + " remaining";
                    }
                    
                    battle.broadcastToGamePlayers(
                        Text.literal("⏰ CTF Timer: " + timeMessage + "!")
                            .formatted(warningTime <= 30 ? Formatting.RED : Formatting.YELLOW)
                    );
                }, (totalSeconds - warningTime) * 1000L, TimeUnit.MILLISECONDS);
            }
        }
        
        // End the round when timer expires
        timerScheduler.schedule(() -> {
            if (!roundTimerActive) return;
            endRoundByTimer();
        }, totalSeconds * 1000L, TimeUnit.MILLISECONDS);
        
        // Start the timer display
        battle.broadcastToGamePlayers(
            Text.literal("⏰ CTF Round started! " + roundTimeMinutes + " minutes to capture flags!")
                .formatted(Formatting.GREEN, Formatting.BOLD)
        );
    }
    
    /**
     * Stop the CTF round timer
     */
    public void stopRoundTimer() {
        roundTimerActive = false;
    }
    
    /**
     * End the round due to timer expiration
     */
    private void endRoundByTimer() {
        if (!roundTimerActive) return;
        
        roundTimerActive = false;
        
        // Determine winner by score
        String winningTeam = null;
        int highestScore = -1;
        boolean tie = false;
        
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            int score = entry.getValue();
            if (score > highestScore) {
                highestScore = score;
                winningTeam = entry.getKey();
                tie = false;
            } else if (score == highestScore && highestScore > 0) {
                tie = true;
            }
        }
        
        // Announce the result
        if (tie || winningTeam == null || highestScore == 0) {
            battle.broadcastToGamePlayers(
                Text.literal("⏰ Time's up! The round ended in a draw.").formatted(Formatting.GRAY)
            );
            // For draws, continue to next round or end battle depending on settings
            battle.handlePlayerElimination(); // This will check for game end conditions
        } else {
            BattleTeam team = battle.getTeam(winningTeam);
            if (team != null) {
                battle.broadcastToGamePlayers(
                    Text.literal("⏰ Time's up! ")
                        .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                        .append(Text.literal(" wins with " + highestScore + " captures!"))
                        .formatted(Formatting.GOLD, Formatting.BOLD)
                );
                
                // End the game or advance to next round
                if (battle.getCurrentRound() >= battle.getTotalRounds()) {
                    // End the entire battle
                    battle.endGame(team);
                } else {
                    // Start next round
                    battle.nextRound();
                }
            }
        }
    }
    
    /**
     * Add colored glowing effect by temporarily placing player in a flag-colored team
     */
    private void addColoredGlowing(ServerPlayerEntity player, String flagTeam) {
        if (battle.getServer() == null) return;
        
        String tempTeamName = "ctf_flag_" + flagTeam.toLowerCase();
        Scoreboard scoreboard = battle.getServer().getScoreboard();
        
        // Create temporary team for flag color if it doesn't exist
        Team scoreboardTeam = scoreboard.getTeam(tempTeamName);
        if (scoreboardTeam == null) {
            scoreboardTeam = scoreboard.addTeam(tempTeamName);
            
            // Set team color based on flag
            switch (flagTeam.toLowerCase()) {
                case "red":
                    scoreboardTeam.setColor(Formatting.RED);
                    break;
                case "blue":
                    scoreboardTeam.setColor(Formatting.BLUE);
                    break;
                default:
                    scoreboardTeam.setColor(Formatting.WHITE);
            }
            
            // Enable glowing for this team
            scoreboardTeam.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
            scoreboardTeam.setShowFriendlyInvisibles(true);
        }
        
        // Add player to flag-colored team (this provides colored glowing)
        scoreboard.addPlayerToTeam(player.getEntityName(), scoreboardTeam);
        
        // Apply vanilla glowing effect
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 999999, 0, false, false, true));
    }
    
    /**
     * Remove colored glowing effect by restoring player to their original battle team
     */
    private void removeColoredGlowing(ServerPlayerEntity player) {
        if (battle.getServer() == null) return;
        
        Scoreboard scoreboard = battle.getServer().getScoreboard();
        
        // Remove player from any temporary flag team
        Team currentTeam = scoreboard.getPlayerTeam(player.getEntityName());
        if (currentTeam != null && currentTeam.getName().startsWith("ctf_flag_")) {
            scoreboard.removePlayerFromTeam(player.getEntityName(), currentTeam);
        }
        
        // Restore player to their original battle team
        BattleTeam playerTeam = battle.getPlayerTeam(player.getUuid());
        if (playerTeam != null) {
            String teamName = "battle_" + playerTeam.getName().toLowerCase();
            Team battleTeam = scoreboard.getTeam(teamName);
            if (battleTeam != null) {
                scoreboard.addPlayerToTeam(player.getEntityName(), battleTeam);
            }
        }
        
        // Remove glowing effect
        player.removeStatusEffect(StatusEffects.GLOWING);
    }
}
