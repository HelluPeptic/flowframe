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
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

// Handles storage of all logs in a single file
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
    public static class KillLogEntry {
        public final String action = "kill";
        public final String killerName;
        public final String victimName;
        public final BlockPos pos;
        public final String world;
        public final Instant timestamp;
        public KillLogEntry(String killerName, String victimName, BlockPos pos, String world, Instant timestamp) {
            this.killerName = killerName;
            this.victimName = victimName;
            this.pos = pos;
            this.world = world;
            this.timestamp = timestamp;
        }
    }
    private static final List<LogEntry> logs = new ArrayList<>();
    private static final List<BlockLogEntry> blockLogs = new ArrayList<>();
    private static final List<SignLogEntry> signLogs = new ArrayList<>();
    private static final List<KillLogEntry> killLogs = new ArrayList<>();
    private static final Path LOG_FILE = Path.of("config", "flowframe", "minetracer", "logs.json");
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(java.time.Instant.class, new TypeAdapter<java.time.Instant>() {
            @Override
            public void write(JsonWriter out, java.time.Instant value) throws java.io.IOException {
                out.value(value.toString());
            }
            @Override
            public java.time.Instant read(JsonReader in) throws java.io.IOException {
                return java.time.Instant.parse(in.nextString());
            }
        })
        .create();

    private static void loadAllLogs() {
        logs.clear();
        blockLogs.clear();
        signLogs.clear();
        killLogs.clear();
        try {
            Files.createDirectories(LOG_FILE.getParent());
            if (Files.exists(LOG_FILE)) {
                String json = Files.readString(LOG_FILE, StandardCharsets.UTF_8);
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> allLogs = GSON.fromJson(json, type);
                if (allLogs != null) {
                    // Container logs
                    List<Map<String, Object>> containerList = (List<Map<String, Object>>) allLogs.getOrDefault("container", new ArrayList<>());
                    for (Map<String, Object> obj : containerList) {
                        String[] posParts = ((String)obj.get("pos")).split(",");
                        BlockPos pos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
                        try {
                            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse((String)obj.get("itemNbt"));
                            ItemStack stack = ItemStack.fromNbt(nbt);
                            logs.add(new LogEntry((String)obj.get("action"), (String)obj.get("playerName"), pos, stack, java.time.Instant.parse((String)obj.get("timestamp"))));
                        } catch (Exception nbtEx) { nbtEx.printStackTrace(); }
                    }
                    // Block logs
                    List<Map<String, Object>> blockList = (List<Map<String, Object>>) allLogs.getOrDefault("block", new ArrayList<>());
                    for (Map<String, Object> obj : blockList) {
                        String[] posParts = ((String)obj.get("pos")).split(",");
                        BlockPos pos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
                        blockLogs.add(new BlockLogEntry((String)obj.get("action"), (String)obj.get("playerName"), pos, (String)obj.get("blockId"), (String)obj.get("nbt"), java.time.Instant.parse((String)obj.get("timestamp"))));
                    }
                    // Sign logs
                    List<Map<String, Object>> signList = (List<Map<String, Object>>) allLogs.getOrDefault("sign", new ArrayList<>());
                    for (Map<String, Object> obj : signList) {
                        String[] posParts = ((String)obj.get("pos")).split(",");
                        BlockPos pos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
                        signLogs.add(new SignLogEntry((String)obj.get("action"), (String)obj.get("playerName"), pos, (String)obj.get("text"), (String)obj.get("nbt"), java.time.Instant.parse((String)obj.get("timestamp"))));
                    }
                    // Kill logs
                    List<Map<String, Object>> killList = (List<Map<String, Object>>) allLogs.getOrDefault("kill", new ArrayList<>());
                    for (Map<String, Object> obj : killList) {
                        String[] posParts = ((String)obj.get("pos")).split(",");
                        BlockPos pos = new BlockPos(Integer.parseInt(posParts[0]), Integer.parseInt(posParts[1]), Integer.parseInt(posParts[2]));
                        killLogs.add(new KillLogEntry((String)obj.get("killerName"), (String)obj.get("victimName"), pos, (String)obj.get("world"), java.time.Instant.parse((String)obj.get("timestamp"))));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Register server lifecycle hooks for loading/saving logs
    public static void registerServerLifecycle() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            loadAllLogs();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            saveAllLogs();
        });
    }

    public static void logContainerAction(String action, PlayerEntity player, BlockPos pos, ItemStack stack) {
        logs.add(new LogEntry(action, player.getName().getString(), pos, stack, Instant.now()));
        saveAllLogs();
    }

    public static void logBlockAction(String action, PlayerEntity player, BlockPos pos, String blockId, String nbt) {
        BlockLogEntry entry = new BlockLogEntry(action, player.getName().getString(), pos, blockId, nbt, Instant.now());
        blockLogs.add(entry);
        saveAllLogs();
    }

    public static void logSignAction(String action, PlayerEntity player, BlockPos pos, String text, String nbt) {
        String playerName = player != null ? player.getName().getString() : "unknown";
        SignLogEntry entry = new SignLogEntry(action, playerName, pos, text, nbt, Instant.now());
        signLogs.add(entry);
        saveAllLogs();
    }

    public static void logKillAction(String killerName, String victimName, BlockPos pos, String world) {
        killLogs.add(new KillLogEntry(killerName, victimName, pos, world, Instant.now()));
        saveAllLogs();
    }

    public static List<String> getAllPlayerNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (LogEntry entry : logs) {
            names.add(entry.playerName);
        }
        for (BlockLogEntry entry : blockLogs) {
            names.add(entry.playerName);
        }
        for (SignLogEntry entry : signLogs) {
            names.add(entry.playerName);
        }
        return new java.util.ArrayList<>(names);
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
    public static List<KillLogEntry> getKillLogsInRange(BlockPos center, int range, String userFilter) {
        List<KillLogEntry> result = new ArrayList<>();
        int r2 = range * range;
        for (KillLogEntry entry : killLogs) {
            if ((userFilter == null || entry.killerName.equalsIgnoreCase(userFilter)) &&
                entry.pos.getSquaredDistance(center.getX(), center.getY(), center.getZ()) <= r2) {
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
    private static class BlockLogEntryJson {
        String action;
        String playerName;
        String pos;
        String blockId;
        String nbt;
        String timestamp;
        BlockLogEntryJson(BlockLogEntry entry) {
            this.action = entry.action;
            this.playerName = entry.playerName;
            this.pos = entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ();
            this.blockId = entry.blockId;
            this.nbt = entry.nbt;
            this.timestamp = entry.timestamp.toString();
        }
    }
    private static class SignLogEntryJson {
        String action;
        String playerName;
        String pos;
        String text;
        String nbt;
        String timestamp;
        SignLogEntryJson(SignLogEntry entry) {
            this.action = entry.action;
            this.playerName = entry.playerName;
            this.pos = entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ();
            this.text = entry.text;
            this.nbt = entry.nbt;
            this.timestamp = entry.timestamp.toString();
        }
    }
    private static class KillLogEntryJson {
        String action = "kill";
        String killerName;
        String victimName;
        String pos;
        String world;
        String timestamp;
        KillLogEntryJson(KillLogEntry entry) {
            this.killerName = entry.killerName;
            this.victimName = entry.victimName;
            this.pos = entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ();
            this.world = entry.world;
            this.timestamp = entry.timestamp.toString();
        }
    }

    private static void saveAllLogs() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Map<String, Object> allLogs = new HashMap<>();
            List<Object> containerList = new ArrayList<>();
            for (LogEntry entry : logs) containerList.add(new LogEntryJson(entry));
            allLogs.put("container", containerList);
            List<Object> blockList = new ArrayList<>();
            for (BlockLogEntry entry : blockLogs) blockList.add(new BlockLogEntryJson(entry));
            allLogs.put("block", blockList);
            List<Object> signList = new ArrayList<>();
            for (SignLogEntry entry : signLogs) signList.add(new SignLogEntryJson(entry));
            allLogs.put("sign", signList);
            List<Object> killList = new ArrayList<>();
            for (KillLogEntry entry : killLogs) killList.add(new KillLogEntryJson(entry));
            allLogs.put("kill", killList);
            String json = GSON.toJson(allLogs);
            Files.writeString(LOG_FILE, json, StandardCharsets.UTF_8);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void logInventoryAction(String action, PlayerEntity player, ItemStack stack) {
        // Use BlockPos.ORIGIN (0,0,0) to mark inventory logs
        logs.add(new LogEntry(action, player.getName().getString(), BlockPos.ORIGIN, stack, Instant.now()));
        saveAllLogs();
    }
}
