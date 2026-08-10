package com.flowframe.flowframe;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Persists all of Flowframe's small pieces of server state to a single JSON file:
 *   - which players have opted out of keep-inventory
 *   - which players have joined before (for the "first time" welcome message)
 *   - whether End portals are currently enabled
 *
 * Every mutating call saves immediately, so nothing here depends on a clean
 * shutdown to be persisted correctly.
 */
public class DataManager {

    /** On-disk shape of the persisted state. Field names double as the JSON keys. */
    private static class State {
        Set<String> optedOut = new HashSet<>();
        Set<String> seenPlayers = new HashSet<>();
        boolean endPortalEnabled = false;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Set<UUID> optedOut = new HashSet<>();
    private final Set<UUID> seenPlayers = new HashSet<>();
    private boolean endPortalEnabled = false;

    public DataManager(Path file) {
        this.file = file;
        load();
    }

    // --- Keep inventory opt-out ---

    public boolean isOptedOut(UUID uuid) {
        return optedOut.contains(uuid);
    }

    /** @return true if the player is now opted OUT, false if they're back in. */
    public boolean toggle(UUID uuid) {
        boolean nowOptedOut = !optedOut.contains(uuid);
        if (nowOptedOut) {
            optedOut.add(uuid);
        } else {
            optedOut.remove(uuid);
        }
        save();
        return nowOptedOut;
    }

    // --- First-join tracking ---

    public boolean hasSeenPlayer(UUID uuid) {
        return seenPlayers.contains(uuid);
    }

    public void markSeenPlayer(UUID uuid) {
        if (seenPlayers.add(uuid)) {
            save();
        }
    }

    // --- End portal toggle ---

    public boolean isEndPortalEnabled() {
        return endPortalEnabled;
    }

    public void setEndPortalEnabled(boolean enabled) {
        this.endPortalEnabled = enabled;
        save();
    }

    private void load() {
        if (!Files.exists(file)) {
            migrateLegacyKeepInvFile();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            State state = GSON.fromJson(reader, State.class);
            if (state == null) {
                return;
            }
            if (state.optedOut != null) {
                for (String value : state.optedOut) {
                    addUuidIfValid(optedOut, value);
                }
            }
            if (state.seenPlayers != null) {
                for (String value : state.seenPlayers) {
                    addUuidIfValid(seenPlayers, value);
                }
            }
            endPortalEnabled = state.endPortalEnabled;
        } catch (IOException | RuntimeException ignored) {
            // Covers both file I/O errors and malformed/unexpected JSON (e.g. GSON's
            // JsonSyntaxException), so a corrupt file can't crash mod init.
        }
    }

    /**
     * Older versions of this mod stored opt-outs as a bare JSON array of UUID strings
     * in "keepinv.json". If that file exists and the new combined file doesn't, pull
     * the opt-outs over so upgrading doesn't silently lose everyone's preference.
     */
    private void migrateLegacyKeepInvFile() {
        if (file.getParent() == null) {
            return;
        }
        Path legacyFile = file.getParent().resolve("keepinv.json");
        if (!Files.exists(legacyFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(legacyFile, StandardCharsets.UTF_8)) {
            Set<String> values = GSON.fromJson(reader, new com.google.gson.reflect.TypeToken<Set<String>>() {}.getType());
            if (values == null) {
                return;
            }
            for (String value : values) {
                addUuidIfValid(optedOut, value);
            }
            if (!optedOut.isEmpty()) {
                save();
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void addUuidIfValid(Set<UUID> target, String value) {
        try {
            target.add(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            State state = new State();
            for (UUID uuid : optedOut) {
                state.optedOut.add(uuid.toString());
            }
            for (UUID uuid : seenPlayers) {
                state.seenPlayers.add(uuid.toString());
            }
            state.endPortalEnabled = endPortalEnabled;
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException ignored) {
        }
    }
}