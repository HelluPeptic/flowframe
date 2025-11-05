package com.flowframe.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FlowframeConfig {
    private static final String CONFIG_DIR = "config/flowframe";
    private static final String CONFIG_FILE = "flowframe-config.json";
    private static FlowframeConfig instance;
    
    // Configuration options
    public boolean enableMapRemoval = false; // Disabled by default
    public boolean enableTrashBagRemoval = true; // Disabled by default
    public boolean enableBanglumNukeCoreRemoval = true; // Disabled by default
    public boolean enableKeepInventory = false; // Disabled by default
    public boolean enablePathSpeed = false; // Disabled by default
    public double pathSpeedModifier = 0.3; // 30% speed increase by default
    public double rainSkipPercentage = 0.0; // 0% chance to skip rain by default
    public boolean rainNotifications = false; // Notifications disabled by default
    
    private FlowframeConfig() {}
    
    public static FlowframeConfig getInstance() {
        if (instance == null) {
            instance = loadConfig();
        }
        return instance;
    }
    
    private static FlowframeConfig loadConfig() {
        File configDir = new File(CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        File configFile = new File(configDir, CONFIG_FILE);
        
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                Gson gson = new Gson();
                FlowframeConfig config = gson.fromJson(reader, FlowframeConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                System.out.println("[FLOWFRAME] Failed to load config, using defaults: " + e.getMessage());
            }
        }
        
        // Create default config
        FlowframeConfig defaultConfig = new FlowframeConfig();
        defaultConfig.saveConfig();
        return defaultConfig;
    }
    
    public void saveConfig() {
        try {
            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            File configFile = new File(configDir, CONFIG_FILE);
            try (FileWriter writer = new FileWriter(configFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(this, writer);
                System.out.println("[FLOWFRAME] Config saved to: " + configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("[FLOWFRAME] Failed to save config: " + e.getMessage());
        }
    }
    
    public void setMapRemovalEnabled(boolean enabled) {
        this.enableMapRemoval = enabled;
        saveConfig();
    }
    
    public boolean isMapRemovalEnabled() {
        return enableMapRemoval;
    }
    
    public void setTrashBagRemovalEnabled(boolean enabled) {
        this.enableTrashBagRemoval = enabled;
        saveConfig();
    }
    
    public boolean isTrashBagRemovalEnabled() {
        return enableTrashBagRemoval;
    }
    
    public void setBanglumNukeCoreRemovalEnabled(boolean enabled) {
        this.enableBanglumNukeCoreRemoval = enabled;
        saveConfig();
    }
    
    public boolean isBanglumNukeCoreRemovalEnabled() {
        return enableBanglumNukeCoreRemoval;
    }
    
    public void setKeepInventoryEnabled(boolean enabled) {
        this.enableKeepInventory = enabled;
        saveConfig();
    }
    
    public boolean isKeepInventoryEnabled() {
        return enableKeepInventory;
    }
    
    public void setPathSpeedEnabled(boolean enabled) {
        this.enablePathSpeed = enabled;
        saveConfig();
    }
    
    public boolean isPathSpeedEnabled() {
        return enablePathSpeed;
    }
    
    public void setPathSpeedModifier(double modifier) {
        this.pathSpeedModifier = modifier;
        saveConfig();
    }
    
    public double getPathSpeedModifier() {
        return pathSpeedModifier;
    }
    
    public void setRainSkipPercentage(double percentage) {
        this.rainSkipPercentage = Math.max(0.0, Math.min(1.0, percentage)); // Clamp between 0 and 1
        saveConfig();
    }
    
    public double getRainSkipPercentage() {
        return rainSkipPercentage;
    }
    
    public void setRainNotifications(boolean enabled) {
        this.rainNotifications = enabled;
        saveConfig();
    }
    
    public boolean isRainNotificationsEnabled() {
        return rainNotifications;
    }
}