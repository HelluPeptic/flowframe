package com.flowframe.features.spawner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class SpawnerFeature {
    private static final Map<String, SpawnerData> spawners = new ConcurrentHashMap<>(); // Changed to String key for names
    private static final Map<UUID, String> entityToSpawner = new ConcurrentHashMap<>(); // Entity UUID -> Spawner Name
    private static int tickCounter = 0;
    
    // Suggestion provider for entity types
    private static final SuggestionProvider<ServerCommandSource> ENTITY_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();
        
        // Get ALL registered entities and suggest them
        List<String> allEntities = new ArrayList<>();
        for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
            allEntities.add(id.toString());
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
        int maxPartialMatches = Math.max(0, 50 - exactMatches.size()); // Limit total suggestions
        for (int i = 0; i < Math.min(partialMatches.size(), maxPartialMatches); i++) {
            builder.suggest(partialMatches.get(i));
        }
        
        return builder.buildFuture();
    };
    
    // Suggestion provider for spawner names
    private static final SuggestionProvider<ServerCommandSource> SPAWNER_NAME_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();
        
        for (String spawnerName : spawners.keySet()) {
            if (spawnerName.toLowerCase().startsWith(input)) {
                builder.suggest(spawnerName);
            }
        }
        
        return builder.buildFuture();
    };
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("spawner")
                    .then(CommandManager.literal("add")
                        .requires(source -> hasSpawnerPermission(source))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 100))
                                .then(CommandManager.argument("limit", IntegerArgumentType.integer(1, 50))
                                    .then(CommandManager.argument("interval", IntegerArgumentType.integer(1, 6000))
                                        .then(CommandManager.argument("entityName", StringArgumentType.string())
                                            .then(CommandManager.argument("mob", StringArgumentType.greedyString())
                                                .suggests(ENTITY_SUGGESTIONS)
                                                .executes(context -> addSpawner(context)))))))))
                    .then(CommandManager.literal("remove")
                        .requires(source -> hasSpawnerPermission(source))
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .suggests(SPAWNER_NAME_SUGGESTIONS)
                            .executes(context -> removeSpawnerByName(context)))
                        .executes(context -> removeSpawner(context)))
                    .then(CommandManager.literal("list")
                        .requires(source -> hasSpawnerPermission(source))
                        .executes(context -> listSpawners(context)))));
        });

        // Register tick event for spawner processing
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            
            // Clean up every 5 minutes (6000 ticks)
            if (tickCounter % 6000 == 0) {
                int beforeCleanup = entityToSpawner.size();
                cleanupSpawnerEntities();
                int afterCleanup = entityToSpawner.size();
                System.out.println("[FLOWFRAME] Cleanup complete. Tracked entities: " + beforeCleanup + " -> " + afterCleanup);
            }
            
            for (SpawnerData spawnerData : spawners.values()) {
                // Check if it's time to spawn
                if (tickCounter % spawnerData.interval == 0) {
                    processSpawner(spawnerData, server);
                }
            }
        });
    }

    private static boolean hasSpawnerPermission(ServerCommandSource source) {
        return Permissions.check(source, "flowframe.command.spawner") || source.hasPermissionLevel(3);
    }

    private static int addSpawner(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) return 0;

            String spawnerName = StringArgumentType.getString(context, "name");
            int radius = IntegerArgumentType.getInteger(context, "radius");
            int limit = IntegerArgumentType.getInteger(context, "limit");
            int interval = IntegerArgumentType.getInteger(context, "interval");
            String entityNameInput = StringArgumentType.getString(context, "entityName");
            String mobString = StringArgumentType.getString(context, "mob");
            
            // Handle entity name - treat empty string as no name
            String entityName = null;
            if (entityNameInput != null && !entityNameInput.isEmpty() && !entityNameInput.equals("\"\"") && !entityNameInput.equals("")) {
                entityName = entityNameInput;
            }

            // Check if spawner name already exists
            if (spawners.containsKey(spawnerName)) {
                player.sendMessage(Text.literal("§c[FLOWFRAME] Spawner with name '" + spawnerName + "' already exists!"), false);
                return 0;
            }

            // Parse entity type from string
            Identifier mobId = new Identifier(mobString);
            EntityType<?> mobType = Registries.ENTITY_TYPE.get(mobId);
            
            if (mobType == null) {
                player.sendMessage(Text.literal("§c[FLOWFRAME] Invalid mob type: " + mobString), false);
                return 0;
            }

            BlockPos playerPos = player.getBlockPos();
            UUID spawnerId = UUID.randomUUID();
            
            SpawnerData spawnerData = new SpawnerData(
                spawnerId,
                spawnerName,
                playerPos,
                radius,
                mobType,
                limit,
                interval,
                player.getServerWorld(),
                entityName
            );

            spawners.put(spawnerName, spawnerData);

            String message = "§a[FLOWFRAME] Spawner '" + spawnerName + "' created!" +
                " | Mob: " + mobType.getTranslationKey() + 
                " | Radius: " + radius + 
                " | Limit: " + limit + 
                " | Interval: " + interval + " ticks";
            
            if (entityName != null) {
                message += " | Entity Name: " + entityName;
            }
            
            player.sendMessage(Text.literal(message), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§c[FLOWFRAME] Error creating spawner: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeSpawnerByName(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) return 0;

            String spawnerName = StringArgumentType.getString(context, "name");
            
            SpawnerData spawnerData = spawners.get(spawnerName);
            if (spawnerData == null) {
                player.sendMessage(Text.literal("§c[FLOWFRAME] No spawner found with name '" + spawnerName + "'"), false);
                return 0;
            }

            spawners.remove(spawnerName);
            
            // Clean up entity tracking for this spawner
            final String finalSpawnerName = spawnerName;
            entityToSpawner.entrySet().removeIf(entry -> entry.getValue().equals(finalSpawnerName));
            
            player.sendMessage(Text.literal("§c[FLOWFRAME] Spawner '" + spawnerName + "' removed!"), false);
            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§c[FLOWFRAME] Error removing spawner: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeSpawner(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) return 0;

            BlockPos playerPos = player.getBlockPos();
            SpawnerData toRemove = null;
            String spawnerName = null;

            // Find spawner that contains the player's position
            for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
                SpawnerData spawnerData = entry.getValue();
                if (spawnerData.world.equals(player.getServerWorld()) && 
                    spawnerData.center.isWithinDistance(playerPos, spawnerData.radius)) {
                    toRemove = spawnerData;
                    spawnerName = entry.getKey();
                    break;
                }
            }

            if (toRemove != null) {
                spawners.remove(spawnerName);
                
                // Clean up entity tracking for this spawner
                final String finalSpawnerName = spawnerName;
                entityToSpawner.entrySet().removeIf(entry -> entry.getValue().equals(finalSpawnerName));
                
                player.sendMessage(Text.literal("§c[FLOWFRAME] Spawner '" + spawnerName + "' removed!"), false);
                return 1;
            } else {
                player.sendMessage(Text.literal("§e[FLOWFRAME] No spawner found at your current location"), false);
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§c[FLOWFRAME] Error removing spawner: " + e.getMessage()));
            return 0;
        }
    }

    private static int listSpawners(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        if (spawners.isEmpty()) {
            player.sendMessage(Text.literal("§e[FLOWFRAME] No spawners exist"), false);
            return 1;
        }

        player.sendMessage(Text.literal("§e[FLOWFRAME] Active spawners:"), false);
        for (Map.Entry<String, SpawnerData> entry : spawners.entrySet()) {
            String name = entry.getKey();
            SpawnerData spawnerData = entry.getValue();
            int currentCount = getCurrentMobCount(spawnerData);
            
            String message = "§7Name: " + name + 
                " | Mob: " + spawnerData.mobType.getTranslationKey() + 
                " | Center: " + spawnerData.center.toShortString() + 
                " | Radius: " + spawnerData.radius + 
                " | Count: " + currentCount + "/" + spawnerData.limit + 
                " | Interval: " + spawnerData.interval + " ticks";
            
            if (spawnerData.entityName != null) {
                message += " | Entity Name: " + spawnerData.entityName;
            }
            
            player.sendMessage(Text.literal(message), false);
        }

        return 1;
    }

    private static void processSpawner(SpawnerData spawnerData, net.minecraft.server.MinecraftServer server) {
        try {
            int currentCount = getCurrentMobCount(spawnerData);
            
            // Don't spawn if at limit
            if (currentCount >= spawnerData.limit) {
                return;
            }

            // Check if the spawner chunk is loaded - only spawn in loaded chunks
            ServerWorld world = spawnerData.world;
            if (!world.isChunkLoaded(spawnerData.center)) {
                return;
            }

            // Try to spawn a mob
            net.minecraft.util.math.random.Random random = world.random;
            
            // Find a random position within the radius
            int attempts = 10; // Try up to 10 times to find a valid spawn location
            for (int i = 0; i < attempts; i++) {
                double x = spawnerData.center.getX() + (random.nextDouble() - 0.5) * 2 * spawnerData.radius;
                double z = spawnerData.center.getZ() + (random.nextDouble() - 0.5) * 2 * spawnerData.radius;
                
                // Start from spawner Y level and search for closest valid spawn point
                int spawnerY = spawnerData.center.getY();
                double y = findBestSpawnY(world, (int)x, (int)z, spawnerY);
                
                if (y == -1) continue; // No valid spawn location found
                
                BlockPos spawnPos = new BlockPos((int)x, (int)y, (int)z);
                
                // Check if the spawn position chunk is loaded
                if (!world.isChunkLoaded(spawnPos)) {
                    continue; // Skip this position if chunk isn't loaded
                }
                
                // Check if the spawn position is valid
                if (world.getBlockState(spawnPos).isAir() && world.getBlockState(spawnPos.up()).isAir()) {
                    // Try to spawn the entity
                    if (spawnerData.mobType.create(world) instanceof LivingEntity entity) {
                        entity.refreshPositionAndAngles(x, y, z, random.nextFloat() * 360.0F, 0.0F);
                        
                        // Initialize equipment and AI for the entity (weapons, armor, etc.)
                        // Use the proper method for different entity types
                        if (entity instanceof net.minecraft.entity.mob.MobEntity mobEntity) {
                            mobEntity.initialize(world, world.getLocalDifficulty(spawnPos), net.minecraft.entity.SpawnReason.SPAWNER, null, null);
                        }
                        
                        // Apply custom name if specified
                        if (spawnerData.entityName != null) {
                            entity.setCustomName(Text.literal(spawnerData.entityName));
                            entity.setCustomNameVisible(true);
                        }
                        
                        // Add NBT tag to mark this entity as spawned by our spawner
                        NbtCompound nbt = new NbtCompound();
                        entity.writeNbt(nbt);
                        nbt.putString("FlowframeSpawner", spawnerData.name);
                        entity.readNbt(nbt);
                        
                        if (world.doesNotIntersectEntities(entity)) {
                            world.spawnEntity(entity);
                            
                            // Track this entity as belonging to our spawner
                            entityToSpawner.put(entity.getUuid(), spawnerData.name);
                            
                            break; // Successfully spawned, exit loop
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Error processing spawner: " + e.getMessage());
        }
    }
    
    // Find the best Y level to spawn at, closest to the spawner's Y level
    private static int findBestSpawnY(ServerWorld world, int x, int z, int spawnerY) {
        int maxSearchRange = 20; // Search up to 20 blocks above/below spawner
        
        // First, try the exact spawner Y level
        if (isValidSpawnLocation(world, x, spawnerY, z)) {
            return spawnerY;
        }
        
        // Search in expanding rings around the spawner Y level
        for (int offset = 1; offset <= maxSearchRange; offset++) {
            // Try below first (caves/dungeons often below)
            int yBelow = spawnerY - offset;
            if (yBelow >= world.getBottomY() && isValidSpawnLocation(world, x, yBelow, z)) {
                return yBelow;
            }
            
            // Then try above
            int yAbove = spawnerY + offset;
            if (yAbove <= world.getTopY() - 2 && isValidSpawnLocation(world, x, yAbove, z)) {
                return yAbove;
            }
        }
        
        return -1; // No valid location found
    }
    
    // Check if a location is valid for spawning (solid block below, air above)
    private static boolean isValidSpawnLocation(ServerWorld world, int x, int y, int z) {
        BlockPos belowPos = new BlockPos(x, y - 1, z);
        BlockPos spawnPos = new BlockPos(x, y, z);
        BlockPos abovePos = new BlockPos(x, y + 1, z);
        
        return !world.getBlockState(belowPos).isAir() && 
               world.getBlockState(spawnPos).isAir() && 
               world.getBlockState(abovePos).isAir();
    }

    private static int getCurrentMobCount(SpawnerData spawnerData) {
        // Count all entities of this type in the world that belong to this spawner
        int count = 0;
        
        // Get all entities of this type in the world
        List<? extends net.minecraft.entity.Entity> allEntities = spawnerData.world.getEntitiesByType(
            spawnerData.mobType, entity -> true);
        
        for (net.minecraft.entity.Entity entity : allEntities) {
            // Check NBT tag for spawner ownership (this persists through chunk unload/reload)
            NbtCompound nbt = entity.writeNbt(new NbtCompound());
            
            if (nbt.contains("FlowframeSpawner")) {
                String spawnerName = nbt.getString("FlowframeSpawner");
                if (spawnerName.equals(spawnerData.name)) {
                    count++;
                    // Rebuild in-memory tracking for entities that were reloaded from chunks
                    entityToSpawner.put(entity.getUuid(), spawnerData.name);
                }
            }
            // Also check our in-memory tracking as fallback
            else if (entityToSpawner.containsKey(entity.getUuid()) && 
                     entityToSpawner.get(entity.getUuid()).equals(spawnerData.name)) {
                count++;
            }
        }
        
        return count;
    }

    private static void cleanupSpawnerEntities() {
        // First, rebuild tracking from all entities in the world (handles chunk reload)
        rebuildTrackingFromNBT();
        
        // Then remove tracking for entities that no longer exist
        Set<UUID> entitiesToRemove = new HashSet<>();
        
        for (Map.Entry<UUID, String> entry : entityToSpawner.entrySet()) {
            UUID entityUuid = entry.getKey();
            String spawnerName = entry.getValue();
            
            // Check if spawner still exists
            SpawnerData spawnerData = spawners.get(spawnerName);
            if (spawnerData == null) {
                entitiesToRemove.add(entityUuid);
                continue;
            }
            
            // Check if entity still exists in the world
            boolean entityFound = false;
            List<? extends net.minecraft.entity.Entity> allEntities = spawnerData.world.getEntitiesByType(
                spawnerData.mobType, entity -> entity.getUuid().equals(entityUuid));
            
            if (!allEntities.isEmpty()) {
                entityFound = true;
            }
            
            // If entity wasn't found, it probably died or despawned
            if (!entityFound) {
                entitiesToRemove.add(entityUuid);
            }
        }
        
        // Remove all marked entities
        for (UUID entityUuid : entitiesToRemove) {
            entityToSpawner.remove(entityUuid);
        }
    }

    private static void rebuildTrackingFromNBT() {
        // Scan all worlds for entities with FlowframeSpawner NBT tags
        for (SpawnerData spawnerData : spawners.values()) {
            List<? extends net.minecraft.entity.Entity> allEntities = spawnerData.world.getEntitiesByType(
                spawnerData.mobType, entity -> true);
            
            for (net.minecraft.entity.Entity entity : allEntities) {
                NbtCompound nbt = entity.writeNbt(new NbtCompound());
                
                if (nbt.contains("FlowframeSpawner")) {
                    String spawnerName = nbt.getString("FlowframeSpawner");
                    // Only track if the spawner still exists
                    if (spawners.containsKey(spawnerName)) {
                        entityToSpawner.put(entity.getUuid(), spawnerName);
                    }
                }
            }
        }
    }

    private static class SpawnerData {
        final UUID id;
        final String name;
        final BlockPos center;
        final int radius;
        final EntityType<?> mobType;
        final int limit;
        final int interval;
        final ServerWorld world;
        final String entityName; // Custom name for spawned entities

        SpawnerData(UUID id, String name, BlockPos center, int radius, EntityType<?> mobType, int limit, int interval, ServerWorld world, String entityName) {
            this.id = id;
            this.name = name;
            this.center = center;
            this.radius = radius;
            this.mobType = mobType;
            this.limit = limit;
            this.interval = interval;
            this.world = world;
            this.entityName = entityName;
        }
    }
}