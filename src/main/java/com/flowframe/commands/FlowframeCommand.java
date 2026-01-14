package com.flowframe.commands;

import com.flowframe.config.FlowframeConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.MinecraftServer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlowframeCommand {

    private static final Map<String, SpawnerData> spawners = new ConcurrentHashMap<>();
    private static final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>();
    private static int tickCounter = 0;
    private static boolean tickEventRegistered = false;
    private static MinecraftServer currentServer = null;

    // Persistence file paths
    private static File getSpawnersFile() {
        if (currentServer == null) {
            return null;
        }
        return new File(currentServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "flowframe_spawners.json");
    }

    private static File getTrackingFile() {
        if (currentServer == null) {
            return null;
        }
        return new File(currentServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "flowframe_tracking.json");
    }

    // Entity type suggestions provider for mob ID tab completion
    private static final SuggestionProvider<CommandSourceStack> ENTITY_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();

        List<String> allEntities = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            // Add all entity types - let the user figure out what works
            String entityName = id.toString();
            // Filter out some obvious non-spawnable entities
            if (!entityName.contains("marker") && !entityName.contains("area_effect_cloud")
                    && !entityName.contains("fishing_bobber") && !entityName.contains("item")
                    && !entityName.contains("experience_orb") && !entityName.contains("lightning_bolt")) {
                allEntities.add(entityName);
            }
        }

        // Sort by relevance: exact prefix matches first, then contains matches
        List<String> exactMatches = new ArrayList<>();
        List<String> partialMatches = new ArrayList<>();

        for (String entity : allEntities) {
            if (entity.toLowerCase().startsWith(input)) {
                exactMatches.add(entity);
            } else if (entity.toLowerCase().contains(input)) {
                partialMatches.add(entity);
            }
        }

        // Add exact matches first (sorted)
        exactMatches.sort(String::compareTo);
        for (String entity : exactMatches) {
            builder.suggest(entity);
        }

        // Then add partial matches (sorted), but limit to prevent spam
        partialMatches.sort(String::compareTo);
        int maxPartialMatches = Math.max(0, 50 - exactMatches.size());
        for (int i = 0; i < Math.min(partialMatches.size(), maxPartialMatches); i++) {
            builder.suggest(partialMatches.get(i));
        }

        return builder.buildFuture();
    };

    // Spawner name suggestions provider
    private static final SuggestionProvider<CommandSourceStack> SPAWNER_NAME_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();

        for (String spawnerName : spawners.keySet()) {
            if (spawnerName.toLowerCase().startsWith(input)) {
                builder.suggest(spawnerName);
            }
        }

        return builder.buildFuture();
    };

    // Persistence methods
    private static void saveSpawners() {
        try {
            File file = getSpawnersFile();
            if (file == null) {
                return;
            }

            JsonObject root = new JsonObject();
            JsonArray spawnersList = new JsonArray();

            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                SpawnerData spawner = entry.getValue();
                JsonObject spawnerObj = new JsonObject();

                spawnerObj.addProperty("name", spawner.name);
                spawnerObj.addProperty("x", spawner.center.getX());
                spawnerObj.addProperty("y", spawner.center.getY());
                spawnerObj.addProperty("z", spawner.center.getZ());
                spawnerObj.addProperty("dimension", spawner.world.dimension().toString());
                spawnerObj.addProperty("radius", spawner.radius);
                spawnerObj.addProperty("limit", spawner.limit);
                spawnerObj.addProperty("interval", spawner.interval);
                spawnerObj.addProperty("mobType", BuiltInRegistries.ENTITY_TYPE.getKey(spawner.mobType).toString());
                if (spawner.entityName != null) {
                    spawnerObj.addProperty("entityName", spawner.entityName);
                }

                spawnersList.add(spawnerObj);
            }

            root.add("spawners", spawnersList);

            try (FileWriter writer = new FileWriter(file)) {
                new Gson().toJson(root, writer);
            }

        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save spawners: " + e.getMessage());
        }
    }

    private static void saveEntityTracking() {
        try {
            File file = getTrackingFile();
            if (file == null) {
                return;
            }

            JsonObject root = new JsonObject();
            JsonArray trackingList = new JsonArray();

            for (Map.Entry<UUID, String> entry : entityToSpawner.entrySet()) {
                JsonObject trackingObj = new JsonObject();
                trackingObj.addProperty("entityId", entry.getKey().toString());
                trackingObj.addProperty("spawnerName", entry.getValue());
                trackingList.add(trackingObj);
            }

            root.add("tracking", trackingList);

            try (FileWriter writer = new FileWriter(file)) {
                new Gson().toJson(root, writer);
            }

        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save entity tracking: " + e.getMessage());
        }
    }

    private static void loadSpawners() {
        try {
            File file = getSpawnersFile();
            if (file == null || !file.exists()) {
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray spawnersList = root.getAsJsonArray("spawners");

                for (JsonElement element : spawnersList) {
                    JsonObject spawnerObj = element.getAsJsonObject();

                    String name = spawnerObj.get("name").getAsString();
                    BlockPos center = new BlockPos(
                            spawnerObj.get("x").getAsInt(),
                            spawnerObj.get("y").getAsInt(),
                            spawnerObj.get("z").getAsInt()
                    );
                    String dimensionStr = spawnerObj.get("dimension").getAsString();
                    int radius = spawnerObj.get("radius").getAsInt();
                    int limit = spawnerObj.get("limit").getAsInt();
                    int interval = spawnerObj.get("interval").getAsInt();
                    String mobTypeStr = spawnerObj.get("mobType").getAsString();
                    String entityName = spawnerObj.has("entityName") ? spawnerObj.get("entityName").getAsString() : null;

                    // Get world
                    ServerLevel world = currentServer.overworld();
                    for (ServerLevel level : currentServer.getAllLevels()) {
                        if (level.dimension().toString().equals(dimensionStr)) {
                            world = level;
                            break;
                        }
                    }
                    if (world == null) {
                        continue;
                    }

                    // Get entity type
                    Identifier mobId = Identifier.tryParse(mobTypeStr);
                    if (mobId == null) {
                        continue;
                    }

                    EntityType<?> mobType = BuiltInRegistries.ENTITY_TYPE.getOptional(mobId).orElse(null);
                    if (mobType == null) {
                        continue;
                    }

                    // Create spawner
                    SpawnerData spawnerData = new SpawnerData(name, center, radius, limit, interval, mobType, entityName, world);
                    spawners.put(name, spawnerData);
                }
            }

        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to load spawners: " + e.getMessage());
        }
    }

    private static void loadEntityTracking() {
        try {
            File file = getTrackingFile();
            if (file == null || !file.exists()) {
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray trackingList = root.getAsJsonArray("tracking");

                for (JsonElement element : trackingList) {
                    JsonObject trackingObj = element.getAsJsonObject();

                    UUID entityId = UUID.fromString(trackingObj.get("entityId").getAsString());
                    String spawnerName = trackingObj.get("spawnerName").getAsString();

                    // Only restore if the spawner still exists
                    if (spawners.containsKey(spawnerName)) {
                        entityToSpawner.put(entityId, spawnerName);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to load entity tracking: " + e.getMessage());
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("flowframe")
                .then(Commands.literal("pathblocksspeed")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("percentage", DoubleArgumentType.doubleArg(0, 1000))
                                .executes(FlowframeCommand::setPathBlockSpeed)))
                .then(Commands.literal("spawner")
                        .then(Commands.literal("add")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 100))
                                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                                        .then(Commands.argument("interval", IntegerArgumentType.integer(1, 6000))
                                                                .then(Commands.argument("entityName", StringArgumentType.string())
                                                                        .then(Commands.argument("mobID", StringArgumentType.greedyString())
                                                                                .suggests(ENTITY_SUGGESTIONS)
                                                                                .executes(FlowframeCommand::addSpawner))))))))
                        .then(Commands.literal("remove")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(SPAWNER_NAME_SUGGESTIONS)
                                        .executes(FlowframeCommand::removeSpawnerByName))
                                .executes(FlowframeCommand::removeSpawner))
                        .then(Commands.literal("list")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(FlowframeCommand::listSpawners))));

        // Register tick event for spawner processing
        if (!tickEventRegistered) {
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                tickCounter++;
                processSpawners();
            });

            // Register server lifecycle events for persistence
            ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                currentServer = server;
                loadSpawners();
                loadEntityTracking();
                System.out.println("[FLOWFRAME] Loaded spawners and entity tracking from disk");
            });

            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                saveSpawners();
                saveEntityTracking();
                System.out.println("[FLOWFRAME] Saved spawners and entity tracking to disk");
            });

            tickEventRegistered = true;
        }
    }

    private static int setPathBlockSpeed(CommandContext<CommandSourceStack> context) {
        double percentage = DoubleArgumentType.getDouble(context, "percentage");
        double multiplier = 1.0 + (percentage / 100.0);

        FlowframeConfig.setPathBlockSpeedMultiplier(multiplier);

        context.getSource().sendSuccess(
                () -> Component.literal(String.format("§aPath block speed set to %.1f%% (%.2fx multiplier)",
                        percentage, multiplier)),
                true
        );

        return 1;
    }

    // Spawner command methods
    private static int addSpawner(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        try {
            String name = StringArgumentType.getString(context, "name");
            int radius = IntegerArgumentType.getInteger(context, "radius");
            int limit = IntegerArgumentType.getInteger(context, "limit");
            int interval = IntegerArgumentType.getInteger(context, "interval");
            String entityName = StringArgumentType.getString(context, "entityName");
            String mobID = StringArgumentType.getString(context, "mobID");

            // Handle empty entity name
            if (entityName.equals("\"\"") || entityName.equals("")) {
                entityName = null;
            }

            // Check if spawner already exists
            if (spawners.containsKey(name)) {
                player.sendSystemMessage(Component.literal("§c[FLOWFRAME] Spawner '" + name + "' already exists!"));
                return 0;
            }

            // Parse mob type
            Identifier mobId = Identifier.tryParse(mobID);
            if (mobId == null) {
                player.sendSystemMessage(Component.literal("§c[FLOWFRAME] Invalid mob ID: " + mobID));
                return 0;
            }

            EntityType<?> mobType = BuiltInRegistries.ENTITY_TYPE.getOptional(mobId).orElse(null);
            if (mobType == null || mobType == EntityType.MARKER) {
                player.sendSystemMessage(Component.literal("§c[FLOWFRAME] Unknown mob type: " + mobID));
                return 0;
            }

            // Create spawner
            SpawnerData spawnerData = new SpawnerData(
                    name,
                    player.blockPosition(),
                    radius,
                    limit,
                    interval,
                    mobType,
                    entityName,
                    (ServerLevel) player.level()
            );

            spawners.put(name, spawnerData);

            // Save spawners immediately
            saveSpawners();

            String message = "§a[FLOWFRAME] Spawner '" + name + "' created! "
                    + "| Mob: " + mobID
                    + " | Radius: " + radius
                    + " | Limit: " + limit
                    + " | Interval: " + interval + " ticks";

            if (entityName != null) {
                message += " | Entity Name: " + entityName;
            }

            player.sendSystemMessage(Component.literal(message));
            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c[FLOWFRAME] Error creating spawner: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeSpawnerByName(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        if (spawners.remove(name) != null) {
            // Clean up entity tracking
            entityToSpawner.entrySet().removeIf(entry -> entry.getValue().equals(name));

            // Save changes immediately
            saveSpawners();
            saveEntityTracking();

            player.sendSystemMessage(Component.literal("§a[FLOWFRAME] Spawner '" + name + "' removed!"));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("§c[FLOWFRAME] Spawner '" + name + "' not found!"));
            return 0;
        }
    }

    private static int removeSpawner(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        BlockPos playerPos = player.blockPosition();
        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
            SpawnerData spawner = entry.getValue();
            if (spawner.world.equals(player.level())
                    && spawner.center.distSqr(playerPos) <= spawner.radius * spawner.radius) {
                String name = entry.getKey();
                spawners.remove(name);
                entityToSpawner.entrySet().removeIf(e -> e.getValue().equals(name));

                // Save changes immediately
                saveSpawners();
                saveEntityTracking();

                player.sendSystemMessage(Component.literal("§a[FLOWFRAME] Spawner '" + name + "' removed!"));
                return 1;
            }
        }

        player.sendSystemMessage(Component.literal("§e[FLOWFRAME] No spawner found at your location"));
        return 0;
    }

    private static int listSpawners(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        if (spawners.isEmpty()) {
            player.sendSystemMessage(Component.literal("§e[FLOWFRAME] No spawners exist"));
            return 1;
        }

        player.sendSystemMessage(Component.literal("§e[FLOWFRAME] Active spawners:"));
        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
            String name = entry.getKey();
            SpawnerData spawner = entry.getValue();
            int count = getCurrentMobCount(spawner);

            // Count how many are alive vs tracked
            int aliveCount = 0;
            for (Map.Entry<UUID, String> entityEntry : entityToSpawner.entrySet()) {
                if (entityEntry.getValue().equals(name)) {
                    Entity entity = spawner.world.getEntity(entityEntry.getKey());
                    if (entity != null && entity.isAlive()) {
                        aliveCount++;
                    }
                }
            }

            String message = "§7" + name
                    + " | Mob: " + BuiltInRegistries.ENTITY_TYPE.getKey(spawner.mobType)
                    + " | Center: " + spawner.center.getX() + "," + spawner.center.getY() + "," + spawner.center.getZ()
                    + " | Radius: " + spawner.radius
                    + " | Count: " + count + "/" + spawner.limit + " (alive: " + aliveCount + ")"
                    + " | Interval: " + spawner.interval + " ticks";

            if (spawner.entityName != null) {
                message += " | Name: " + spawner.entityName;
            }

            player.sendSystemMessage(Component.literal(message));
        }

        return 1;
    }

    private static void processSpawners() {
        for (SpawnerData spawner : spawners.values()) {
            if (tickCounter % spawner.interval == 0) {
                trySpawnMob(spawner);
            }
        }

        // More frequent cleanup - every 30 seconds instead of 5 minutes
        if (tickCounter % 600 == 0) {
            cleanupDeadEntities();
        }
    }

    private static void trySpawnMob(SpawnerData spawner) {
        try {
            // Check if we're at the limit
            int currentCount = getCurrentMobCount(spawner);
            if (currentCount >= spawner.limit) {
                return;
            }

            // Check if chunk is loaded
            ServerLevel world = spawner.world;
            if (!world.hasChunk(spawner.center.getX() >> 4, spawner.center.getZ() >> 4)) {
                return;
            }

            RandomSource random = world.random;

            // Try multiple spawn attempts
            for (int attempt = 0; attempt < 10; attempt++) {
                // Calculate spawn position - keep same Y level as spawner (fix the cylinder bug)
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = random.nextDouble() * spawner.radius;

                double x = spawner.center.getX() + 0.5 + Math.cos(angle) * distance;
                double z = spawner.center.getZ() + 0.5 + Math.sin(angle) * distance;
                double y = spawner.center.getY();

                // Find a safe spawn location near the Y level
                BlockPos spawnPos = findSafeSpawnLocation(world, new BlockPos((int) x, (int) y, (int) z));
                if (spawnPos == null) {
                    continue;
                }

                // Create entity - using new constructor approach
                try {
                    LivingEntity entity = (LivingEntity) spawner.mobType.create(world, net.minecraft.world.entity.EntitySpawnReason.SPAWNER);
                    if (entity == null) {
                        continue;
                    }

                    // Set position
                    entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                    entity.setYRot(random.nextFloat() * 360.0F);

                    // Initialize mob (simplified - just spawn without special initialization)
                    // This avoids the equipment/weapon initialization issues
                    // Set custom name if specified
                    if (spawner.entityName != null) {
                        entity.setCustomName(Component.literal(spawner.entityName));
                        entity.setCustomNameVisible(true);
                    }

                    // Check for collisions
                    if (world.noCollision(entity)) {
                        // Add to world
                        world.addFreshEntity(entity);

                        // Track entity
                        entityToSpawner.put(entity.getUUID(), spawner.name);

                        // Save entity tracking when new entities are spawned
                        saveEntityTracking();

                        break; // Successfully spawned
                    }
                } catch (Exception e) {
                    // Skip this spawn attempt if entity creation fails
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Error spawning mob: " + e.getMessage());
        }
    }

    private static BlockPos findSafeSpawnLocation(ServerLevel world, BlockPos center) {
        // Search for a safe location within 3 blocks up/down from center
        for (int yOffset = 0; yOffset <= 3; yOffset++) {
            for (int yDir = 0; yDir < (yOffset == 0 ? 1 : 2); yDir++) {
                int y = center.getY() + (yDir == 0 ? yOffset : -yOffset);
                if (y < -64 || y >= 320) {
                    continue; // Hardcoded world limits
                }
                BlockPos pos = new BlockPos(center.getX(), y, center.getZ());

                // Check if it's a valid spawn location
                if (!world.getBlockState(pos.below()).isAir()
                        && // Solid ground
                        world.getBlockState(pos).isAir()
                        && // Air at spawn level
                        world.getBlockState(pos.above()).isAir()) {  // Air above
                    return pos;
                }
            }
        }
        return null;
    }

    private static int getCurrentMobCount(SpawnerData spawner) {
        // Count all tracked entities for this spawner, regardless of location
        return (int) entityToSpawner.entrySet().stream()
                .filter(entry -> entry.getValue().equals(spawner.name))
                .count();
    }

    private static void cleanupDeadEntities() {
        boolean changed = false;

        changed = entityToSpawner.entrySet().removeIf(entry -> {
            UUID entityId = entry.getKey();
            String spawnerName = entry.getValue();

            SpawnerData spawner = spawners.get(spawnerName);
            if (spawner == null) {
                return true; // Remove if spawner doesn't exist
            }

            // Check if entity still exists anywhere in the world
            Entity entity = spawner.world.getEntity(entityId);
            return entity == null || !entity.isAlive();
        });

        // Save tracking if any entities were cleaned up
        if (changed) {
            saveEntityTracking();
        }
    }

    // Spawner data class
    private static class SpawnerData {

        final String name;
        final BlockPos center;
        final int radius;
        final int limit;
        final int interval;
        final EntityType<?> mobType;
        final String entityName;
        final ServerLevel world;

        SpawnerData(String name, BlockPos center, int radius, int limit, int interval,
                EntityType<?> mobType, String entityName, ServerLevel world) {
            this.name = name;
            this.center = center;
            this.radius = radius;
            this.limit = limit;
            this.interval = interval;
            this.mobType = mobType;
            this.entityName = entityName;
            this.world = world;
        }
    }
}
