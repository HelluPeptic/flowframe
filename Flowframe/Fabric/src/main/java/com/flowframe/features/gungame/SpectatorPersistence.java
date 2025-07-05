package com.flowframe.features.gungame;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpectatorPersistence {
    private static SpectatorPersistence instance;
    private File spectatorFile;
    private final Set<UUID> spectatorUUIDs = new HashSet<>();
    
    private SpectatorPersistence() {}
    
    public static SpectatorPersistence getInstance() {
        if (instance == null) {
            instance = new SpectatorPersistence();
        }
        return instance;
    }
    
    public void initialize(Object server) {
        try {
            // Use reflection to get the server run directory
            java.lang.reflect.Method getRunDirectory = server.getClass().getMethod("getRunDirectory");
            File serverDir = (File) getRunDirectory.invoke(server);
            
            if (serverDir == null) {
                System.err.println("[FLOWFRAME] Failed to get server directory");
                return;
            }
            
            // Create config directory if it doesn't exist
            File configDir = new File(serverDir, "config/flowframe/battles");
            if (!configDir.exists()) {
                boolean created = configDir.mkdirs();
                if (!created) {
                    System.err.println("[FLOWFRAME] Failed to create config directory: " + configDir.getAbsolutePath());
                    return;
                }
                System.out.println("[FLOWFRAME] Created config directory: " + configDir.getAbsolutePath());
            }
            
            spectatorFile = new File(configDir, "spectators.json");
            System.out.println("[FLOWFRAME] SpectatorPersistence initialized with file: " + spectatorFile.getAbsolutePath());
            loadSpectators();
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to initialize spectator persistence: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if the SpectatorPersistence is properly initialized
     */
    public boolean isInitialized() {
        return spectatorFile != null;
    }
      /**
     * Add a spectator UUID to the persistent list
     */
    public void addSpectator(UUID playerUUID) {
        if (playerUUID == null) {
            System.err.println("[FLOWFRAME] Cannot add null UUID to spectator persistence");
            return;
        }

        if (!isInitialized()) {
            // Silently skip if not initialized - this can happen during server startup/shutdown
            return;
        }

        spectatorUUIDs.add(playerUUID);
        saveSpectators();
        System.out.println("[FLOWFRAME] Added spectator to persistence: " + playerUUID);
    }
      /**
     * Remove a spectator UUID from the persistent list
     */
    public void removeSpectator(UUID playerUUID) {
        if (playerUUID == null) {
            System.err.println("[FLOWFRAME] Cannot remove null UUID from spectator persistence");
            return;
        }

        if (!isInitialized()) {
            // Silently skip if not initialized - this can happen during server startup/shutdown
            return;
        }

        if (spectatorUUIDs.remove(playerUUID)) {
            saveSpectators();
            System.out.println("[FLOWFRAME] Removed spectator from persistence: " + playerUUID);
        }
    }
    
    /**
     * Check if a player is in the spectator persistence list
     */
    public boolean isPersistedSpectator(UUID playerUUID) {
        return spectatorUUIDs.contains(playerUUID);
    }
    
    /**
     * Handle player joining - restore them to survival if they were a spectator
     */
    public void handlePlayerJoin(Object player) {
        try {
            if (player == null) {
                System.err.println("[FLOWFRAME] Cannot handle null player join");
                return;
            }
            
            // Cast directly to ServerPlayerEntity since we know what it is
            net.minecraft.server.network.ServerPlayerEntity serverPlayer = 
                (net.minecraft.server.network.ServerPlayerEntity) player;
            
            UUID playerUUID = serverPlayer.getUuid();
            
            if (playerUUID == null) {
                System.err.println("[FLOWFRAME] Player UUID is null for player: " + serverPlayer.getName().getString());
                return;
            }
            
            if (isPersistedSpectator(playerUUID)) {
                // Set player back to survival mode
                serverPlayer.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                
                // Remove from persistence list
                removeSpectator(playerUUID);
                
                // Send message to player
                serverPlayer.sendMessage(
                    net.minecraft.text.Text.literal("You have been restored to survival mode after leaving during a battle.")
                        .formatted(net.minecraft.util.Formatting.YELLOW),
                    false
                );
                
                System.out.println("[FLOWFRAME] Restored spectator to survival: " + serverPlayer.getName().getString());
            }
        } catch (ClassCastException e) {
            System.err.println("[FLOWFRAME] Error casting player to ServerPlayerEntity: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Error handling player join: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Clear all spectators (called when battle ends)
     */
    public void clearAllSpectators() {
        spectatorUUIDs.clear();
        saveSpectators();
        System.out.println("[FLOWFRAME] Cleared all persistent spectators");
    }
    
    /**
     * Get count of persistent spectators
     */
    public int getSpectatorCount() {
        return spectatorUUIDs.size();
    }
    
    private void loadSpectators() {
        if (!spectatorFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(spectatorFile)) {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            String content = sb.toString().trim();
            
            if (!content.isEmpty()) {
                // Simple JSON parsing - expecting array of UUID strings
                content = content.replace("[", "").replace("]", "").replace("\"", "");
                String[] uuidStrings = content.split(",");
                
                spectatorUUIDs.clear();
                for (String uuidString : uuidStrings) {
                    uuidString = uuidString.trim();
                    if (!uuidString.isEmpty()) {
                        try {
                            UUID uuid = UUID.fromString(uuidString);
                            spectatorUUIDs.add(uuid);
                        } catch (IllegalArgumentException e) {
                            System.err.println("[FLOWFRAME] Invalid UUID in spectators.json: " + uuidString);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to load spectator persistence: " + e.getMessage());
        }
    }
    
    private void saveSpectators() {
        if (spectatorFile == null) {
            System.err.println("[FLOWFRAME] Cannot save spectators: spectatorFile is null. SpectatorPersistence may not be initialized.");
            return;
        }
        
        try (FileWriter writer = new FileWriter(spectatorFile)) {
            // Simple JSON format
            writer.write("[");
            boolean first = true;
            for (UUID uuid : spectatorUUIDs) {
                if (!first) {
                    writer.write(",");
                }
                writer.write("\"" + uuid.toString() + "\"");
                first = false;
            }
            writer.write("]");
        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save spectator persistence: " + e.getMessage());
        }
    }
}
