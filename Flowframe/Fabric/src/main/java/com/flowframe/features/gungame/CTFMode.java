package com.flowframe.features.gungame;

/**
 * Enum for different CTF game mode variants
 */
public enum CTFMode {
    TIME("Time-based", "Teams compete for most captures within 10 minutes"),
    SCORE("Score-based", "First team to reach target score wins");
    
    private final String displayName;
    private final String description;
    
    CTFMode(String displayName, String description) {
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
