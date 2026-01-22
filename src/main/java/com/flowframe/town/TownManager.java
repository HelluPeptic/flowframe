package com.flowframe.town;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TownManager {
    private static final Map<String, TownData> towns = new ConcurrentHashMap<>();
    private static final Map<UUID, String> playerTowns = new ConcurrentHashMap<>();
    private static MinecraftServer currentServer = null;

    // Persistence
    private static File getTownsFile() {
        if (currentServer == null) return null;
        return new File(currentServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "flowframe_towns.json");
    }

    private static File getPlayerTownsFile() {
        if (currentServer == null) return null;
        return new File(currentServer.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "flowframe_player_towns.json");
    }

    public static void initialize(MinecraftServer server) {
        currentServer = server;
        loadTowns();
        loadPlayerTowns();
        updateAllPlayerTeams();
    }

    public static void shutdown() {
        saveTowns();
        savePlayerTowns();
    }

    // Town management
    public static void createTown(TownData townData) {
        towns.put(townData.getName(), townData);
        saveTowns();
        
        // Broadcast message and play sound for new town founding
        broadcastTownEvent(
            Component.literal("§6" + townData.getName() + " §6has been founded! §7Welcome to the world!"),
            "town_founded"
        );
    }

    public static boolean townExists(String name) {
        return towns.containsKey(name);
    }

    public static TownData getTown(String name) {
        return towns.get(name);
    }

    public static String getPlayerOwnedTown(UUID playerUuid) {
        for (TownData town : towns.values()) {
            if (town.getOwner().equals(playerUuid)) {
                return town.getName();
            }
        }
        return null;
    }

    public static int disbandTown(String name) {
        TownData town = towns.remove(name);
        if (town == null) return 0;

        // Remove all players from this town and their teams
        int memberCount = 0;
        Iterator<Map.Entry<UUID, String>> iterator = playerTowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, String> entry = iterator.next();
            if (entry.getValue().equals(name)) {
                // Properly remove player from team before removing from town
                removePlayerFromTeam(entry.getKey());
                iterator.remove();
                memberCount++;
            }
        }

        // Force removal of the town team entirely
        if (currentServer != null) {
            Scoreboard scoreboard = currentServer.getScoreboard();
            String teamName = "town_" + name.toLowerCase();
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                // Remove any remaining players from team (safety check)
                for (String playerName : team.getPlayers()) {
                    scoreboard.removePlayerFromTeam(playerName, team);
                }
                // Delete the team entirely
                scoreboard.removePlayerTeam(team);
            }
        }

        saveTowns();
        savePlayerTowns();
        return memberCount;
    }

    public static void updateTownColor(String name, ChatFormatting color) {
        TownData town = towns.get(name);
        if (town != null) {
            town.setColor(color);
            
            // Update team color for all members
            if (currentServer != null) {
                Scoreboard scoreboard = currentServer.getScoreboard();
                String teamName = "town_" + name.toLowerCase();
                PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                if (team != null) {
                    team.setColor(color);
                    team.setPlayerPrefix(Component.literal(town.getFormattedPrefix() + " "));
                }
            }
            
            saveTowns();
        }
    }

    public static void relocateTown(String name, BlockPos newCoords) {
        TownData town = towns.get(name);
        if (town != null) {
            town.setCoords(newCoords);
            saveTowns();
        }
    }

    public static Set<String> getAllTownNames() {
        return new HashSet<>(towns.keySet());
    }

    // Player management
    public static void setPlayerTown(UUID playerUuid, String townName) {
        // Remove from previous team if any
        removePlayerFromTown(playerUuid);
        
        // Check for rank change before adding player
        TownData town = getTown(townName);
        String oldRank = null;
        if (town != null) {
            int oldMemberCount = getTownMemberCount(townName);
            oldRank = town.getRank(oldMemberCount);
        }
        
        // Check if this is the town owner (creator) to avoid duplicate messages
        boolean isOwner = (town != null && town.getOwner().equals(playerUuid));
        
        playerTowns.put(playerUuid, townName);
        updatePlayerTeam(playerUuid, townName);
        savePlayerTowns();
        
        // Broadcast join message for non-owners (owners already get founding message)
        if (town != null && !isOwner) {
            // Get player name for the message
            String playerName = "Unknown Player";
            if (currentServer != null) {
                ServerPlayer player = currentServer.getPlayerList().getPlayer(playerUuid);
                if (player != null) {
                    playerName = player.getName().getString();
                }
            }
            
            broadcastTownEvent(
                Component.literal(town.getColor() + town.getName() + " §6welcomes a new member, " + "§6" + playerName + "§6!"),
                "town_join"
            );
        }
        
        // Check if town reached a new rank
        if (town != null) {
            int newMemberCount = getTownMemberCount(townName);
            String newRank = town.getRank(newMemberCount);
            
            if (oldRank != null && !oldRank.equals(newRank)) {
                // Town reached a new rank! Broadcast message and play sound
                broadcastTownEvent(
                    Component.literal("§6" + town.getFormattedPrefix() + " §6has grown into a " + newRank + "! §7(" + newMemberCount + " members)"),
                    "town_rankup"
                );
            }
        }
    }

    public static String getPlayerTown(UUID playerUuid) {
        return playerTowns.get(playerUuid);
    }

    public static TownData getTownByPlayer(String playerName) {
        if (currentServer == null) return null;
        
        ServerPlayer player = currentServer.getPlayerList().getPlayerByName(playerName);
        if (player == null) return null;
        
        String townName = getPlayerTown(player.getUUID());
        return townName != null ? getTown(townName) : null;
    }

    public static void removePlayerFromTown(UUID playerUuid) {
        String oldTown = playerTowns.remove(playerUuid);
        if (oldTown != null) {
            removePlayerFromTeam(playerUuid);
            
            // Check if the town team is now empty and clean it up
            if (currentServer != null) {
                Scoreboard scoreboard = currentServer.getScoreboard();
                String teamName = "town_" + oldTown.toLowerCase();
                PlayerTeam team = scoreboard.getPlayerTeam(teamName);
                if (team != null && team.getPlayers().isEmpty()) {
                    scoreboard.removePlayerTeam(team);
                }
            }
        }
        savePlayerTowns();
    }

    public static int getTownMemberCount(String townName) {
        TownData town = getTown(townName);
        if (town != null && town.hasFakeMemberCount()) {
            return town.getFakeMemberCount();
        }
        return (int) playerTowns.values().stream().filter(townName::equals).count();
    }

    public static void setFakeMemberCount(String townName, int count) {
        TownData town = getTown(townName);
        if (town != null) {
            town.setFakeMemberCount(count);
            saveTowns();
        }
    }

    // Helper class to represent town member information
    public static class TownMember {
        private final String name;
        private final boolean isOnline;
        private final boolean isFounder;
        
        public TownMember(String name, boolean isOnline, boolean isFounder) {
            this.name = name;
            this.isOnline = isOnline;
            this.isFounder = isFounder;
        }
        
        public String getName() { return name; }
        public boolean isOnline() { return isOnline; }
        public boolean isFounder() { return isFounder; }
    }

    public static List<TownMember> getTownMembers(String townName) {
        List<TownMember> members = new ArrayList<>();
        
        TownData town = getTown(townName);
        if (town == null) return members;
        
        // Add all members (including founder)
        for (Map.Entry<UUID, String> entry : playerTowns.entrySet()) {
            if (entry.getValue().equals(townName)) {
                UUID playerUuid = entry.getKey();
                boolean isFounder = town.getOwner().equals(playerUuid);
                
                // Get player name and online status
                String playerName;
                boolean isOnline = false;
                
                if (currentServer != null) {
                    ServerPlayer player = currentServer.getPlayerList().getPlayer(playerUuid);
                    if (player != null) {
                        playerName = player.getName().getString();
                        isOnline = true;
                    } else {
                        // For offline players, use founder name if this is the founder, otherwise generic name
                        playerName = isFounder ? town.getFounderName() : "Offline Player";
                    }
                } else {
                    playerName = isFounder ? town.getFounderName() : "Unknown Player";
                }
                
                members.add(new TownMember(playerName, isOnline, isFounder));
            }
        }
        
        // Sort members: online first, then offline, with founder always first within each group
        members.sort((a, b) -> {
            // Founder always comes first regardless of online status
            if (a.isFounder() && !b.isFounder()) return -1;
            if (!a.isFounder() && b.isFounder()) return 1;
            
            // Then sort by online status (online first)
            if (a.isOnline() && !b.isOnline()) return -1;
            if (!a.isOnline() && b.isOnline()) return 1;
            
            // Finally sort alphabetically by name
            return a.getName().compareToIgnoreCase(b.getName());
        });
        
        return members;
    }

    public static boolean transferTownOwnership(String townName, UUID currentOwner, UUID newOwner, String newOwnerName) {
        TownData town = getTown(townName);
        if (town == null || !town.getOwner().equals(currentOwner)) {
            return false;
        }

        // Update ownership (founder name remains the original founder)
        town.setOwner(newOwner);
        saveTowns();

        return true;
    }

    public static void broadcastToTown(String townName, Component message, UUID... excludePlayers) {
        if (currentServer == null) return;
        
        Set<UUID> excludeSet = new HashSet<>();
        for (UUID uuid : excludePlayers) {
            excludeSet.add(uuid);
        }

        // Send message to all members of the town
        for (Map.Entry<UUID, String> entry : playerTowns.entrySet()) {
            if (entry.getValue().equals(townName) && !excludeSet.contains(entry.getKey())) {
                ServerPlayer player = currentServer.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    player.sendSystemMessage(message);
                }
            }
        }
    }

    public static TownData getPlayerTownData(UUID playerUuid) {
        String townName = getPlayerTown(playerUuid);
        return townName != null ? getTown(townName) : null;
    }

    // Team management methods
    private static void updatePlayerTeam(UUID playerUuid, String townName) {
        if (currentServer == null) return;
        
        TownData town = getTown(townName);
        if (town == null) return;
        
        Scoreboard scoreboard = currentServer.getScoreboard();
        String teamName = "town_" + townName.toLowerCase();
        
        // Get or create team
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setDisplayName(Component.literal(townName));
        }
        
        // Always update team properties to ensure they're current
        team.setColor(town.getColor());
        team.setPlayerPrefix(Component.literal(town.getFormattedPrefix() + " "));
        
        // Add player to team if they're online
        ServerPlayer player = currentServer.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            // Remove from any other team first
            PlayerTeam currentTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (currentTeam != null && !currentTeam.equals(team)) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), currentTeam);
            }
            
            // Add to correct team
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }
    
    private static void removePlayerFromTeam(UUID playerUuid) {
        if (currentServer == null) return;
        
        ServerPlayer player = currentServer.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;
        
        Scoreboard scoreboard = currentServer.getScoreboard();
        PlayerTeam currentTeam = scoreboard.getPlayersTeam(player.getScoreboardName());
        
        if (currentTeam != null && currentTeam.getName().startsWith("town_")) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName(), currentTeam);
            
            // Remove empty teams
            if (currentTeam.getPlayers().isEmpty()) {
                scoreboard.removePlayerTeam(currentTeam);
            }
        }
    }
    
    public static void updateAllPlayerTeams() {
        if (currentServer == null) return;
        
        // Don't clean up stale teams during initialization - let teams restore naturally
        // cleanupStaleTeams();
        
        // Update all current player teams (only for online players)
        for (Map.Entry<UUID, String> entry : playerTowns.entrySet()) {
            updatePlayerTeam(entry.getKey(), entry.getValue());
        }
    }
    
    // Method to restore a player's team when they join the server
    public static void restorePlayerTeamOnJoin(ServerPlayer player) {
        if (player == null) return;
        
        String townName = getPlayerTown(player.getUUID());
        if (townName != null) {
            updatePlayerTeam(player.getUUID(), townName);
        }
    }
    
    // Clean up teams for towns that no longer exist or have no members
    private static void cleanupStaleTeams() {
        if (currentServer == null) return;
        
        Scoreboard scoreboard = currentServer.getScoreboard();
        Set<PlayerTeam> teamsToRemove = new HashSet<>();
        
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            if (team.getName().startsWith("town_")) {
                String townName = team.getName().substring(5); // Remove "town_" prefix
                
                // Only remove teams if the town doesn't exist at all
                // Don't remove based on hasMembers as players might not be online yet
                boolean townExists = towns.containsKey(townName);
                
                if (!townExists) {
                    teamsToRemove.add(team);
                }
            }
        }
        
        // Remove stale teams
        for (PlayerTeam team : teamsToRemove) {
            // Remove all players from the team first
            for (String playerName : team.getPlayers()) {
                scoreboard.removePlayerFromTeam(playerName, team);
            }
            scoreboard.removePlayerTeam(team);
        }
    }

    // Persistence methods
    private static void saveTowns() {
        try {
            File file = getTownsFile();
            if (file == null) return;

            JsonObject root = new JsonObject();
            JsonArray townsList = new JsonArray();

            for (TownData town : towns.values()) {
                JsonObject townObj = new JsonObject();
                townObj.addProperty("name", town.getName());
                townObj.addProperty("owner", town.getOwner().toString());
                townObj.addProperty("founderName", town.getFounderName());
                townObj.addProperty("color", town.getColor().getName());
                townObj.addProperty("x", town.getCoords().getX());
                townObj.addProperty("y", town.getCoords().getY());
                townObj.addProperty("z", town.getCoords().getZ());
                townObj.addProperty("dimension", town.getDimension());
                if (town.hasFakeMemberCount()) {
                    townObj.addProperty("fakeMemberCount", town.getFakeMemberCount());
                }
                townsList.add(townObj);
            }

            root.add("towns", townsList);
            
            try (FileWriter writer = new FileWriter(file)) {
                new Gson().toJson(root, writer);
            }

        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save towns: " + e.getMessage());
        }
    }

    private static void savePlayerTowns() {
        try {
            File file = getPlayerTownsFile();
            if (file == null) return;

            JsonObject root = new JsonObject();
            JsonArray playersList = new JsonArray();

            for (Map.Entry<UUID, String> entry : playerTowns.entrySet()) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("player", entry.getKey().toString());
                playerObj.addProperty("town", entry.getValue());
                playersList.add(playerObj);
            }

            root.add("players", playersList);
            
            try (FileWriter writer = new FileWriter(file)) {
                new Gson().toJson(root, writer);
            }

        } catch (IOException e) {
            System.err.println("[FLOWFRAME] Failed to save player towns: " + e.getMessage());
        }
    }

    private static void loadTowns() {
        try {
            File file = getTownsFile();
            if (file == null || !file.exists()) return;

            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray townsList = root.getAsJsonArray("towns");

                for (JsonElement element : townsList) {
                    JsonObject townObj = element.getAsJsonObject();

                    String name = townObj.get("name").getAsString();
                    UUID owner = UUID.fromString(townObj.get("owner").getAsString());
                    // Load founder name with backward compatibility
                    String founderName = townObj.has("founderName") ? townObj.get("founderName").getAsString() : "Unknown";
                    ChatFormatting color = ChatFormatting.getByName(townObj.get("color").getAsString());
                    BlockPos coords = new BlockPos(
                            townObj.get("x").getAsInt(),
                            townObj.get("y").getAsInt(),
                            townObj.get("z").getAsInt()
                    );
                    String dimension = townObj.get("dimension").getAsString();

                    if (color == null) color = ChatFormatting.WHITE;

                    TownData townData = new TownData(name, owner, founderName, color, coords, dimension);
                    
                    // Load fake member count if present
                    if (townObj.has("fakeMemberCount")) {
                        townData.setFakeMemberCount(townObj.get("fakeMemberCount").getAsInt());
                    }
                    
                    towns.put(name, townData);
                }
            }

        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to load towns: " + e.getMessage());
        }
    }

    private static void loadPlayerTowns() {
        try {
            File file = getPlayerTownsFile();
            if (file == null || !file.exists()) return;

            try (FileReader reader = new FileReader(file)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray playersList = root.getAsJsonArray("players");

                for (JsonElement element : playersList) {
                    JsonObject playerObj = element.getAsJsonObject();

                    UUID player = UUID.fromString(playerObj.get("player").getAsString());
                    String town = playerObj.get("town").getAsString();

                    // Only restore if the town still exists
                    if (towns.containsKey(town)) {
                        playerTowns.put(player, town);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[FLOWFRAME] Failed to load player towns: " + e.getMessage());
        }
    }
    
    // Helper method to broadcast town events with sound to all online players
    private static void broadcastTownEvent(Component message, String soundType) {
        if (currentServer == null) return;
        
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            
            // Try to play sound - use different sounds based on type
            try {
                if ("town_founded".equals(soundType)) {
                    // Very quiet XP pickup or item pickup for town founding
                    try {
                        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.1f, 1.2f);
                    } catch (Exception e1) {
                        try {
                            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.1f, 1.5f);
                        } catch (Exception e2) {
                            System.err.println("[FLOWFRAME] Could not play town founding sound");
                        }
                    }
                } else if ("town_rankup".equals(soundType)) {
                    // Very quiet level up or XP pickup for rank up
                    try {
                        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.1f, 1.0f);
                    } catch (Exception e1) {
                        try {
                            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.1f, 0.8f);
                        } catch (Exception e2) {
                            System.err.println("[FLOWFRAME] Could not play town rank up sound");
                        }
                    }
                } else if ("town_join".equals(soundType)) {
                    // Very quiet XP pickup or item pickup for joining
                    try {
                        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.1f, 1.0f);
                    } catch (Exception e1) {
                        try {
                            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.1f, 1.0f);
                        } catch (Exception e2) {
                            System.err.println("[FLOWFRAME] Could not play town join sound");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[FLOWFRAME] Sound system error: " + e.getMessage());
            }
        }
    }
}