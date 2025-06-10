package com.flowframe.features.gl;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class GlAliasCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /gl i -> /gl inspect
            dispatcher.register(CommandManager.literal("gl")
                .then(CommandManager.literal("i")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gl.inspect"))
                    .executes(ctx -> runGlInspect(ctx))
                )
                .then(CommandManager.literal("p")
                    .requires(source -> hasLuckPermsPermission(source, "flowframe.command.gl.page"))
                    .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> runGriefLoggerPage(ctx, IntegerArgumentType.getInteger(ctx, "page")))
                    )
                )
            );
        });
    }

    private static int runGlInspect(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        // Elevate permission level for this command execution
        if (src.getEntity() instanceof ServerPlayerEntity) {
            src.getServer().getCommandManager().executeWithPrefix(src.withLevel(2), "/gl inspect");
            return 1;
        }
        src.sendError(Text.literal("Only players can use this command."));
        return 0;
    }

    private static int runGriefLoggerPage(CommandContext<ServerCommandSource> ctx, int page) {
        ServerCommandSource src = ctx.getSource();
        if (src.getEntity() instanceof ServerPlayerEntity) {
            src.getServer().getCommandManager().executeWithPrefix(src.withLevel(2), "/grieflogger page " + page);
            return 1;
        }
        src.sendError(Text.literal("Only players can use this command."));
        return 0;
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
