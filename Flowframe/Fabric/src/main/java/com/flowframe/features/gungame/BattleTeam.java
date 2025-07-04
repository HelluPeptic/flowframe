package com.flowframe.features.gungame;

import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BattleTeam {
    private final String name;
    private Formatting formatting;
    private final Map<UUID, String> players = new ConcurrentHashMap<>();
    private final Set<UUID> eliminatedPlayers = ConcurrentHashMap.newKeySet();
    
    public BattleTeam(String name, Formatting formatting) {
        this.name = name;
        this.formatting = formatting;
    }
    
    public void addPlayer(UUID playerId, String playerName) {
        players.put(playerId, playerName);
        eliminatedPlayers.remove(playerId); // In case they rejoin
    }
    
    public void removePlayer(UUID playerId) {
        players.remove(playerId);
        eliminatedPlayers.remove(playerId);
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
