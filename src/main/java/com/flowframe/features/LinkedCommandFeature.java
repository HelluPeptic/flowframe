package com.flowframe.features;

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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class LinkedCommandFeature {
    private static final Path USERDATA_PATH = Path.of("config", "d4f_userdata.json");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("linked")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("minecraft_name", StringArgumentType.word())
                    .executes(LinkedCommandFeature::runLinkedCommand))
            );
        });
    }

    private static int runLinkedCommand(CommandContext<CommandSourceStack> context) {
        String mcName = StringArgumentType.getString(context, "minecraft_name");
        UUID uuid = getUuidFromName(mcName, context.getSource());
        if (uuid == null) {
            context.getSource().sendFailure(Component.literal("Could not resolve UUID for: " + mcName).withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            if (!Files.exists(USERDATA_PATH)) {
                context.getSource().sendFailure(Component.literal("d4f_userdata.json not found in config folder!").withStyle(ChatFormatting.RED));
                return 0;
            }
            String json = Files.readString(USERDATA_PATH, StandardCharsets.UTF_8);
            Map<String, Long> map = new Gson().fromJson(json, new TypeToken<Map<String, Long>>(){}.getType());
            String uuidStr = uuid.toString();
            if (map.containsKey(uuidStr)) {
                long otherId = map.get(uuidStr);
                Component uuidText = Component.literal("UUID: " + uuidStr).withStyle(ChatFormatting.GRAY);
                Component idText = Component.literal("Linked ID: " + otherId).withStyle(ChatFormatting.AQUA);
                
                context.getSource().sendSuccess(() -> uuidText, false);
                context.getSource().sendSuccess(() -> idText, false);
                return 1;
            } else {
                context.getSource().sendSuccess(() -> Component.literal("UUID: " + uuidStr).withStyle(ChatFormatting.GRAY), false);
                context.getSource().sendFailure(Component.literal("No linked ID found for " + mcName).withStyle(ChatFormatting.RED));
                return 0;
            }
        } catch (IOException | IllegalStateException e) {
            context.getSource().sendFailure(Component.literal("Error reading d4f_userdata.json: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    // Try to resolve UUID from name (online, playerdb.co API, then offline fallback)
    private static UUID getUuidFromName(String name, CommandSourceStack source) {
        // Try online players first
        try {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                if (player.getName().getString().equalsIgnoreCase(name)) {
                    return player.getUUID();
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
}