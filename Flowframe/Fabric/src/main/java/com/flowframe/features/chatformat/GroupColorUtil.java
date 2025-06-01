package com.flowframe.features.chatformat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.text.TextColor;
import net.minecraft.server.command.ServerCommandSource;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class GroupColorUtil {
    // Returns the Formatting color for the player based on LuckPerms permission
    public static Formatting getPlayerGroupColor(ServerPlayerEntity player) {
        // Check for all Minecraft color codes (&0-&f)
        for (Formatting formatting : Formatting.values()) {
            if (formatting.isColor()) {
                String code = formatting.getName();
                // Minecraft color codes are 0-9, a-f
                String permission = "flowframe.groupcolor.&" + code;
                ServerCommandSource source = player.getCommandSource();
                if (Permissions.check(source, permission)) {
                    return formatting;
                }
            }
        }
        // Default color if none found, use GOLD for ops, WHITE for others
        if (player.getCommandSource().hasPermissionLevel(2)) {
            return Formatting.GOLD;
        }
        return Formatting.WHITE;
    }

    // Returns a TextColor for use with .styled
    public static TextColor getPlayerGroupTextColor(ServerPlayerEntity player) {
        Formatting formatting = getPlayerGroupColor(player);
        Integer colorValue = formatting.getColorValue();
        if (colorValue != null) {
            return TextColor.fromRgb(colorValue);
        }
        if (player.getCommandSource().hasPermissionLevel(2)) {
            return TextColor.fromRgb(0xFFD700); // Gold
        }
        return TextColor.fromRgb(0xFFFFFF); // White
    }

    // Returns the color code string (e.g., "§4") for legacy formatting
    public static String getPlayerGroupColorCode(ServerPlayerEntity player) {
        Formatting formatting = getPlayerGroupColor(player);
        // getCode() returns a char, not a String, and never null
        return "§" + formatting.getCode();
    }
}
