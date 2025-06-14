package com.flowframe.features.version;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class VersionCommand {
    public static final String MOD_VERSION = "1.25.4"; // Set manually to match gradle.properties

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("version")
                    .executes(VersionCommand::sendVersion))
            );
        });
    }

    private static int sendVersion(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal("Flowframe version: " + MOD_VERSION), false);
        return 1;
    }
}
