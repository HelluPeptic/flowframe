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
import com.google.gson.reflect.TypeToken;

public class DataManager {
    private static final Gson GSON = new Gson();
    private final Path file;
    private final Set<UUID> optedOut = new HashSet<>();

    public DataManager(Path file) {
        this.file = file;
        load();
    }

    public boolean isOptedOut(UUID uuid) {
        return optedOut.contains(uuid);
    }

    public boolean toggle(UUID uuid) {
        boolean nowOptedOut = optedOut.contains(uuid);
        if (nowOptedOut) {
            optedOut.remove(uuid);
        } else {
            optedOut.add(uuid);
        }
        save();
        return !nowOptedOut;
    }

    public void addSeenPlayer(UUID uuid) {
        optedOut.add(uuid);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Set<String> values = GSON.fromJson(reader, new TypeToken<Set<String>>() {}.getType());
            if (values == null) {
                return;
            }
            for (String value : values) {
                try {
                    optedOut.add(UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(optedOut.stream().map(UUID::toString).toList(), writer);
            }
        } catch (IOException ignored) {
        }
    }
}
