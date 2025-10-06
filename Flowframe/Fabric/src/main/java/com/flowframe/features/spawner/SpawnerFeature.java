package com.flowframe.features.spawner;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtCompound;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class SpawnerFeature {
    private static final Map<UUID, SpawnerData> spawners = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> entityToSpawner = new ConcurrentHashMap<>(); // Entity UUID -> Spawner UUID
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
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("spawner")
                    .then(CommandManager.literal("add")
                        .requires(source -> hasSpawnerPermission(source))
                        .then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 100))
                            .then(CommandManager.argument("limit", IntegerArgumentType.integer(1, 50))
                                .then(CommandManager.argument("interval", IntegerArgumentType.integer(1, 6000))
                                    .then(CommandManager.argument("mob", StringArgumentType.greedyString())
                                        .suggests(ENTITY_SUGGESTIONS)
                                        .executes(context -> addSpawner(context)))))))
                    .then(CommandManager.literal("remove")
                        .requires(source -> hasSpawnerPermission(source))
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
                cleanupSpawnerEntities();
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

            int radius = IntegerArgumentType.getInteger(context, "radius");
            int limit = IntegerArgumentType.getInteger(context, "limit");
            int interval = IntegerArgumentType.getInteger(context, "interval");
            String mobString = StringArgumentType.getString(context, "mob");

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
                playerPos,
                radius,
                mobType,
                limit,
                interval,
                player.getServerWorld()
            );

            spawners.put(spawnerId, spawnerData);

            player.sendMessage(Text.literal("§a[FLOWFRAME] Spawner created! ID: " + spawnerId.toString().substring(0, 8) + 
                "... | Mob: " + mobType.getTranslationKey() + 
                " | Radius: " + radius + 
                " | Limit: " + limit + 
                " | Interval: " + interval + " ticks"), false);

            return 1;
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§c[FLOWFRAME] Error creating spawner: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeSpawner(CommandContext<ServerCommandSource> context) {
        try {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) return 0;

            BlockPos playerPos = player.getBlockPos();
            SpawnerData toRemove = null;

            // Find spawner that contains the player's position
            for (SpawnerData spawnerData : spawners.values()) {
                if (spawnerData.world.equals(player.getServerWorld()) && 
                    spawnerData.center.isWithinDistance(playerPos, spawnerData.radius)) {
                    toRemove = spawnerData;
                    break;
                }
            }

            if (toRemove != null) {
                final UUID spawnerIdToRemove = toRemove.id;
                spawners.remove(spawnerIdToRemove);
                
                // Clean up entity tracking for this spawner
                entityToSpawner.entrySet().removeIf(entry -> entry.getValue().equals(spawnerIdToRemove));
                
                player.sendMessage(Text.literal("§c[FLOWFRAME] Spawner removed! ID: " + spawnerIdToRemove.toString().substring(0, 8) + "..."), false);
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
        for (SpawnerData spawnerData : spawners.values()) {
            int currentCount = getCurrentMobCount(spawnerData);
            player.sendMessage(Text.literal("§7ID: " + spawnerData.id.toString().substring(0, 8) + 
                "... | Mob: " + spawnerData.mobType.getTranslationKey() + 
                " | Center: " + spawnerData.center.toShortString() + 
                " | Radius: " + spawnerData.radius + 
                " | Count: " + currentCount + "/" + spawnerData.limit + 
                " | Interval: " + spawnerData.interval + " ticks"), false);
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

            // Try to spawn a mob
            ServerWorld world = spawnerData.world;
            net.minecraft.util.math.random.Random random = world.random;
            
            // Find a random position within the radius
            int attempts = 10; // Try up to 10 times to find a valid spawn location
            for (int i = 0; i < attempts; i++) {
                double x = spawnerData.center.getX() + (random.nextDouble() - 0.5) * 2 * spawnerData.radius;
                double z = spawnerData.center.getZ() + (random.nextDouble() - 0.5) * 2 * spawnerData.radius;
                double y = world.getTopY();
                
                // Find the ground level
                for (int yCheck = (int)y; yCheck > world.getBottomY(); yCheck--) {
                    BlockPos checkPos = new BlockPos((int)x, yCheck, (int)z);
                    if (!world.getBlockState(checkPos).isAir() && world.getBlockState(checkPos.up()).isAir()) {
                        y = yCheck + 1;
                        break;
                    }
                }

                BlockPos spawnPos = new BlockPos((int)x, (int)y, (int)z);
                
                // Check if the spawn position is valid
                if (world.getBlockState(spawnPos).isAir() && world.getBlockState(spawnPos.up()).isAir()) {
                    // Try to spawn the entity
                    if (spawnerData.mobType.create(world) instanceof LivingEntity entity) {
                        entity.refreshPositionAndAngles(x, y, z, random.nextFloat() * 360.0F, 0.0F);
                        
                        if (world.doesNotIntersectEntities(entity)) {
                            world.spawnEntity(entity);
                            
                            // Track this entity as belonging to our spawner
                            entityToSpawner.put(entity.getUuid(), spawnerData.id);
                            
                            break; // Successfully spawned, exit loop
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Error processing spawner: " + e.getMessage());
        }
    }

    private static int getCurrentMobCount(SpawnerData spawnerData) {
        // Use a larger search radius (3x the spawn radius) to account for mob movement
        int searchRadius = spawnerData.radius * 3;
        BlockPos center = spawnerData.center;
        Box searchBox = new Box(
            center.getX() - searchRadius, 
            center.getY() - searchRadius, 
            center.getZ() - searchRadius,
            center.getX() + searchRadius, 
            center.getY() + searchRadius, 
            center.getZ() + searchRadius
        );
        
        // Count entities that belong to this spawner
        int taggedCount = 0;
        int nearbyCount = 0;
        
        List<? extends net.minecraft.entity.Entity> entities = spawnerData.world.getEntitiesByType(
            spawnerData.mobType, searchBox, entity -> true);
        
        for (net.minecraft.entity.Entity entity : entities) {
            UUID entityUuid = entity.getUuid();
            
            // Check if this entity was spawned by our spawner
            if (entityToSpawner.containsKey(entityUuid) && 
                entityToSpawner.get(entityUuid).equals(spawnerData.id)) {
                taggedCount++;
            } else {
                // Also count nearby naturally spawned mobs within the original radius
                double distance = Math.sqrt(entity.squaredDistanceTo(center.getX(), center.getY(), center.getZ()));
                if (distance <= spawnerData.radius) {
                    nearbyCount++;
                }
            }
        }
        
        return taggedCount + nearbyCount;
    }

    private static void cleanupSpawnerEntities() {
        // Remove tracking for entities that no longer exist or are too far from any spawner
        Set<UUID> entitiesToRemove = new HashSet<>();
        
        for (Map.Entry<UUID, UUID> entry : entityToSpawner.entrySet()) {
            UUID entityUuid = entry.getKey();
            UUID spawnerUuid = entry.getValue();
            
            // Check if spawner still exists
            SpawnerData spawnerData = spawners.get(spawnerUuid);
            if (spawnerData == null) {
                entitiesToRemove.add(entityUuid);
                continue;
            }
            
            // Try to find the entity in the world
            boolean entityFound = false;
            int cleanupRadius = spawnerData.radius * 5;
            BlockPos center = spawnerData.center;
            Box searchBox = new Box(
                center.getX() - cleanupRadius, 
                center.getY() - cleanupRadius, 
                center.getZ() - cleanupRadius,
                center.getX() + cleanupRadius, 
                center.getY() + cleanupRadius, 
                center.getZ() + cleanupRadius
            );
            
            for (net.minecraft.entity.Entity entity : spawnerData.world.getEntitiesByType(
                spawnerData.mobType, searchBox, e -> e.getUuid().equals(entityUuid))) {
                entityFound = true;
                
                // Check if entity is too far away
                double distance = Math.sqrt(entity.squaredDistanceTo(center.getX(), center.getY(), center.getZ()));
                if (distance > cleanupRadius) {
                    entitiesToRemove.add(entityUuid);
                }
                break;
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

    private static class SpawnerData {
        final UUID id;
        final BlockPos center;
        final int radius;
        final EntityType<?> mobType;
        final int limit;
        final int interval;
        final ServerWorld world;

        SpawnerData(UUID id, BlockPos center, int radius, EntityType<?> mobType, int limit, int interval, ServerWorld world) {
            this.id = id;
            this.center = center;
            this.radius = radius;
            this.mobType = mobType;
            this.limit = limit;
            this.interval = interval;
            this.world = world;
        }
    }
}