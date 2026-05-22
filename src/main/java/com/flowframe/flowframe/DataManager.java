package com.flowframe.flowframe;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manages persistent per-player data (currently: keep-inventory opt-outs).
 * Stores opted-out UUIDs in plugins/Flowframe/keepinv_optout.txt — one UUID per line.
 */
public class DataManager {

    private final JavaPlugin plugin;
    private final Set<UUID> optedOut = new HashSet<>();
    private final File dataFile;

    public DataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "keepinv_optout.txt");
        load();
    }

    /** Returns true if the player has opted OUT of keep inventory. */
    public boolean isOptedOut(UUID uuid) {
        return optedOut.contains(uuid);
    }

    /**
     * Toggles the opt-out status for the given player.
     *
     * @return true if the player is now opted OUT, false if they are back in.
     */
    public boolean toggle(UUID uuid) {
        boolean nowOptedOut;
        if (optedOut.contains(uuid)) {
            optedOut.remove(uuid);
            nowOptedOut = false;
        } else {
            optedOut.add(uuid);
            nowOptedOut = true;
        }
        save();
        return nowOptedOut;
    }

    private void load() {
        if (!dataFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    optedOut.add(UUID.fromString(line));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid UUID in keepinv_optout.txt: " + line);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load keepinv data: " + e.getMessage());
        }
    }

    private void save() {
        plugin.getDataFolder().mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
            for (UUID uuid : optedOut) {
                writer.write(uuid.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save keepinv data: " + e.getMessage());
        }
    }
}
