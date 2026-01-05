package com.flowframe.features.chatformat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class TablistUtil {
    private static int tablistUpdateTick = 0;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Simple storage for BattleCore team colors
    private static final Map<UUID, Formatting> battleCoreColors = new HashMap<>();
    
    // Store server reference for tablist updates
    private static MinecraftServer currentServer = null;
    
    // Method for BattleCore to set a player's team color
    public static void setBattleCoreTeamColor(UUID playerUuid, Formatting color) {
        battleCoreColors.put(playerUuid, color);
        
        // Trigger a tablist display name update for all players
        if (currentServer != null) {
            try {
                updateTablistDisplayNamesForAll(currentServer);
            } catch (Exception e) {
                // Silently handle errors
            }
        }
    }
    
    // Method for BattleCore to remove a player's team color
    public static void removeBattleCoreTeamColor(UUID playerUuid) {
        battleCoreColors.remove(playerUuid);
        
        // Trigger a tablist display name update for all players
        if (currentServer != null) {
            try {
                updateTablistDisplayNamesForAll(currentServer);
            } catch (Exception e) {
                // Silently handle errors
            }
        }
    }
    
    public static void updateTablistForAll(MinecraftServer server) {
        currentServer = server; // Store server reference
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateTablistForPlayer(player, server);
        }
    }
    
    public static void updateTablistForPlayer(ServerPlayerEntity player, MinecraftServer server) {
        currentServer = server; // Store server reference
        // Get LuckPerms prefix
        String prefix = "";
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                CachedMetaData meta = user.getCachedData().getMetaData();
                String lpPrefix = meta.getPrefix();
                if (lpPrefix != null) {
                    prefix = lpPrefix.replace('&', '§');
                }
            }
        } catch (Throwable e) {
            // LuckPerms not available or error occurred
        }
        
        String displayName = prefix + player.getName().getString();
        Text tablistName = Text.literal(displayName);
        
        int online = server.getPlayerManager().getCurrentPlayerCount();
        // Add +1 to include yourself in the count if not already included
        // Vanilla getCurrentPlayerCount() does not include the player for whom the header is being set
        Text header = Text.literal("")
            .append(Text.literal("FlowSMP").styled(style -> style.withColor(Formatting.AQUA).withBold(true)))
            .append(Text.literal("\n" + displayName).styled(style -> style.withColor(Formatting.WHITE)))
            .append(Text.literal("\nOnline players: " + online).styled(style -> style.withColor(Formatting.GRAY)))
            .append(Text.literal("\n "));
        int ping = player.pingMilliseconds;
        Text footer = Text.literal("\n ")
            .append(Text.literal("Ping: " + ping).styled(style -> style.withColor(Formatting.GOLD)));
        player.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(header, footer));
        // Tablist display name update is not possible here without a mixin. Header/footer will still update correctly.
    }

    // Returns the custom tablist name (with prefix) for a player
    public static Text getTablistName(ServerPlayerEntity player) {
        String prefix = "";
        
        // Get LuckPerms prefix first
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                CachedMetaData meta = user.getCachedData().getMetaData();
                String lpPrefix = meta.getPrefix();
                if (lpPrefix != null) {
                    prefix = lpPrefix.replace('&', '§');
                }
            }
        } catch (Throwable e) {
            // LuckPerms not available or error occurred
        }
        
        // Check if BattleCore has set a color for this player first
        Formatting playerColor = battleCoreColors.get(player.getUuid());
        if (playerColor == null) {
            // Fall back to GroupColorUtil
            playerColor = GroupColorUtil.getPlayerGroupColor(player);
        }
        
        // Build the text with proper formatting by parsing color codes
        Text result;
        if (!prefix.isEmpty()) {
            // Parse the prefix for color codes and build styled text
            Text prefixText = parseColoredText(prefix);
            Text nameText = Text.literal(player.getName().getString()).formatted(playerColor);
            result = prefixText.copy().append(nameText);
        } else {
            // No LuckPerms prefix, just use player name with color
            result = Text.literal(player.getName().getString()).formatted(playerColor);
        }
        
        return result;
    }
    
    // Helper method to parse color codes in text
    private static Text parseColoredText(String text) {
        if (text == null || text.isEmpty()) {
            return Text.literal("");
        }
        
        Text result = Text.literal("");
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                // Add current text if any
                if (current.length() > 0) {
                    result = result.copy().append(Text.literal(current.toString()));
                    current = new StringBuilder();
                }
                
                // Get color code
                char colorCode = text.charAt(i + 1);
                Formatting formatting = getFormattingFromColorCode(colorCode);
                
                // Find the end of this colored segment
                int nextColor = text.indexOf('§', i + 2);
                if (nextColor == -1) nextColor = text.length();
                
                String coloredText = text.substring(i + 2, nextColor);
                if (!coloredText.isEmpty()) {
                    result = result.copy().append(Text.literal(coloredText).formatted(formatting));
                }
                
                i = nextColor - 1; // Skip to next color code (or end)
            } else if (c != '§') {
                current.append(c);
            }
        }
        
        // Add any remaining text
        if (current.length() > 0) {
            result = result.copy().append(Text.literal(current.toString()));
        }
        
        return result;
    }
    
    // Helper method to convert color code to Formatting
    private static Formatting getFormattingFromColorCode(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> Formatting.BLACK;
            case '1' -> Formatting.DARK_BLUE;
            case '2' -> Formatting.DARK_GREEN;
            case '3' -> Formatting.DARK_AQUA;
            case '4' -> Formatting.DARK_RED;
            case '5' -> Formatting.DARK_PURPLE;
            case '6' -> Formatting.GOLD;
            case '7' -> Formatting.GRAY;
            case '8' -> Formatting.DARK_GRAY;
            case '9' -> Formatting.BLUE;
            case 'a' -> Formatting.GREEN;
            case 'b' -> Formatting.AQUA;
            case 'c' -> Formatting.RED;
            case 'd' -> Formatting.LIGHT_PURPLE;
            case 'e' -> Formatting.YELLOW;
            case 'f' -> Formatting.WHITE;
            case 'l' -> Formatting.BOLD;
            case 'm' -> Formatting.STRIKETHROUGH;
            case 'n' -> Formatting.UNDERLINE;
            case 'o' -> Formatting.ITALIC;
            case 'r' -> Formatting.RESET;
            default -> Formatting.WHITE;
        };
    }
    
    // Helper method to convert Formatting to color code
    private static String getFormattingCode(Formatting formatting) {
        return switch (formatting) {
            case BLACK -> "0";
            case DARK_BLUE -> "1";
            case DARK_GREEN -> "2";
            case DARK_AQUA -> "3";
            case DARK_RED -> "4";
            case DARK_PURPLE -> "5";
            case GOLD -> "6";
            case GRAY -> "7";
            case DARK_GRAY -> "8";
            case BLUE -> "9";
            case GREEN -> "a";
            case AQUA -> "b";
            case RED -> "c";
            case LIGHT_PURPLE -> "d";
            case YELLOW -> "e";
            case WHITE -> "f";
            default -> "f";
        };
    }

    public static void updateTablistDisplayNamesForAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
        }
    }

    public static void registerTablistAutoUpdate(MinecraftServer server) {
        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv != server) return;
            tablistUpdateTick++;
            if (tablistUpdateTick >= 100) { // 5 seconds at 20 TPS
                tablistUpdateTick = 0;
                updateTablistForAll(server);
            }
        });
    }
}
