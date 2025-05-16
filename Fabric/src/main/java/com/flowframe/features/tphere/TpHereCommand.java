package com.flowframe.features.tphere;

import net.minecraft.command.argument.EntityArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class TpHereCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("tphere")
                .requires(source -> hasLuckPermsPermission(source, "flowframe.command.tphere"))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> executeTpHereCommand(ctx, EntityArgumentType.getPlayer(ctx, "player"))))
            );
        });
    }

    private static int executeTpHereCommand(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity executor;
        try {
            executor = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command."));
            return 1;
        }
        // Teleport target to executor
        target.teleport((ServerWorld) executor.getWorld(), executor.getX(), executor.getY(), executor.getZ(), executor.getYaw(), executor.getPitch());
        return 1;
    }

    // LuckPerms/Fabric Permissions API check helper
    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        // Try Fabric Permissions API if present
        try {
            Class<?> permApi = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Class<?> scsClass = Class.forName("net.minecraft.server.command.ServerCommandSource");
            java.lang.reflect.Method check = permApi.getMethod("check", scsClass, String.class);
            Object result = check.invoke(null, source, permission);
            if (result instanceof Boolean) return (Boolean) result;
        } catch (Exception ignored) {}
        // Fallback: allow ops (permission level 2+)
        return source.hasPermissionLevel(2);
    }
}
