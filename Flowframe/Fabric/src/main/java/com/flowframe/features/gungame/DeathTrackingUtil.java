package com.flowframe.features.gungame;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for tracking recent deaths to prevent spam
 * This is separate from the mixin to avoid public static method restrictions
 */
public class DeathTrackingUtil {
    // Track recent death processing to prevent spam (player UUID -> last death time)
    private static final Map<UUID, Long> recentDeaths = new ConcurrentHashMap<>();
    private static final long DEATH_COOLDOWN_MS = 1000; // 1 second cooldown
    
    /**
     * Check if a player's death should be processed (not in cooldown)
     */
    public static boolean shouldProcessDeath(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        Long lastDeathTime = recentDeaths.get(playerId);
        if (lastDeathTime != null && (currentTime - lastDeathTime) < DEATH_COOLDOWN_MS) {
            return false; // Too soon since last death processing, ignore
        }
        
        // Record this death processing time
        recentDeaths.put(playerId, currentTime);
        return true;
    }
    
    /**
     * Clean up death tracking for a player (call when player leaves battle)
     */
    public static void cleanupDeathTracking(UUID playerId) {
        recentDeaths.remove(playerId);
    }
    
    /**
     * Clean up all death tracking (call when battle ends)  
     */
    public static void cleanupAllDeathTracking() {
        recentDeaths.clear();
    }
}
