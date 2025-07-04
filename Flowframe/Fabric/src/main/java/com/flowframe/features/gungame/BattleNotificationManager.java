package com.flowframe.features.gungame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BattleNotificationManager {
    private static BattleNotificationManager instance;
    private final Map<UUID, Boolean> playerNotificationSettings = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File configFile;
    
    public static BattleNotificationManager getInstance() {
        if (instance == null) {
            instance = new BattleNotificationManager();
        }
        return instance;
    }
    
    public void initialize(MinecraftServer server) {
        // Create config directory if it doesn't exist
        File configDir = new File(server.getRunDirectory(), "config/flowframe");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        configFile = new File(configDir, "battlenotifications.json");
        loadSettings();
    }
    
    /**
     * Toggle notification setting for a player
     * @param playerId The player's UUID
     * @return The new notification setting (true = enabled, false = disabled)
     */
    public boolean toggleNotifications(UUID playerId) {
        boolean currentSetting = playerNotificationSettings.getOrDefault(playerId, true);
        boolean newSetting = !currentSetting;
        playerNotificationSettings.put(playerId, newSetting);
        saveSettings();
        return newSetting;
    }
    
    /**
     * Check if a player should receive battle notifications
     * @param playerId The player's UUID
     * @return true if player should receive notifications, false otherwise
     */
    public boolean shouldReceiveNotifications(UUID playerId) {
        return playerNotificationSettings.getOrDefault(playerId, true); // Default to enabled
    }
    
    /**
     * Get the current notification setting for a player
     * @param playerId The player's UUID
     * @return true if notifications are enabled, false if disabled
     */
    public boolean isNotificationsEnabled(UUID playerId) {
        return playerNotificationSettings.getOrDefault(playerId, true);
    }
    
    private void loadSettings() {
        if (!configFile.exists()) {
            // File doesn't exist yet, start with empty settings
            return;
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            Type type = new TypeToken<Map<String, Boolean>>(){}.getType();
            Map<String, Boolean> stringMap = gson.fromJson(reader, type);
            
            if (stringMap != null) {
                playerNotificationSettings.clear();
                // Convert string UUIDs back to UUID objects
                for (Map.Entry<String, Boolean> entry : stringMap.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        playerNotificationSettings.put(playerId, entry.getValue());
                    } catch (IllegalArgumentException e) {
                        // Skip invalid UUIDs
                        System.err.println("[FLOWFRAME] Invalid UUID in battlenotifications.json: " + entry.getKey());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to load battle notification settings: " + e.getMessage());
        }
    }
    
    private void saveSettings() {
        try {
            // Convert UUID keys to strings for JSON serialization
            Map<String, Boolean> stringMap = new HashMap<>();
            for (Map.Entry<UUID, Boolean> entry : playerNotificationSettings.entrySet()) {
                stringMap.put(entry.getKey().toString(), entry.getValue());
            }
            
            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(stringMap, writer);
            }
        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save battle notification settings: " + e.getMessage());
        }
    }
}
