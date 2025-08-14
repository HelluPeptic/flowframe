package com.flowframe.features.gungame;

/**
 * Enum for different battle game modes
 */
public enum BattleMode {
    ELIMINATION("Elimination", "Last team standing wins!"),
    CAPTURE_THE_FLAG("Capture the Flag", "Capture the enemy flag and return it to your base!"),
    VILLAGER_DEFENSE("Villager Defense", "Protect your villager while destroying the enemy's villager!");
    
    private final String displayName;
    private final String description;
    
    BattleMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static BattleMode fromString(String mode) {
        for (BattleMode battleMode : values()) {
            if (battleMode.name().equalsIgnoreCase(mode) || 
                battleMode.getDisplayName().equalsIgnoreCase(mode)) {
                return battleMode;
            }
        }
        return null;
    }
}
