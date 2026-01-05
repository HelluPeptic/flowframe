package com.flowframe.features.phantomdeny;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phantom Deny feature that prevents phantom spawning in the overworld.
 * Additionally spawns phantoms in the nether for all players regardless of sleep status,
 * providing access to phantom membrane while preventing overworld phantom harassment.
 * 
 * The overworld phantom blocking is handled by MixinServerWorld_NoPhantoms.java
 * The nether phantom spawning is handled by this feature's server tick event.
 */
public class PhantomDenyFeature {
    private static final Random RANDOM = new Random();
    private static final ConcurrentHashMap<String, Integer> playerCooldowns = new ConcurrentHashMap<>();
    private static int phantomTick = 0;
    
    // Configuration
    private static final int PHANTOM_SPAWN_COOLDOWN = 7200; // 2 minute cooldown per player
    private static final double SPAWN_CHANCE = 0.3; // 30% chance per check
    private static final int CHECK_INTERVAL = 20; // Check every 1 second
    
    public static void register() {
        // Register server tick event to handle nether phantom spawning
        ServerTickEvents.END_SERVER_TICK.register(PhantomDenyFeature::onServerTick);
    }
    
    /**
     * Called every server tick to check for phantom spawning in nether
     */
    private static void onServerTick(MinecraftServer server) {
        phantomTick++;
        
        // Only check every CHECK_INTERVAL ticks to avoid performance issues
        if (phantomTick < CHECK_INTERVAL) {
            return;
        }
        phantomTick = 0;
        
        // Find the nether world
        ServerWorld netherWorld = null;
        for (ServerWorld world : server.getWorlds()) {
            Identifier worldId = world.getRegistryKey().getValue();
            if (worldId.equals(new Identifier("minecraft", "the_nether"))) {
                netherWorld = world;
                break;
            }
        }
        
        if (netherWorld == null) {
            return; // No nether world found
        }
        
        // Check each player in the nether
        List<ServerPlayerEntity> playersInNether = netherWorld.getPlayers();
        
        for (ServerPlayerEntity player : playersInNether) {
            checkAndSpawnPhantom(player, netherWorld);
        }
        
        // Clean up cooldowns for offline players - use player names instead of objects
        playerCooldowns.entrySet().removeIf(entry -> {
            String playerName = entry.getKey();
            return server.getPlayerManager().getPlayer(playerName) == null;
        });
        
        // Reduce all cooldowns
        for (String playerName : playerCooldowns.keySet()) {
            Integer cooldown = playerCooldowns.get(playerName);
            if (cooldown != null && cooldown > 0) {
                playerCooldowns.put(playerName, cooldown - CHECK_INTERVAL);
            }
        }
    }
    
    /**
     * Check if we should spawn a phantom for a player and spawn it if conditions are met
     */
    private static void checkAndSpawnPhantom(ServerPlayerEntity player, ServerWorld world) {
        String playerName = player.getName().getString();
        
        // Skip creative/spectator players
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        
        // Check cooldown
        Integer cooldown = playerCooldowns.get(playerName);
        if (cooldown != null && cooldown > 0) {
            return;
        }
        
        // Sleep requirement removed - phantoms can spawn anytime in the nether!
        
        // Random chance
        double roll = RANDOM.nextDouble();
        if (roll > SPAWN_CHANCE) {
            return;
        }
        
        // Check for nearby phantoms to avoid spam - increased limit
        BlockPos playerPos = player.getBlockPos();
        List<PhantomEntity> nearbyPhantoms = world.getEntitiesByClass(PhantomEntity.class, 
            player.getBoundingBox().expand(32), phantom -> true); // Reduced search radius
        
        if (nearbyPhantoms.size() >= 4) { // Increased phantom limit
        }
        
        // Try to spawn phantom
        if (spawnPhantomNearPlayer(player, world)) {
            // Set cooldown on successful spawn
            playerCooldowns.put(playerName, PHANTOM_SPAWN_COOLDOWN);
        }
    }
    
    /**
     * Attempt to spawn a phantom near the player - more lenient conditions
     */
    private static boolean spawnPhantomNearPlayer(ServerPlayerEntity player, ServerWorld world) {
        BlockPos playerPos = player.getBlockPos();
        
        // Try multiple spawn locations with more lenient conditions
        for (int attempts = 0; attempts < 15; attempts++) { // Increased attempts
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            int distance = 15 + RANDOM.nextInt(25); // 15-40 blocks away
            int heightOffset = 5 + RANDOM.nextInt(25); // 5-30 blocks above
            
            int spawnX = (int) (playerPos.getX() + Math.cos(angle) * distance);
            int spawnY = playerPos.getY() + heightOffset;
            int spawnZ = (int) (playerPos.getZ() + Math.sin(angle) * distance);
            
            // More lenient nether bounds - allow wider range
            if (spawnY < 5 || spawnY > 250) {
                continue;
            }
            
            BlockPos spawnPos = new BlockPos(spawnX, spawnY, spawnZ);
            
            // More lenient spawn condition - only check current position
            if (world.getBlockState(spawnPos).isAir()) {
                
                PhantomEntity phantom = EntityType.PHANTOM.create(world);
                if (phantom != null) {
                    phantom.setPosition(spawnX + 0.5, spawnY, spawnZ + 0.5);
                    phantom.setTarget(player);
                    
                    // Set phantom size to random value (0-2 for variety)
                    int phantomSize = RANDOM.nextInt(3);
                    phantom.setPhantomSize(phantomSize);
                    
                    if (world.spawnEntity(phantom)) {
                        return true; // Successfully spawned
                    }
                }
            }
        }
        return false; // Failed to spawn
    }
}
