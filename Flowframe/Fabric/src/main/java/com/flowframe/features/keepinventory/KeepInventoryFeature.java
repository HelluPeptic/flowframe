package com.flowframe.features.keepinventory;

import com.flowframe.config.FlowframeConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.command.CommandManager;

public class KeepInventoryFeature {
    public static void register() {
        // Register commands for global keep inventory control
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("keepinventory")
                    .then(CommandManager.literal("enable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setKeepInventoryEnabled(true);
                            context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Keep inventory enabled globally"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("disable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setKeepInventoryEnabled(false);
                            context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Keep inventory disabled globally"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("status")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            boolean enabled = FlowframeConfig.getInstance().isKeepInventoryEnabled();
                            String status = enabled ? "§aenabled" : "§cdisabled";
                            context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Keep inventory is " + status + " globally"), false);
                            return 1;
                        }))));
        });

        System.out.println("[FLOWFRAME] Keep inventory feature initialized (disabled by default)");
    }

    // Called from Mixin to check if a player should keep inventory
    public static boolean shouldKeepInventory(ServerPlayerEntity player) {
        return FlowframeConfig.getInstance().isKeepInventoryEnabled();
    }
}