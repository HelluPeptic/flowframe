package com.flowframe.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FlowframeConfig {
    private static final String CONFIG_DIR = "config/flowframe";
    private static final String CONFIG_FILE = "flowframe-config.json";
    private static FlowframeConfig instance;
    
    // Configuration options
    public boolean enableMapRemoval = false; // Disabled by default
    
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
}