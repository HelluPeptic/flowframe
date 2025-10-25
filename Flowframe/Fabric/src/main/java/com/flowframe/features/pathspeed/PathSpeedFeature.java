package com.flowframe.features.pathspeed;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.flowframe.config.FlowframeConfig;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PathSpeedFeature {
    private static final String SPEED_MODIFIER_NAME = "flowframe_path_speed";
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final Map<UUID, Long> lastPathTime = new HashMap<>();
    private static final long SPEED_REMOVAL_DELAY = 2000; // 2 seconds in milliseconds

    private static boolean isOnPath(ServerPlayerEntity player) {
        World world = player.getWorld();
        BlockPos playerPos = player.getBlockPos();
        
        // Check the block the player is standing on and 2 blocks below
        for (int i = 0; i <= 2; i++) {
            BlockPos checkPos = playerPos.down(i);
            if (world.getBlockState(checkPos).getBlock() == Blocks.DIRT_PATH) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHorseOnPath(HorseEntity horse) {
        World world = horse.getWorld();
        BlockPos horsePos = horse.getBlockPos();
        
        // Check the block the horse is standing on and 2 blocks below
        for (int i = 0; i <= 2; i++) {
            BlockPos checkPos = horsePos.down(i);
            if (world.getBlockState(checkPos).getBlock() == Blocks.DIRT_PATH) {
                return true;
            }
        }
        return false;
    }

    private static void applySpeedBoost(ServerPlayerEntity player) {
        double speedModifier = FlowframeConfig.getInstance().getPathSpeedModifier();
        
        // Remove existing modifier if present
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
            .removeModifier(SPEED_MODIFIER_UUID);
        
        // Apply new speed modifier
        EntityAttributeModifier modifier = new EntityAttributeModifier(
            SPEED_MODIFIER_UUID,
            SPEED_MODIFIER_NAME,
            speedModifier,
            EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
            .addPersistentModifier(modifier);
    }

    private static void applyHorseSpeedBoost(HorseEntity horse) {
        double speedModifier = FlowframeConfig.getInstance().getPathSpeedModifier();
        
        // Remove existing modifier if present
        horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
            .removeModifier(SPEED_MODIFIER_UUID);
        
        // Apply new speed modifier
        EntityAttributeModifier modifier = new EntityAttributeModifier(
            SPEED_MODIFIER_UUID,
            SPEED_MODIFIER_NAME,
            speedModifier,
            EntityAttributeModifier.Operation.MULTIPLY_BASE
        );
        
        horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
            .addPersistentModifier(modifier);
    }

    private static void removeSpeedBoost(ServerPlayerEntity player) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
            .removeModifier(SPEED_MODIFIER_UUID);
    }

    private static void removeHorseSpeedBoost(HorseEntity horse) {
        horse.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
            .removeModifier(SPEED_MODIFIER_UUID);
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("pathspeed")
                    .then(CommandManager.literal("enable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setPathSpeedEnabled(true);
                            context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Path speed boost enabled"), true);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("disable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setPathSpeedEnabled(false);
                            context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Path speed boost disabled"), true);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("status")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            boolean enabled = FlowframeConfig.getInstance().isPathSpeedEnabled();
                            double modifier = FlowframeConfig.getInstance().getPathSpeedModifier();
                            String status = enabled ? "§aenabled" : "§cdisabled";
                            context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Path speed boost is " + status), false);
                            context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Speed modifier: " + (modifier * 100) + "%"), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("setmodifier")
                        .requires(source -> source.hasPermissionLevel(3))
                        .then(CommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 2.0))
                            .executes(context -> {
                                double value = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "value");
                                FlowframeConfig.getInstance().setPathSpeedModifier(value);
                                context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Path speed modifier set to " + (value * 100) + "%"), true);
                                return 1;
                            })
                        )
                    )
                )
            );
        });

        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            FlowframeConfig config = FlowframeConfig.getInstance();
            if (!config.isPathSpeedEnabled()) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID playerId = player.getUuid();
                boolean onPath = false;

                // Check if player is on foot on a path
                if (player.getVehicle() == null && isOnPath(player)) {
                    onPath = true;
                    applySpeedBoost(player);
                    lastPathTime.put(playerId, currentTime);
                }
                // Check if player is riding a horse on a path
                else if (player.getVehicle() instanceof HorseEntity horse) {
                    if (isHorseOnPath(horse)) {
                        onPath = true;
                        applyHorseSpeedBoost(horse);
                        lastPathTime.put(playerId, currentTime);
                    }
                }

                // Remove speed boost if player hasn't been on path for 2 seconds
                if (!onPath && lastPathTime.containsKey(playerId)) {
                    long timeSinceLastPath = currentTime - lastPathTime.get(playerId);
                    if (timeSinceLastPath >= SPEED_REMOVAL_DELAY) {
                        removeSpeedBoost(player);
                        if (player.getVehicle() instanceof HorseEntity horse) {
                            removeHorseSpeedBoost(horse);
                        }
                        lastPathTime.remove(playerId);
                    }
                }
            }
        });

        System.out.println("[FLOWFRAME] Path speed boost feature initialized (disabled by default)");
    }
}