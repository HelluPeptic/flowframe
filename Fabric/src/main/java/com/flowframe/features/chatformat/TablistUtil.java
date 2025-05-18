package com.flowframe.features.chatformat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Collections;

public class TablistUtil {
    public static void updateTablistForAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateTablistForPlayer(player, server);
        }
    }

    public static void updateTablistForPlayer(ServerPlayerEntity player, MinecraftServer server) {
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
        } catch (Throwable ignored) {}
        String displayName = prefix + player.getName().getString();
        Text tablistName = Text.literal(displayName);

        Text header = Text.literal("")
            .append(Text.literal("FlowSMP").styled(style -> style.withColor(Formatting.AQUA).withBold(true)))
            .append(Text.literal("\n" + player.getName().getString()).styled(style -> style.withColor(Formatting.WHITE)))
            .append(Text.literal("\nOnline players: " + server.getCurrentPlayerCount()).styled(style -> style.withColor(Formatting.GRAY)))
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
        } catch (Throwable ignored) {}
        String displayName = prefix + player.getName().getString();
        return Text.literal(displayName);
    }

    // Returns the LuckPerms group weight for a player (default 0 if not found)
    public static int getLuckPermsWeight(ServerPlayerEntity player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                String group = user.getPrimaryGroup();
                if (group != null) {
                    Integer weight = luckPerms.getGroupManager().getGroup(group).getWeight().orElse(0);
                    return weight != null ? weight : 0;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    // Returns a tablist name with a prefix that sorts by group weight (higher = higher in tablist)
    public static Text getTablistNameSorted(ServerPlayerEntity player, int maxWeight) {
        int weight = getLuckPermsWeight(player);
        // Use color codes as invisible sort prefix: §0, §1, ... up to §f (16 max)
        // If more than 16 weights, use \u200B (zero-width space) repeated
        String sortPrefix;
        if (maxWeight <= 15) {
            int colorCode = Math.max(0, Math.min(15, maxWeight - weight)); // Lower weights = higher color code
            sortPrefix = "§" + Integer.toHexString(colorCode);
        } else {
            int pad = maxWeight - weight;
            sortPrefix = "\u200B".repeat(Math.max(0, pad));
        }
        // Add LuckPerms prefix
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
        } catch (Throwable ignored) {}
        String displayName = sortPrefix + prefix + player.getName().getString();
        return Text.literal(displayName);
    }

    // Update all tablist display names, sorted by group weight
    public static void updateTablistDisplayNamesSorted(MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        int maxWeight = 0;
        for (ServerPlayerEntity player : players) {
            int w = getLuckPermsWeight(player);
            if (w > maxWeight) maxWeight = w;
        }
        // Sort by weight desc, then name
        players.sort(Comparator.comparingInt(TablistUtil::getLuckPermsWeight).reversed().thenComparing(p -> p.getName().getString()));
        // Update each player's tablist name
        for (ServerPlayerEntity player : players) {
            player.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(Text.literal(""), Text.literal(""))); // Clear header/footer to force update
            // Use mixin to override getPlayerListName
            player.server.getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
        }
    }

    // Assigns each player to a scoreboard team for tablist sorting by LuckPerms group weight
    public static void updateTablistTeamsForAll(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        List<String> usedTeamNames = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            int weight = getLuckPermsWeight(player);
            String group = "default";
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().getUser(player.getUuid());
                if (user != null) {
                    group = user.getPrimaryGroup();
                }
            } catch (Throwable ignored) {}
            String teamName = String.format("%03d_%s", 999-weight, group);
            usedTeamNames.add(teamName);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.addTeam(teamName);
            }
            // Set prefix to LuckPerms prefix (for tablist)
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
            } catch (Throwable ignored) {}
            team.setPrefix(Text.literal(prefix));
            // Remove player from all other teams
            for (Team t : scoreboard.getTeams()) {
                t.getPlayerList().remove(player.getGameProfile().getName());
            }
            // Add player to the correct team
            team.getPlayerList().add(player.getGameProfile().getName());
        }
        // Clean up unused teams
        List<Team> toRemove = new ArrayList<>();
        for (Team t : scoreboard.getTeams()) {
            if (!usedTeamNames.contains(t.getName())) {
                toRemove.add(t);
            }
        }
        for (Team t : toRemove) {
            scoreboard.removeTeam(t);
        }
    }
}
