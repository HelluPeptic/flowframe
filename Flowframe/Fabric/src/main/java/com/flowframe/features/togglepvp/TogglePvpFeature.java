package com.flowframe.features.togglepvp;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TogglePvpFeature {
    // Tracks players who have PVP OFF (by UUID)
    private static final Set<UUID> pvpDisabled = new HashSet<>();
    private static final Path SAVE_PATH = Path.of("config", "flowframe", "pvp-disabled.txt");

    public static void register() {
        loadDisabledSet();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("togglepvp")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.togglepvp"))
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null)
                            return 0;
                        UUID uuid = player.getUuid();
                        // Toggle PVP for the player
                        if (pvpDisabled.contains(uuid)) {
                            pvpDisabled.remove(uuid);
                            saveDisabledSet();
                            player.sendMessage(Text.literal("PVP is now ON").formatted(Formatting.GREEN), false);
                        } else {
                            pvpDisabled.add(uuid);
                            saveDisabledSet();
                            player.sendMessage(Text.literal("PVP is now OFF").formatted(Formatting.RED), false);
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

    // Called from Mixin/event to check if a player should be able to PVP
    public static boolean isPvpEnabled(ServerPlayerEntity player) {
        return !pvpDisabled.contains(player.getUuid());
    }

    // Persistence helpers
    private static void saveDisabledSet() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            Files.write(SAVE_PATH, pvpDisabled.stream().map(UUID::toString).collect(Collectors.toSet()));
        } catch (IOException e) {
            System.err.println("[TogglePvpFeature] Failed to save disabled set: " + e.getMessage());
        }
    }

    private static void loadDisabledSet() {
        pvpDisabled.clear();
        if (!Files.exists(SAVE_PATH)) return;
        try {
            for (String line : Files.readAllLines(SAVE_PATH)) {
                try {
                    pvpDisabled.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            System.err.println("[TogglePvpFeature] Failed to load disabled set: " + e.getMessage());
        }
    }
}
