package com.flowframe.features.countentities;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import me.lucko.fabric.api.permissions.v0.Permissions;

import java.util.*;
import java.util.stream.Collectors;

public class CountEntitiesCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("countentities")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.countentities"))
                    .executes(CountEntitiesCommand::execute)
                )
            );
        });
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player;
        try {
            player = source.getPlayer();
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command."));
            return 1;
        }

        Map<String, Integer> entityCounts = new HashMap<>();
        for (ServerWorld world : source.getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                String name = entity.getType().toString();
                entityCounts.put(name, entityCounts.getOrDefault(name, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sorted = entityCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(15)
            .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            player.sendMessage(Text.literal("No entities found."));
        } else {
            for (Map.Entry<String, Integer> entry : sorted) {
                player.sendMessage(Text.literal(entry.getKey() + ": " + entry.getValue()));
            }
        }
        return 1;
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
