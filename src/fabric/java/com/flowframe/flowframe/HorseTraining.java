package com.flowframe.flowframe;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;

/**
 * Horses very slowly get better the more they're used: speed grows with distance
 * ridden and jump strength grows with each jump, both capped at the best stats a
 * vanilla horse can naturally have.
 *
 * Everything works by nudging the horse's real attribute base values, which vanilla
 * already saves with the horse and syncs to clients — so no client mod or extra save
 * data is needed.
 *
 * Horses and mules train. Mules share the horse cap because vanilla breeds mule stats
 * from the same range as horse stats.
 */
public class HorseTraining {

    // Vanilla's natural horse stat range (AbstractHorse's private MIN_/MAX_ constants).
    private static final double MIN_SPEED = 0.1125;
    private static final double MAX_SPEED = 0.3375;
    private static final double MIN_JUMP = 0.4;
    private static final double MAX_JUMP = 1.0;

    // How much use it takes to train a worst-possible horse all the way to the max.
    private static final double BLOCKS_FOR_FULL_TRAINING = 100_000; // ~100 km of riding
    private static final double JUMPS_FOR_FULL_TRAINING = 4_000;
    private static final double SPEED_PER_BLOCK = (MAX_SPEED - MIN_SPEED) / BLOCKS_FOR_FULL_TRAINING;
    private static final double JUMP_PER_JUMP = (MAX_JUMP - MIN_JUMP) / JUMPS_FOR_FULL_TRAINING;

    private static final int SAMPLE_PERIOD_TICKS = 20;
    // A max-speed horse covers ~14 blocks/s, so credit at most that much per sample
    // (no bonus from ice highways), and treat anything far beyond it as a teleport.
    private static final double MAX_CREDITED_BLOCKS_PER_SAMPLE = 15.0;
    private static final double TELEPORT_BLOCKS_PER_SAMPLE = 64.0;
    // Ignores jump-packet spam from modified clients; a real jump takes longer than this.
    private static final int MIN_TICKS_BETWEEN_JUMPS = 10;

    // Approximate conversions from vanilla attribute values to the numbers players know.
    private static final double SPEED_TO_BLOCKS_PER_SECOND = 42.16;

    private record Sample(ResourceKey<Level> dimension, double x, double z) {}

    private Map<UUID, Sample> lastSamples = new HashMap<>();
    private final Map<UUID, Integer> lastJumpTick = new HashMap<>();
    private int tickCounter;

    /** Credits distance ridden since the last sample towards each ridden horse's speed. */
    public void tick(MinecraftServer server) {
        if (++tickCounter % SAMPLE_PERIOD_TICKS != 0) {
            return;
        }

        Map<UUID, Sample> samples = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.getVehicle() instanceof AbstractHorse horse) || !isTrainable(horse) || horse.getControllingPassenger() != player) {
                continue;
            }
            Sample current = new Sample(horse.level().dimension(), horse.getX(), horse.getZ());
            Sample previous = lastSamples.get(horse.getUUID());
            samples.put(horse.getUUID(), current);
            if (previous == null || !previous.dimension().equals(current.dimension()) || !isSteering(player)) {
                continue;
            }
            double distance = Math.hypot(current.x() - previous.x(), current.z() - previous.z());
            if (distance > TELEPORT_BLOCKS_PER_SAMPLE) {
                continue;
            }
            raiseBaseValue(horse, Attributes.MOVEMENT_SPEED, Math.min(distance, MAX_CREDITED_BLOCKS_PER_SAMPLE) * SPEED_PER_BLOCK, MAX_SPEED);
        }
        // Rebuilding the map drops horses nobody is riding anymore, so a horse that's
        // moved while unridden (leads, minecarts, ...) doesn't get that credited later.
        lastSamples = samples;
        lastJumpTick.keySet().retainAll(samples.keySet());
    }

    /** Called (from AbstractHorseMixin) when a rider makes a horse jump. */
    public void onRiderJump(AbstractHorse horse) {
        if (!isTrainable(horse) || !(horse.getControllingPassenger() instanceof ServerPlayer)) {
            return;
        }
        Integer lastTick = lastJumpTick.get(horse.getUUID());
        if (lastTick != null) {
            int sinceLast = horse.tickCount - lastTick;
            if (sinceLast >= 0 && sinceLast < MIN_TICKS_BETWEEN_JUMPS) {
                return;
            }
        }
        lastJumpTick.put(horse.getUUID(), horse.tickCount);
        raiseBaseValue(horse, Attributes.JUMP_STRENGTH, JUMP_PER_JUMP, MAX_JUMP);
    }

    /** /horsestats: shows the speed and jump height of the horse the player is riding. */
    public void showStats(ServerPlayer player) {
        if (!(player.getVehicle() instanceof AbstractHorse horse) || horse instanceof Llama || horse instanceof Camel) {
            player.sendSystemMessage(Component.literal("You need to be riding a horse to use this.").withStyle(ChatFormatting.RED));
            return;
        }

        boolean trainable = isTrainable(horse);
        double speed = attributeValue(horse, Attributes.MOVEMENT_SPEED);
        double jump = attributeValue(horse, Attributes.JUMP_STRENGTH);

        player.sendSystemMessage(Component.literal("=== ").withStyle(ChatFormatting.GOLD)
                .append(horse.getDisplayName().copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" ===").withStyle(ChatFormatting.GOLD)));
        player.sendSystemMessage(statLine("Speed", speed * SPEED_TO_BLOCKS_PER_SECOND, MAX_SPEED * SPEED_TO_BLOCKS_PER_SECOND, "blocks/s", trainable));
        player.sendSystemMessage(statLine("Jump", jumpHeightInBlocks(jump), jumpHeightInBlocks(MAX_JUMP), "blocks", trainable));
        if (trainable) {
            player.sendSystemMessage(Component.literal("Riding and jumping slowly improve these stats.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static MutableComponent statLine(String label, double value, double max, String unit, boolean showMax) {
        MutableComponent line = Component.literal(label + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(String.format(Locale.ROOT, "%.2f %s", value, unit)).withStyle(ChatFormatting.WHITE));
        if (showMax) {
            // Compare with a little slack since the values are rounded for display.
            if (value >= max - 0.005) {
                line.append(Component.literal(" (MAX)").withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.literal(String.format(Locale.ROOT, " (max %.2f)", max)).withStyle(ChatFormatting.GRAY));
            }
        }
        return line;
    }

    private static boolean isTrainable(AbstractHorse horse) {
        return horse instanceof Horse || horse instanceof Mule;
    }

    private static boolean isSteering(ServerPlayer player) {
        Input input = player.getLastClientInput();
        return input.forward() || input.backward() || input.left() || input.right();
    }

    private static void raiseBaseValue(AbstractHorse horse, Holder<Attribute> attribute, double amount, double max) {
        AttributeInstance instance = horse.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        double base = instance.getBaseValue();
        if (base < max) {
            instance.setBaseValue(Math.min(base + amount, max));
        }
    }

    private static double attributeValue(AbstractHorse horse, Holder<Attribute> attribute) {
        AttributeInstance instance = horse.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    /** Standard fit for how many blocks high a horse can jump at a given jump strength. */
    private static double jumpHeightInBlocks(double strength) {
        return -0.1817584952 * strength * strength * strength
                + 3.689713992 * strength * strength
                + 2.128599134 * strength
                - 0.343930367;
    }
}
