package com.flowframe.features.endtoggle;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class EndToggleFeature {
    private static boolean endEnabled = true;
    private static final Path SAVE_PATH = Path.of("config", "flowfrane", "endtoggle.txt");

    public static void register() {
        loadState();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("endtoggle")
                .requires(source -> hasLuckPermsPermission(source, "flowframe.command.endtoggle"))
                .executes(context -> {
                    endEnabled = !endEnabled;
                    saveState();
                    String msg = endEnabled ? "The End is now enabled" : "The End is now disabled";
                    Formatting color = endEnabled ? Formatting.GREEN : Formatting.RED;
                    context.getSource().sendFeedback(() -> Text.literal(msg).formatted(color), true);
                    return 1;
                })
            );
        });
    }

    public static boolean isEndEnabled() {
        return endEnabled;
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }

    private static void saveState() {
        try {
            Files.createDirectories(SAVE_PATH.getParent());
            Files.writeString(SAVE_PATH, Boolean.toString(endEnabled));
        } catch (IOException e) {
            System.err.println("[EndToggleFeature] Failed to save state: " + e.getMessage());
        }
    }

    private static void loadState() {
        if (!Files.exists(SAVE_PATH)) return;
        try {
            String value = Files.readString(SAVE_PATH).trim();
            endEnabled = Boolean.parseBoolean(value);
        } catch (IOException e) {
            System.err.println("[EndToggleFeature] Failed to load state: " + e.getMessage());
        }
    }
}
