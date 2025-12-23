package com.flowframe.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlowframeConfig {
    private static final Map<UUID, Boolean> endPortalToggle = new HashMap<>();
    private static double pathBlockSpeedMultiplier = 1.3; // 30% faster
    
    public static void init() {
        // Configuration initialization
    }
    
    public static boolean isEndPortalEnabled(UUID playerId) {
        return endPortalToggle.getOrDefault(playerId, false);
    }
    
    public static void setEndPortalEnabled(UUID playerId, boolean enabled) {
        endPortalToggle.put(playerId, enabled);
    }
    
    public static double getPathBlockSpeedMultiplier() {
        return pathBlockSpeedMultiplier;
    }
    
    public static void setPathBlockSpeedMultiplier(double multiplier) {
        pathBlockSpeedMultiplier = multiplier;
    }
}
