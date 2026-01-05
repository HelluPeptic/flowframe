package com.flowframe.features.chatformat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.text.TextColor;
import net.minecraft.server.command.ServerCommandSource;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class GroupColorUtil {
    // Returns the Formatting color for the player based on LuckPerms permission
    public static Formatting getPlayerGroupColor(ServerPlayerEntity player) {
        // FIRST: Check for BattleCore team colors (highest priority)
        Formatting battleTeamColor = getBattleTeamColor(player);
        if (battleTeamColor != null) {
            return battleTeamColor;
        }
        
        ServerCommandSource source = player.getCommandSource();
        
        // Check for all standard Minecraft color codes (&0-&f) in priority order
        // Check for the specific permission first before checking others
        String[] colorCodes = {
            "4", "c", "6", "e", "2", "a", "b", "3", "1", "9", "d", "5", "f", "7", "8", "0"
        };
        
        for (String code : colorCodes) {
            String permission = "flowframe.groupcolor.&" + code;
            try {
                if (Permissions.check(source, permission)) {
                    return getFormattingFromCode(code);
                }
            } catch (Exception e) {
                // Permission check failed, continue to next
            }
        }
        
        // Try LuckPerms prefix-based colors if available
        try {
            String prefix = getLuckPermsPrefix(player);
            if (prefix != null && !prefix.isEmpty()) {
                Formatting prefixColor = extractColorFromPrefix(prefix);
                if (prefixColor != null) {
                    return prefixColor;
                }
            }
        } catch (Throwable ignored) {
            // LuckPerms not available or error occurred
        }
        
        // Default color if none found, use GOLD for ops, WHITE for others
        if (source.hasPermissionLevel(2)) {
            return Formatting.GOLD;
        }
        return Formatting.WHITE;
    }
    
    // Get LuckPerms prefix for a player
    private static String getLuckPermsPrefix(ServerPlayerEntity player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                CachedMetaData meta = user.getCachedData().getMetaData();
                String prefix = meta.getPrefix();
                if (prefix != null) {
                    // Convert & color codes to §
                    return prefix.replace('&', '§');
                }
            }
        } catch (Throwable e) {
            // LuckPerms not available or error occurred
        }
        return "";
    }
    
    // Convert color code to Formatting
    public static Formatting getFormattingFromCode(String code) {
        switch (code.toLowerCase()) {
            case "0": return Formatting.BLACK;
            case "1": return Formatting.DARK_BLUE;
            case "2": return Formatting.DARK_GREEN;
            case "3": return Formatting.DARK_AQUA;
            case "4": return Formatting.DARK_RED;
            case "5": return Formatting.DARK_PURPLE;
            case "6": return Formatting.GOLD;
            case "7": return Formatting.GRAY;
            case "8": return Formatting.DARK_GRAY;
            case "9": return Formatting.BLUE;
            case "a": return Formatting.GREEN;
            case "b": return Formatting.AQUA;
            case "c": return Formatting.RED;
            case "d": return Formatting.LIGHT_PURPLE;
            case "e": return Formatting.YELLOW;
            case "f": return Formatting.WHITE;
            default: return Formatting.WHITE;
        }
    }
    
    // Extract color from LuckPerms prefix (e.g., "§4[Admin] " -> DARK_RED)
    private static Formatting extractColorFromPrefix(String prefix) {
        if (prefix.length() >= 2 && prefix.charAt(0) == '§') {
            String code = String.valueOf(prefix.charAt(1));
            return getFormattingFromCode(code);
        }
        return null;
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
    
    // Helper method to check for BattleCore team colors
    private static Formatting getBattleTeamColor(ServerPlayerEntity player) {
        try {
            // First check if BattleCore has set a color directly via the TablistUtil API
            Formatting directColor = getBattleCoreColorFromTablistUtil(player);
            if (directColor != null) {
                return directColor;
            }
            
            // Check if player is on a BattleCore team by looking at scoreboard teams
            net.minecraft.scoreboard.Team team = player.getScoreboard().getPlayerTeam(player.getEntityName());
            if (team != null && team.getName().startsWith("battlecore_")) {
                // Extract team color from the team name and return the corresponding formatting
                String teamName = team.getName().replace("battlecore_", "");
                Formatting color = switch (teamName.toLowerCase()) {
                    case "red" -> Formatting.RED;
                    case "blue" -> Formatting.BLUE;
                    case "green" -> Formatting.GREEN;
                    case "yellow" -> Formatting.YELLOW;
                    case "orange" -> Formatting.GOLD;
                    case "pink" -> Formatting.LIGHT_PURPLE;
                    case "purple" -> Formatting.DARK_PURPLE;
                    case "aqua" -> Formatting.AQUA;
                    case "lime" -> Formatting.GREEN;
                    case "brown" -> Formatting.GOLD;
                    case "magenta" -> Formatting.LIGHT_PURPLE;
                    case "cyan" -> Formatting.DARK_AQUA;
                    default -> null;
                };
                return color;
            }
        } catch (Exception e) {
            // Silently handle errors
        }
        return null;
    }
    
    // Helper method to get BattleCore color from TablistUtil's battleCoreColors map
    private static Formatting getBattleCoreColorFromTablistUtil(ServerPlayerEntity player) {
        try {
            // Use reflection to access the private battleCoreColors map in TablistUtil
            Class<?> tablistUtilClass = Class.forName("com.flowframe.features.chatformat.TablistUtil");
            java.lang.reflect.Field battleCoreColorsField = tablistUtilClass.getDeclaredField("battleCoreColors");
            battleCoreColorsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, Formatting> battleCoreColors = 
                (java.util.Map<java.util.UUID, Formatting>) battleCoreColorsField.get(null);
            
            return battleCoreColors.get(player.getUuid());
        } catch (Exception e) {
            // This is fine - just means TablistUtil integration isn't available
            return null;
        }
    }
}
