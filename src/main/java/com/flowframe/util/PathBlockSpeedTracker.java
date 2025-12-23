package com.flowframe.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PathBlockSpeedTracker {
    private static final Map<UUID, Long> lastPathBlockTime = new HashMap<>();
    private static final long GRACE_PERIOD_MS = 1000; // 1 second grace period
    
    public static void updatePathBlockTime(UUID playerId) {
        lastPathBlockTime.put(playerId, System.currentTimeMillis());
    }
    
    public static boolean shouldApplySpeedBoost(UUID playerId) {
        Long lastTime = lastPathBlockTime.get(playerId);
        if (lastTime == null) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastPath = currentTime - lastTime;
        
        return timeSinceLastPath <= GRACE_PERIOD_MS;
    }
    
    public static void removePlayer(UUID playerId) {
        lastPathBlockTime.remove(playerId);
    }
}
