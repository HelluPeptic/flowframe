package com.flowframe.config;

import net.minecraft.server.MinecraftServer;

public class FlowframeConfig {
    private static boolean globalEndPortalEnabled = false; // Disabled by default
    private static double pathBlockSpeedMultiplier = 1.3; // 30% faster
    private static MinecraftServer serverRef = null;

    public static void init() {
        // Configuration initialization
    }

    public static void load(MinecraftServer server) {
        serverRef = server;
        globalEndPortalEnabled = FlowframeConfigPersistence.loadEndPortalEnabled(server);
    }

    public static void save() {
        if (serverRef != null) {
            FlowframeConfigPersistence.saveConfig(serverRef, globalEndPortalEnabled);
        }
    }

    public static boolean isEndPortalEnabled() {
        return globalEndPortalEnabled;
    }

    public static void setEndPortalEnabled(boolean enabled) {
        globalEndPortalEnabled = enabled;
        save();
    }

    public static double getPathBlockSpeedMultiplier() {
        return pathBlockSpeedMultiplier;
    }

    public static void setPathBlockSpeedMultiplier(double multiplier) {
        pathBlockSpeedMultiplier = multiplier;
    }
}
