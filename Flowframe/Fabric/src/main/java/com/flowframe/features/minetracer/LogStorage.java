package com.flowframe.features.minetracer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtHelper;

// Handles storage of chest/container logs
public class LogStorage {
    public static class LogEntry {
        public final String action; // "insert" or "remove"
        public final String playerName;
        public final BlockPos pos;
        public final ItemStack stack;
        public final Instant timestamp;
        public LogEntry(String action, String playerName, BlockPos pos, ItemStack stack, Instant timestamp) {
            this.action = action;
            this.playerName = playerName;
            this.pos = pos;
            this.stack = stack.copy();
            this.timestamp = timestamp;
        }
    }
    public static class BlockLogEntry {
        public final String action; // "place" or "remove"
        public final String playerName;
        public final BlockPos pos;
        public final String blockId;
        public final String nbt;
        public final Instant timestamp;
        public BlockLogEntry(String action, String playerName, BlockPos pos, String blockId, String nbt, Instant timestamp) {
            this.action = action;
            this.playerName = playerName;
            this.pos = pos;
            this.blockId = blockId;
            this.nbt = nbt;
            this.timestamp = timestamp;
        }
    }
    public static class SignLogEntry {
        public final String action; // "place", "remove", "edit"
        public final String playerName;
        public final BlockPos pos;
        public final String text;
        public final String nbt;
        public final Instant timestamp;
        public SignLogEntry(String action, String playerName, BlockPos pos, String text, String nbt, Instant timestamp) {
            this.action = action;
            this.playerName = playerName;
            this.pos = pos;
            this.text = text;
            this.nbt = nbt;
            this.timestamp = timestamp;
        }
    }
    private static final List<LogEntry> logs = new ArrayList<>();
    private static final List<BlockLogEntry> blockLogs = new ArrayList<>();
    private static final List<SignLogEntry> signLogs = new ArrayList<>();
    private static final Path LOG_DIR = Path.of("config", "flowframe", "minetracer");
    private static final Path BLOCK_LOG_DIR = Path.of("config", "flowframe", "minetracer", "blocks");
    private static final Path SIGN_LOG_DIR = Path.of("config", "flowframe", "minetracer", "signs");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        // Load logs from disk on startup
        try {
            java.nio.file.Files.createDirectories(LOG_DIR);
            java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(LOG_DIR);
            files.filter(f -> f.getFileName().toString().startsWith("log-") && f.getFileName().toString().endsWith(".json"))
                .forEach(f -> {
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            LogEntryJson json = GSON.fromJson(line, LogEntryJson.class);
                            String[] posParts = json.pos.split(",");
                            BlockPos pos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
                            try {
                                net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(json.itemNbt);
                                ItemStack stack = ItemStack.fromNbt(nbt);
                                logs.add(new LogEntry(json.action, json.playerName, pos, stack, java.time.Instant.parse(json.timestamp)));
                            } catch (Exception nbtEx) { nbtEx.printStackTrace(); }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                });
            // Load block logs
            java.nio.file.Files.createDirectories(BLOCK_LOG_DIR);
            java.util.stream.Stream<java.nio.file.Path> blockFiles = java.nio.file.Files.list(BLOCK_LOG_DIR);
            blockFiles.filter(f -> f.getFileName().toString().startsWith("block-") && f.getFileName().toString().endsWith(".json"))
                .forEach(f -> {
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            BlockLogEntry entry = GSON.fromJson(line, BlockLogEntry.class);
                            blockLogs.add(entry);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                });
            // Load sign logs
            java.nio.file.Files.createDirectories(SIGN_LOG_DIR);
            java.util.stream.Stream<java.nio.file.Path> signFiles = java.nio.file.Files.list(SIGN_LOG_DIR);
            signFiles.filter(f -> f.getFileName().toString().startsWith("sign-") && f.getFileName().toString().endsWith(".json"))
                .forEach(f -> {
                    try {
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8);
                        for (String line : lines) {
                            SignLogEntry entry = GSON.fromJson(line, SignLogEntry.class);
                            signLogs.add(entry);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                });
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void logContainerAction(String action, PlayerEntity player, BlockPos pos, ItemStack stack) {
        logs.add(new LogEntry(action, player.getName().getString(), pos, stack, Instant.now()));
        saveLogToFile(logs.get(logs.size() - 1));
    }

    private static void saveLogToFile(LogEntry entry) {
        try {
            Files.createDirectories(LOG_DIR);
            String fileName = "log-" + entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ() + ".json";
            Path file = LOG_DIR.resolve(fileName);
            String json = GSON.toJson(new LogEntryJson(entry));
            Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8, Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper class for JSON serialization
    private static class LogEntryJson {
        String action;
        String playerName;
        String pos;
        String itemNbt;
        String timestamp;
        LogEntryJson(LogEntry entry) {
            this.action = entry.action;
            this.playerName = entry.playerName;
            this.pos = entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ();
            this.itemNbt = entry.stack.writeNbt(new NbtCompound()).toString();
            this.timestamp = entry.timestamp.toString();
        }
    }

    public static List<LogEntry> getLogsFor(BlockPos pos) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : logs) {
            if (entry.pos.equals(pos)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<LogEntry> getLogsInRange(BlockPos center, int range) {
        List<LogEntry> result = new ArrayList<>();
        int r2 = range * range;
        for (LogEntry entry : logs) {
            if (entry.pos.getSquaredDistance(center.getX(), center.getY(), center.getZ()) <= r2) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<String> getAllPlayerNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (LogEntry entry : logs) {
            names.add(entry.playerName);
        }
        return new java.util.ArrayList<>(names);
    }

    public static void logBlockAction(String action, PlayerEntity player, BlockPos pos, String blockId, String nbt) {
        BlockLogEntry entry = new BlockLogEntry(action, player.getName().getString(), pos, blockId, nbt, Instant.now());
        blockLogs.add(entry);
        saveBlockLogToFile(entry);
    }
    private static void saveBlockLogToFile(BlockLogEntry entry) {
        try {
            Files.createDirectories(BLOCK_LOG_DIR);
            String fileName = "block-" + entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ() + ".json";
            Path file = BLOCK_LOG_DIR.resolve(fileName);
            String json = GSON.toJson(entry);
            Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8, Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void logSignAction(String action, PlayerEntity player, BlockPos pos, String text, String nbt) {
        String playerName = player != null ? player.getName().getString() : "unknown";
        SignLogEntry entry = new SignLogEntry(action, playerName, pos, text, nbt, Instant.now());
        signLogs.add(entry);
        saveSignLogToFile(entry);
    }
    private static void saveSignLogToFile(SignLogEntry entry) {
        try {
            Files.createDirectories(SIGN_LOG_DIR);
            String fileName = "sign-" + entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ() + ".json";
            Path file = SIGN_LOG_DIR.resolve(fileName);
            String json = GSON.toJson(entry);
            Files.writeString(file, json + System.lineSeparator(), StandardCharsets.UTF_8, Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static List<BlockLogEntry> getBlockLogsInRange(BlockPos center, int range, String userFilter) {
        List<BlockLogEntry> result = new ArrayList<>();
        int r2 = range * range;
        for (BlockLogEntry entry : blockLogs) {
            if ((userFilter == null || entry.playerName.equalsIgnoreCase(userFilter)) &&
                entry.pos.getSquaredDistance(center.getX(), center.getY(), center.getZ()) <= r2) {
                result.add(entry);
            }
        }
        return result;
    }
    public static List<SignLogEntry> getSignLogsInRange(BlockPos center, int range, String userFilter) {
        List<SignLogEntry> result = new ArrayList<>();
        int r2 = range * range;
        for (SignLogEntry entry : signLogs) {
            if ((userFilter == null || entry.playerName.equalsIgnoreCase(userFilter)) &&
                entry.pos.getSquaredDistance(center.getX(), center.getY(), center.getZ()) <= r2) {
                result.add(entry);
            }
        }
        return result;
    }
    public static void logInventoryAction(String action, PlayerEntity player, ItemStack stack) {
        // Use BlockPos.ORIGIN (0,0,0) to mark inventory logs
        logs.add(new LogEntry(action, player.getName().getString(), BlockPos.ORIGIN, stack, Instant.now()));
        saveLogToFile(logs.get(logs.size() - 1));
    }
}
