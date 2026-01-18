package com.flowframe.config;

public class FlowframeConfig {
    private static boolean globalEndPortalEnabled = false; // Disabled by default
    private static double pathBlockSpeedMultiplier = 1.3; // 30% faster
    
    public static void init() {
        // Configuration initialization
    }
    
    public static boolean isEndPortalEnabled() {
        return globalEndPortalEnabled;
    }
    
    public static void setEndPortalEnabled(boolean enabled) {
        globalEndPortalEnabled = enabled;
    }
    
    public static double getPathBlockSpeedMultiplier() {
        return pathBlockSpeedMultiplier;
    }
    
    public static void setPathBlockSpeedMultiplier(double multiplier) {
        pathBlockSpeedMultiplier = multiplier;
    }
}
