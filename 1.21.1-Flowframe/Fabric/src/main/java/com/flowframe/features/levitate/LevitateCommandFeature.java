package com.flowframe.features.levitate;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class LevitateCommandFeature {
    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTION_PROVIDER = (context, builder) -> {
        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
            builder.suggest(player.getName().getString());
        }
        return builder.buildFuture();
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("levitate")
                .requires(source -> hasLuckPermsPermission(source, "flowframe.command.levitate"))
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .suggests(PLAYER_SUGGESTION_PROVIDER)
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player");
                        ServerPlayerEntity target = null;
                        for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                            if (player.getName().getString().equalsIgnoreCase(playerName)) {
                                target = player;
                                break;
                            }
                        }
                        if (target == null) {
                            context.getSource().sendError(Text.literal("Player not found: " + playerName).formatted(Formatting.RED));
                            return 0;
                        }
                        // Apply levitation 50 for 1 second (20 ticks)
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 49));
                        context.getSource().sendFeedback(() -> Text.literal("Gave levitation 50 to " + playerName).formatted(Formatting.AQUA), false);
                        return 1;
                    })
                )
            );
        });
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
