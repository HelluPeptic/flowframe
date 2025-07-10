package com.flowframe.features.gungame;

import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BattleTeam {
    private final String name;
    private Formatting formatting;
    private final Map<UUID, String> players = new ConcurrentHashMap<>();
    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    private UUID teamLeader; // First player to join becomes team leader
    
    public BattleTeam(String name, Formatting formatting) {
        this.name = name;
        this.formatting = formatting;
    }
    
    public void addPlayer(UUID playerId, String playerName) {
        players.put(playerId, playerName);
        eliminatedPlayers.remove(playerId); // In case they rejoin
        if (teamLeader == null) {
            teamLeader = playerId; // Set the first player as team leader
        }
    }
    
    public void removePlayer(UUID playerId) {
        players.remove(playerId);
        eliminatedPlayers.remove(playerId);
        if (playerId.equals(teamLeader)) {
            teamLeader = players.keySet().stream().findFirst().orElse(null); // Reassign team leader if the current leader leaves
        }
    }
    
    public void eliminatePlayer(UUID playerId) {
        if (players.containsKey(playerId)) {
            eliminatedPlayers.add(playerId);
        }
    }
    
    public boolean isEmpty() {
        // Team is empty if no players are alive (all are eliminated)
        return players.keySet().stream().allMatch(eliminatedPlayers::contains);
    }
    
    public boolean hasPlayer(UUID playerId) {
        return players.containsKey(playerId);
    }
    
    public boolean isPlayerEliminated(UUID playerId) {
        return eliminatedPlayers.contains(playerId);
    }
    
    public Set<UUID> getAlivePlayers() {
        Set<UUID> alive = new HashSet<>(players.keySet());
        alive.removeAll(eliminatedPlayers);
        return alive;
    }
    
    public Set<UUID> getAllPlayers() {
        return new HashSet<>(players.keySet());
    }
    
    public Map<UUID, String> getPlayerNames() {
        return new HashMap<>(players);
    }
    
    public String getName() {
        return name;
    }
    
    public String getColor() {
        return name; // For backwards compatibility
    }
    
    public String getDisplayName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
    
    public Formatting getFormatting() {
        return formatting;
    }
    
    public void setFormatting(Formatting formatting) {
        this.formatting = formatting;
    }
    
    public void resetForNextRound() {
        // Clear eliminated players for the next round - all players become alive again
        eliminatedPlayers.clear();
    }
    
    public int getPlayerCount() {
        return players.size();
    }
    
    public int getAlivePlayerCount() {
        return players.size() - eliminatedPlayers.size();
    }
    
    /**
     * Get the team leader (first player to join)
     */
    public UUID getTeamLeader() {
        return teamLeader;
    }
    
    /**
     * Check if a player is the team leader
     */
    public boolean isTeamLeader(UUID playerId) {
        return teamLeader != null && teamLeader.equals(playerId);
    }
    
    /**
     * Get the team leader's name for display purposes
     */
    public String getTeamLeaderName() {
        if (teamLeader != null && players.containsKey(teamLeader)) {
            return players.get(teamLeader);
        }
        return null;
    }
    
    /**
     * Reset team leader (used when leader leaves and team needs new leader)
     */
    public void reassignTeamLeader() {
        if (players.isEmpty()) {
            teamLeader = null;
        } else {
            // Assign leadership to the first remaining player
            teamLeader = players.keySet().iterator().next();
        }
    }
    
    @Override
    public String toString() {
        return "BattleTeam{" +
                "name='" + name + '\'' +
                ", players=" + players.size() +
                ", alive=" + getAlivePlayerCount() +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BattleTeam that = (BattleTeam) o;
        return Objects.equals(name, that.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
