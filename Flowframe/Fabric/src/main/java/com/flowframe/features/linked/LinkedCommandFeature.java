package com.flowframe.features.linked;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import me.lucko.fabric.api.permissions.v0.Permissions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

public class LinkedCommandFeature {
    private static final Path USERDATA_PATH = Path.of("config", "d4f_userdata.json");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("linked")
                .requires(source -> hasLuckPermsPermission(source, "flowframe.command.linked"))
                .then(CommandManager.argument("minecraft_name", StringArgumentType.word())
                    .executes(LinkedCommandFeature::runLinkedCommand))
            );
        });
    }

    private static int runLinkedCommand(CommandContext<ServerCommandSource> context) {
        String mcName = StringArgumentType.getString(context, "minecraft_name");
        UUID uuid = getUuidFromName(mcName, context.getSource());
        if (uuid == null) {
            context.getSource().sendError(Text.literal("Could not resolve UUID for: " + mcName).formatted(Formatting.RED));
            return 0;
        }
        try {
            if (!Files.exists(USERDATA_PATH)) {
                context.getSource().sendError(Text.literal("d4f_userdata.json not found in config folder!").formatted(Formatting.RED));
                return 0;
            }
            String json = Files.readString(USERDATA_PATH, StandardCharsets.UTF_8);
            Map<String, Long> map = new Gson().fromJson(json, new TypeToken<Map<String, Long>>(){}.getType());
            String uuidStr = uuid.toString();
            if (map.containsKey(uuidStr)) {
                long otherId = map.get(uuidStr);
                Text uuidText = Text.literal("UUID: " + uuidStr).formatted(Formatting.GRAY);
                Text idText = Text.literal("Linked ID: " + otherId)
                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, String.valueOf(otherId)))
                        .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy to clipboard"))));
                context.getSource().sendFeedback(() -> uuidText, false);
                context.getSource().sendFeedback(() -> idText, false);
                return 1;
            } else {
                context.getSource().sendFeedback(() -> Text.literal("UUID: " + uuidStr).formatted(Formatting.GRAY), false);
                context.getSource().sendError(Text.literal("No linked ID found for " + mcName).formatted(Formatting.RED));
                return 0;
            }
        } catch (IOException | IllegalStateException e) {
            context.getSource().sendError(Text.literal("Error reading d4f_userdata.json: " + e.getMessage()).formatted(Formatting.RED));
            return 0;
        }
    }

    // Try to resolve UUID from name (online, playerdb.co API, then offline fallback)
    private static UUID getUuidFromName(String name, ServerCommandSource source) {
        // Try online players first
        try {
            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                if (player.getName().getString().equalsIgnoreCase(name)) {
                    return player.getUuid();
                }
            }
        } catch (Throwable ignored) {}
        // Try playerdb.co API for real UUID
        try {
            URL url = new URL("https://playerdb.co/api/player/minecraft/" + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int status = conn.getResponseCode();
            if (status == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    content.append(line);
                }
                in.close();
                JsonObject obj = JsonParser.parseString(content.toString()).getAsJsonObject();
                if (obj.has("success") && obj.get("success").getAsBoolean()) {
                    JsonObject dataObj = obj.getAsJsonObject("data");
                    if (dataObj != null && dataObj.has("player")) {
                        JsonObject playerObj = dataObj.getAsJsonObject("player");
                        if (playerObj != null && playerObj.has("id")) {
                            String uuidStr = playerObj.get("id").getAsString(); // already dashed
                            return UUID.fromString(uuidStr);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        // Fallback: generate offline UUID
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
