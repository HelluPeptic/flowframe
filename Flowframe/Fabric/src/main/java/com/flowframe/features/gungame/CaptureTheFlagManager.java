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
    private final int scoreToWin = 3; // First team to capture 3 flags wins
    private ScheduledExecutorService particleScheduler;
    
    // CTF only supports Red and Blue teams
    private final List<String> allowedTeams = Arrays.asList("Red", "Blue");
    
    private final Battle battle;
    
    public CaptureTheFlagManager(Battle battle) {
        this.battle = battle;
        this.particleScheduler = Executors.newScheduledThreadPool(1);
    }
    
    /**
     * Initialize CTF for the given teams (only Red and Blue allowed)
     */
    public void initializeCTF(Collection<String> teamNames) {
        flagBases.clear();
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
        
        // Only allow Red and Blue teams in CTF
        List<String> validTeams = teamNames.stream()
            .filter(allowedTeams::contains)
            .collect(java.util.stream.Collectors.toList());
        
        if (validTeams.size() != 2 || !validTeams.contains("Red") || !validTeams.contains("Blue")) {
            // Force Red and Blue teams
            validTeams = Arrays.asList("Red", "Blue");
        }
        
        // Initialize each team
        for (String teamName : validTeams) {
            flagsAtBase.put(teamName, true);
            teamScores.put(teamName, 0);
            
            // Create colored banner for team flag
            BattleTeam team = battle.getTeam(teamName);
            if (team != null) {
                ItemStack flag = new ItemStack(Items.WHITE_BANNER);
                
                // Set custom name using NBT
                NbtCompound nbt = flag.getOrCreateNbt();
                nbt.putString("display.Name", 
                    Text.Serializer.toJson(Text.literal(teamName + " Flag").formatted(team.getFormatting())));
                
                teamFlags.put(teamName, flag);
            }
        }
    }
    
    /**
     * Set flag base location for a team and start particle effects
     */
    public void setFlagBase(String teamName, BlockPos basePos) {
        flagBases.put(teamName, basePos);
        startBaseParticles(teamName, basePos);
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
        
        // Give flag item to player
        ItemStack flag = teamFlags.get(flagTeam);
        if (flag != null) {
            player.getInventory().insertStack(flag.copy());
        }
        
        // Announce flag pickup
        battle.broadcastToGamePlayers(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal(playerTeamName).formatted(playerTeam.getFormatting()))
                .append(Text.literal("] " + player.getName().getString() + " picked up the "))
                .append(Text.literal(flagTeam + " flag!").formatted(battle.getTeam(flagTeam).getFormatting()))
                .formatted(Formatting.YELLOW)
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
        
        // Remove flag from player's inventory
        ItemStack flag = teamFlags.get(carriedFlag);
        if (flag != null) {
            player.getInventory().removeStack(player.getInventory().getSlotWithStack(flag));
        }
        
        // Increase score
        int newScore = teamScores.get(playerTeamName) + 1;
        teamScores.put(playerTeamName, newScore);
        
        // Announce capture
        battle.broadcastToGamePlayers(
            Text.literal("[").formatted(Formatting.GRAY)
                .append(Text.literal(playerTeamName).formatted(playerTeam.getFormatting()))
                .append(Text.literal("] " + player.getName().getString() + " captured the "))
                .append(Text.literal(carriedFlag + " flag!").formatted(battle.getTeam(carriedFlag).getFormatting()))
                .append(Text.literal(" Score: " + newScore + "/" + scoreToWin))
                .formatted(Formatting.GOLD)
        );
        
        // Check for win condition
        return newScore >= scoreToWin;
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
     */
    public String getWinningTeam() {
        for (Map.Entry<String, Integer> entry : teamScores.entrySet()) {
            if (entry.getValue() >= scoreToWin) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Reset CTF state for new round
     */
    public void resetForNewRound() {
        // Return all flags to base
        for (String team : flagBases.keySet()) {
            flagsAtBase.put(team, true);
        }
        flagCarriers.clear();
        
        // Don't reset scores - they persist across rounds
    }
    
    /**
     * Reset CTF state completely
     */
    public void reset() {
        // Stop all particle effects
        stopAllParticles();
        
        flagBases.clear();
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
        
        // Remove debug message to avoid overwriting important action bar feedback
        // player.sendMessage(
        //     Text.literal("CTF Movement Check: " + playerTeamName + " at " + playerPos)
        //         .formatted(Formatting.DARK_GRAY),
        //     true // Action bar
        // );
        
        // Check for automatic flag capture at own base
        if (isPlayerCarryingFlag(playerId)) {
            BlockPos ownBase = flagBases.get(playerTeamName);
            if (ownBase != null) {
                int distance = playerPos.getManhattanDistance(ownBase);
                // Remove debug message
                // player.sendMessage(
                //     Text.literal("Distance to own base: " + distance + " (need ≤3)")
                //         .formatted(Formatting.GRAY),
                //     true // Action bar
                // );
                
                if (distance <= 3) {
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
                    int distance = playerPos.getManhattanDistance(enemyBase);
                    if (distance <= 3) {
                        // Remove debug message
                        // player.sendMessage(
                        //     Text.literal("Near " + teamName + " base, distance: " + distance)
                        //         .formatted(Formatting.GRAY),
                        //     true // Action bar
                        // );
                        
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
        
        // Give flag item to player
        ItemStack flag = teamFlags.get(flagTeam);
        if (flag != null) {
            player.getInventory().insertStack(flag.copy());
        }
        
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
                Text.literal("You picked up the " + flagTeam + " flag! Return it to your base to score!")
                    .formatted(Formatting.GREEN),
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
        
        // Remove flag from player's inventory
        ItemStack flag = teamFlags.get(carriedFlag);
        if (flag != null) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() == flag.getItem()) {
                    player.getInventory().removeStack(i);
                    break;
                }
            }
        }
        
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
                .append(Text.literal(" Score: " + newScore + "/" + scoreToWin))
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
        
        // Check for win condition
        if (newScore >= scoreToWin) {
            return true; // Team wins - this will be handled by the calling code
        }
        
        return false; // Continue playing
    }
    
    /**
     * Start visual effects for flag carrier
     */
    private void startFlagCarrierEffects(ServerPlayerEntity player, String flagTeam) {
        UUID playerId = player.getUuid();
        stopFlagCarrierEffects(player);
        DustParticleEffect particleEffect = getTeamParticleEffect(flagTeam);
        // Add glowing status effect
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 220, 0, false, false));
        String taskId = "carrier_" + playerId.toString();
        particleScheduler.scheduleAtFixedRate(() -> {
            if (player.isRemoved() || !flagCarriers.containsKey(flagTeam) || 
                !flagCarriers.get(flagTeam).equals(playerId)) {
                stopFlagCarrierEffects(player);
                return;
            }
            ServerWorld world = player.getServerWorld();
            Vec3d pos = player.getPos();
            for (int i = 0; i < 12; i++) {
                double offsetX = (Math.random() - 0.5) * 1.2;
                double offsetY = Math.random() * 1.8;
                double offsetZ = (Math.random() - 0.5) * 1.2;
                double x = pos.x + offsetX;
                double y = pos.y + offsetY;
                double z = pos.z + offsetZ;
                world.spawnParticles(particleEffect, x, y, z, 1, 0.05, 0.05, 0.05, 0);
            }
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
        }, 0, 1, TimeUnit.SECONDS);
        carrierEffectTasks.put(playerId, taskId);
    }
    
    /**
     * Stop visual effects for flag carrier
     */
    private void stopFlagCarrierEffects(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        String taskId = carrierEffectTasks.remove(playerId);
        // Remove glowing status effect
        player.removeStatusEffect(StatusEffects.GLOWING);
        
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
            
            // Remove flag from player's inventory
            ItemStack flag = teamFlags.get(carriedFlag);
            if (flag != null) {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (stack.getItem() == flag.getItem()) {
                        player.getInventory().removeStack(i);
                        break;
                    }
                }
            }
            
            // Announce flag return
            battle.broadcastToGamePlayers(
                Text.literal("The " + carriedFlag + " flag was returned to base!")
                    .formatted(battle.getTeam(carriedFlag).getFormatting())
            );
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
                scoreMessage.append(teamName).append(": ").append(score).append("/").append(scoreToWin);
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
        
        // Stop all flag carrier effects
        for (UUID playerId : new HashSet<>(carrierEffectTasks.keySet())) {
            ServerPlayerEntity player = battle.getServer().getPlayerManager().getPlayer(playerId);
            if (player != null) {
                stopFlagCarrierEffects(player);
            }
        }
        
        // Clear all data
        flagBases.clear();
        flagCarriers.clear();
        flagsAtBase.clear();
        teamFlags.clear();
        teamScores.clear();
        carrierEffectTasks.clear();
    }
}
