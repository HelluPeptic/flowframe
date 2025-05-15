package com.flowframe.features.keepinventory;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.Set;

public class KeepInventoryFeature {
    // Tracks players who have keep inventory OFF
    private static final Set<ServerPlayerEntity> keepInventoryDisabled = new HashSet<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("keepinv")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null)
                            return 0;

                        // Toggle keepInventory for the player
                        if (keepInventoryDisabled.contains(player)) {
                            keepInventoryDisabled.remove(player);
                            player.sendMessage(Text.literal("KeepInventory is now ON")
                                    .formatted(Formatting.GREEN), false);
                        } else {
                            keepInventoryDisabled.add(player);
                            player.sendMessage(Text.literal("KeepInventory is now OFF")
                                    .formatted(Formatting.RED), false);
                        }
                        return 1;
                    }));
        });
    }

    // Called from Mixin to check if a player should keep inventory
    public static boolean shouldKeepInventory(ServerPlayerEntity player) {
        return !keepInventoryDisabled.contains(player);
    }
}