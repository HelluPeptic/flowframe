package com.flowframe.features.keepinventory;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class KeepInventoryFeature {
    // Tracks players who have keep inventory OFF (by UUID)
    private static final Set<UUID> keepInventoryDisabled = new HashSet<>();
    private static final Path SAVE_PATH = Path.of("config", "keepinv-disabled.txt");

    public static void register() {
        loadDisabledSet();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("keepinv")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.keepinv"))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null)
                            return 0;
                        UUID uuid = player.getUuid();
                        // Toggle keepInventory for the player
                        if (keepInventoryDisabled.contains(uuid)) {
                            keepInventoryDisabled.remove(uuid);
                            saveDisabledSet();
                            player.sendMessage(Text.literal("KeepInventory is now ON")
                                    .formatted(Formatting.GREEN), false);
                        } else {
                            keepInventoryDisabled.add(uuid);
                            saveDisabledSet();
                            player.sendMessage(Text.literal("KeepInventory is now OFF")
                                    .formatted(Formatting.RED), false);
                        }
                        return 1;
                    }));
        });
    }

    // LuckPerms/Fabric Permissions API check helper
    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        // Allow if player has permission or is an operator (permission level 2+)
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }

    // Called from Mixin to check if a player should keep inventory
    public static boolean shouldKeepInventory(ServerPlayerEntity player) {
        return !keepInventoryDisabled.contains(player.getUuid());
    }

    // Persistence helpers
    private static void saveDisabledSet() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            Files.write(SAVE_PATH, keepInventoryDisabled.stream().map(UUID::toString).collect(Collectors.toSet()));
        } catch (IOException e) {
            System.err.println("[KeepInventoryFeature] Failed to save disabled set: " + e.getMessage());
        }
    }

    private static void loadDisabledSet() {
        keepInventoryDisabled.clear();
        if (!Files.exists(SAVE_PATH)) return;
        try {
            for (String line : Files.readAllLines(SAVE_PATH)) {
                try {
                    keepInventoryDisabled.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            System.err.println("[KeepInventoryFeature] Failed to load disabled set: " + e.getMessage());
        }
    }
}