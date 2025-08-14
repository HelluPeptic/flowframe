package com.flowframe.features.gungame;

/**
 * Enum for different Villager Defense game mode variants
 */
public enum VillagerDefenseMode {
    TIMED("Timed", "Teams compete to destroy enemy villager within time limit"),
    SURVIVAL("Survival", "First team to destroy enemy villager wins");
    
    private final String displayName;
    private final String description;
    
    VillagerDefenseMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}
