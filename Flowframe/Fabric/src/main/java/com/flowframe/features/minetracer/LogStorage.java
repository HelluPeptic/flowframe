package com.flowframe.features.minetracer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
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
    
    // Batching system for improved performance
    private static final Object saveLock = new Object();
    private static volatile boolean hasUnsavedChanges = false;
    private static volatile boolean isShuttingDown = false;
    private static java.util.concurrent.ScheduledExecutorService saveScheduler;
    
    // Asynchronous logging system
    private static java.util.concurrent.ExecutorService asyncLogExecutor;
    private static final java.util.concurrent.BlockingQueue<Runnable> logQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    
    // Performance optimization: Batch logging to reduce overhead
    private static final int BATCH_SIZE = 50;
    private static final List<LogEntry> pendingLogs = new ArrayList<>();
    private static final List<BlockLogEntry> pendingBlockLogs = new ArrayList<>();
    private static final List<SignLogEntry> pendingSignLogs = new ArrayList<>();
    private static final List<KillLogEntry> pendingKillLogs = new ArrayList<>();
    
    // Configuration options for performance tuning
    public static boolean ENABLE_DETAILED_NBT_LOGGING = true; // Enable detailed NBT by default
    public static boolean ENABLE_CONTAINER_LOGGING = true;
    public static boolean ENABLE_BLOCK_LOGGING = true;
    public static boolean ENABLE_SIGN_LOGGING = true;
    public static boolean ENABLE_KILL_LOGGING = true;
    
    // Cache frequently used objects to reduce allocations
    private static final ThreadLocal<java.time.format.DateTimeFormatter> DATE_FORMATTER = 
        ThreadLocal.withInitial(() -> java.time.format.DateTimeFormatter.ISO_INSTANT);
    
    // Reusable string builders for coordinate formatting
    private static final ThreadLocal<StringBuilder> COORD_BUILDER = 
        ThreadLocal.withInitial(() -> new StringBuilder(32));
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
        synchronized (saveLock) {
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
                hasUnsavedChanges = false;
                System.out.println("[MineTracer] Loaded " + 
                    (logs.size() + blockLogs.size() + signLogs.size() + killLogs.size()) + 
                    " log entries from disk");
            } catch (Exception e) { 
                System.err.println("[MineTracer] Failed to load logs: " + e.getMessage());
                e.printStackTrace(); 
            }
        }
    }

    // Public method to force immediate save (for critical situations)
    public static void forceSave() {
        saveAllLogsNow();
    }

    // Register server lifecycle hooks for loading/saving logs
    public static void registerServerLifecycle() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            loadAllLogs();
            startPeriodicSaving();
            startAsyncLogging();
            com.flowframe.features.minetracer.KillLogger.register(); // Register kill logging
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            isShuttingDown = true;
            stopAsyncLogging();
            stopPeriodicSaving();
            saveAllLogsNow(); // Final save on shutdown
        });
    }

    // Start the periodic saving task
    private static void startPeriodicSaving() {
        if (saveScheduler == null || saveScheduler.isShutdown()) {
            saveScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MineTracer-LogSaver");
                t.setDaemon(true); // Don't prevent JVM shutdown
                return t;
            });
            
            // Save every 30 seconds if there are unsaved changes
            saveScheduler.scheduleWithFixedDelay(() -> {
                if (hasUnsavedChanges && !isShuttingDown) {
                    saveAllLogsNow();
                }
            }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    // Stop the periodic saving task
    private static void stopPeriodicSaving() {
        if (saveScheduler != null && !saveScheduler.isShutdown()) {
            saveScheduler.shutdown();
            try {
                if (!saveScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    saveScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                saveScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Start the asynchronous logging system
    private static void startAsyncLogging() {
        if (asyncLogExecutor == null || asyncLogExecutor.isShutdown()) {
            asyncLogExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "MineTracer-AsyncLogger");
                t.setDaemon(true); // Don't prevent JVM shutdown
                return t;
            });
            
            // Start the async log processor
            asyncLogExecutor.submit(() -> {
                while (!isShuttingDown && !Thread.currentThread().isInterrupted()) {
                    try {
                        Runnable logTask = logQueue.take(); // Blocks until available
                        logTask.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        System.err.println("[MineTracer] Error in async logging: " + e.getMessage());
                    }
                }
            });
        }
    }

    // Stop the asynchronous logging system
    private static void stopAsyncLogging() {
        if (asyncLogExecutor != null && !asyncLogExecutor.isShutdown()) {
            // Process any remaining log entries
            while (!logQueue.isEmpty()) {
                try {
                    Runnable logTask = logQueue.poll();
                    if (logTask != null) {
                        logTask.run();
                    }
                } catch (Exception e) {
                    System.err.println("[MineTracer] Error processing remaining logs: " + e.getMessage());
                }
            }
            
            asyncLogExecutor.shutdown();
            try {
                if (!asyncLogExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    asyncLogExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncLogExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void logContainerAction(String action, PlayerEntity player, BlockPos pos, ItemStack stack) {
        if (!ENABLE_CONTAINER_LOGGING) return;
        
        // Early exit for empty stacks to reduce noise
        if (stack.isEmpty()) return;
        
        // Create copies of data to avoid issues with async processing
        final String finalAction = action;
        final String finalPlayerName = player.getName().getString();
        final BlockPos finalPos = pos;
        final ItemStack finalStack = stack.copy();
        final Instant finalTimestamp = Instant.now();
        
        // Submit to async queue for processing
        logQueue.offer(() -> {
            synchronized (saveLock) {
                pendingLogs.add(new LogEntry(finalAction, finalPlayerName, finalPos, finalStack, finalTimestamp));
                
                // Flush batch if it's getting large
                if (pendingLogs.size() >= BATCH_SIZE) {
                    flushPendingLogs();
                }
                hasUnsavedChanges = true;
            }
        });
    }

    public static void logBlockAction(String action, PlayerEntity player, BlockPos pos, String blockId, String nbt) {
        if (!ENABLE_BLOCK_LOGGING) return;
        
        // Create copies of data to avoid issues with async processing
        final String finalAction = action;
        final String finalPlayerName = player.getName().getString();
        final BlockPos finalPos = pos;
        final String finalBlockId = blockId;
        final String finalNbt = ENABLE_DETAILED_NBT_LOGGING ? nbt : null;
        final Instant finalTimestamp = Instant.now();
        
        // Submit to async queue for processing
        logQueue.offer(() -> {
            synchronized (saveLock) {
                pendingBlockLogs.add(new BlockLogEntry(finalAction, finalPlayerName, finalPos, finalBlockId, finalNbt, finalTimestamp));
                
                // Flush batch if it's getting large
                if (pendingBlockLogs.size() >= BATCH_SIZE) {
                    flushPendingBlockLogs();
                }
                hasUnsavedChanges = true;
            }
        });
    }

    public static void logSignAction(String action, PlayerEntity player, BlockPos pos, String text, String nbt) {
        if (!ENABLE_SIGN_LOGGING) return;
        
        // Create copies of data to avoid issues with async processing
        final String finalAction = action;
        final String finalPlayerName = player != null ? player.getName().getString() : "unknown";
        final BlockPos finalPos = pos;
        final String finalText = text;
        final String finalNbt = ENABLE_DETAILED_NBT_LOGGING ? nbt : null;
        final Instant finalTimestamp = Instant.now();
        
        // Submit to async queue for processing
        logQueue.offer(() -> {
            synchronized (saveLock) {
                pendingSignLogs.add(new SignLogEntry(finalAction, finalPlayerName, finalPos, finalText, finalNbt, finalTimestamp));
                
                // Flush batch if it's getting large
                if (pendingSignLogs.size() >= BATCH_SIZE) {
                    flushPendingSignLogs();
                }
                hasUnsavedChanges = true;
            }
        });
    }

    public static void logKillAction(String killerName, String victimName, BlockPos pos, String world) {
        if (!ENABLE_KILL_LOGGING) return;
        
        // Create copies of data to avoid issues with async processing
        final String finalKillerName = killerName;
        final String finalVictimName = victimName;
        final BlockPos finalPos = pos;
        final String finalWorld = world;
        final Instant finalTimestamp = Instant.now();
        
        // Submit to async queue for processing
        logQueue.offer(() -> {
            synchronized (saveLock) {
                pendingKillLogs.add(new KillLogEntry(finalKillerName, finalVictimName, finalPos, finalWorld, finalTimestamp));
                
                // Flush batch if it's getting large
                if (pendingKillLogs.size() >= BATCH_SIZE) {
                    flushPendingKillLogs();
                }
                hasUnsavedChanges = true;
            }
        });
    }
    
    // Flush pending logs to main storage
    private static void flushPendingLogs() {
        logs.addAll(pendingLogs);
        pendingLogs.clear();
    }
    
    private static void flushPendingBlockLogs() {
        blockLogs.addAll(pendingBlockLogs);
        pendingBlockLogs.clear();
    }
    
    private static void flushPendingSignLogs() {
        signLogs.addAll(pendingSignLogs);
        pendingSignLogs.clear();
    }
    
    private static void flushPendingKillLogs() {
        killLogs.addAll(pendingKillLogs);
        pendingKillLogs.clear();
    }
    
    // Flush all pending logs before saving or querying
    private static void flushAllPendingLogs() {
        // Wait for async queue to process (with timeout)
        waitForAsyncQueue(1000); // Wait up to 1 second
        
        synchronized (saveLock) {
            flushPendingLogs();
            flushPendingBlockLogs();
            flushPendingSignLogs();
            flushPendingKillLogs();
        }
    }
    
    // Wait for async logging queue to be empty
    private static void waitForAsyncQueue(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (!logQueue.isEmpty() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            try {
                Thread.sleep(10); // Small delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
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
    public static List<KillLogEntry> getKillLogsInRange(BlockPos center, int range, String userFilter, boolean filterByKiller) {
        List<KillLogEntry> result = new ArrayList<>();
        int r2 = range * range;
        for (KillLogEntry entry : killLogs) {
            boolean userMatch = true;
            if (userFilter != null) {
                if (filterByKiller) {
                    userMatch = entry.killerName.equalsIgnoreCase(userFilter);
                } else {
                    userMatch = entry.victimName.equalsIgnoreCase(userFilter);
                }
            }
            if (userMatch && entry.pos.getSquaredDistance(center.getX(), center.getY(), center.getZ()) <= r2) {
                result.add(entry);
            }
        }
        return result;
    }
    // Deprecated: use the new method with filterByKiller
    public static List<KillLogEntry> getKillLogsInRange(BlockPos center, int range, String userFilter) {
        return getKillLogsInRange(center, range, userFilter, true);
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

    private static void saveAllLogsNow() {
        synchronized (saveLock) {
            if (!hasUnsavedChanges && !isShuttingDown) {
                return; // Nothing to save
            }
            
            // Flush any pending logs before saving
            flushAllPendingLogs();
            
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
                hasUnsavedChanges = false;
                
                if (!isShuttingDown) {
                    System.out.println("[MineTracer] Saved " + 
                        (logs.size() + blockLogs.size() + signLogs.size() + killLogs.size()) + 
                        " log entries to disk");
                }
            } catch (Exception e) { 
                System.err.println("[MineTracer] Failed to save logs: " + e.getMessage());
                e.printStackTrace(); 
            }
        }
    }

    public static void logInventoryAction(String action, PlayerEntity player, ItemStack stack) {
        synchronized (saveLock) {
            // Use BlockPos.ORIGIN (0,0,0) to mark inventory logs
            logs.add(new LogEntry(action, player.getName().getString(), BlockPos.ORIGIN, stack, Instant.now()));
            hasUnsavedChanges = true;
        }
    }

    // Inspector mode state tracking
    private static final java.util.Set<java.util.UUID> inspectorPlayers = new java.util.HashSet<>();

    public static void setInspectorMode(ServerPlayerEntity player, boolean enabled) {
        if (enabled) {
            inspectorPlayers.add(player.getUuid());
        } else {
            inspectorPlayers.remove(player.getUuid());
        }
    }

    public static boolean isInspectorMode(ServerPlayerEntity player) {
        return inspectorPlayers.contains(player.getUuid());
    }

    public static void toggleInspectorMode(ServerPlayerEntity player) {
        if (isInspectorMode(player)) {
            setInspectorMode(player, false);
        } else {
            setInspectorMode(player, true);
        }
    }
}
