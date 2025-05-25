package com.flowframe.features.chatformat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

public class ChatFormatFeature {
    private static final Path PLAYERNAMES_PATH = Path.of(System.getProperty("user.dir"), "config",
            "playernames.txt");
    private static final Set<String> savedPlayerNames = new HashSet<>();

    private static void loadPlayerNames() {
        try {
            if (Files.exists(PLAYERNAMES_PATH)) {
                Files.readAllLines(PLAYERNAMES_PATH, StandardCharsets.UTF_8)
                    .forEach(savedPlayerNames::add);
            }
        } catch (Exception ignored) {
        }
    }

    public static void register() {
        loadPlayerNames();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();
            boolean isNew = false;
            synchronized (savedPlayerNames) {
                if (!savedPlayerNames.contains(playerName)) {
                    isNew = true;
                    savePlayerName(playerName);
                }
            }
            // Set tablist header/footer for the joining player
            com.flowframe.features.chatformat.TablistUtil.updateTablistForPlayer(player, server);
            com.flowframe.features.chatformat.TablistUtil.updateTablistDisplayNamesForAll(server);
            TablistUtil.registerTablistAutoUpdate(server);
            Text msg;
            if (isNew) {
                msg = Text.literal(playerName)
                        .styled(style -> style.withColor(TextColor.fromRgb(0x443e69)))
                        .append(Text.literal(" joined for the first time")
                                .styled(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            } else {
                msg = Text.literal(playerName)
                        .styled(style -> style.withColor(TextColor.fromRgb(0x443e69)))
                        .append(Text.literal(" joined the server.")
                                .styled(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            }
            server.getPlayerManager().broadcast(msg, false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            Text msg = Text.literal(player.getName().getString())
                    .styled(style -> style.withColor(TextColor.fromRgb(0x443e69)))
                    .append(Text.literal(" left the server.")
                            .styled(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            server.getPlayerManager().broadcast(msg, false);
        });
    }

    private static void savePlayerName(String name) {
        if (savedPlayerNames.contains(name))
            return;
        savedPlayerNames.add(name);
        try {
            Files.createDirectories(PLAYERNAMES_PATH.getParent());
            Files.writeString(PLAYERNAMES_PATH, name + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
