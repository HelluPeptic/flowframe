package com.flowframe.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FlowframeConfigPersistence {
    private static final String CONFIG_FILE = "flowframe_config.json";

    public static void saveConfig(MinecraftServer server, boolean endPortalEnabled, boolean netherPortalEnabled) {
        try {
            File file = getConfigFile(server);
            JsonObject root = new JsonObject();
            root.addProperty("endPortalEnabled", endPortalEnabled);
            root.addProperty("netherPortalEnabled", netherPortalEnabled);
            try (FileWriter writer = new FileWriter(file)) {
                new Gson().toJson(root, writer);
            }
        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save config: " + e.getMessage());
        }
    }

    public static boolean loadEndPortalEnabled(MinecraftServer server) {
        try {
            File file = getConfigFile(server);
            if (!file.exists()) return false;
            try (FileReader reader = new FileReader(file)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                if (root.has("endPortalEnabled")) {
                    return root.get("endPortalEnabled").getAsBoolean();
                }
            }
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to load config: " + e.getMessage());
        }
        return false;
    }

    public static boolean loadNetherPortalEnabled(MinecraftServer server) {
        try {
            File file = getConfigFile(server);
            if (!file.exists()) return true; // Default enabled
            try (FileReader reader = new FileReader(file)) {
                JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                if (root.has("netherPortalEnabled")) {
                    return root.get("netherPortalEnabled").getAsBoolean();
                }
            }
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to load nether portal config: " + e.getMessage());
        }
        return true; // Default enabled
    }

    private static File getConfigFile(MinecraftServer server) {
        return new File(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), CONFIG_FILE);
    }
}
