package com.flowframe.features.gungame;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.Arrays;
import java.util.List;

/**
 * Centralized battle settings management for all game modes
 */
public class BattleSettings {
    private static BattleSettings instance;
    
    // Global settings (apply to all game modes)
    private int voidLevel = -64; // Y-level to teleport players back to base when they fall below
    private boolean uniformHearts = true; // Whether to show uniform heart display (default enabled)
    private boolean uniformSpeed = true; // Whether all players have the same movement speed (default enabled)
    private boolean uniformJumpBoost = true; // Whether all players have the same jump boost (default enabled)
    private boolean uniformAttributes = true; // Whether to override all player attributes for fair play (default enabled)
    private boolean uniformHealth = true; // Whether to aggressively enforce 20 HP for all players (default enabled)
    
    // CTF-specific settings
    private CTFMode ctfMode = CTFMode.TIME;
    private int ctfTargetScore = 5;
    
    // Villager Defense-specific settings
    private int villagerHealth = 1000;
    
    public static BattleSettings getInstance() {
        if (instance == null) {
            instance = new BattleSettings();
        }
        return instance;
    }
    
    private BattleSettings() {}
    
    /**
     * Handle settings command execution
     */
    public int handleSettingsCommand(ServerCommandSource source, String gamemode, String setting, String value) {
        BattleMode mode = BattleMode.fromString(gamemode);
        if (mode == null) {
            source.sendError(Text.literal("Invalid game mode! Available modes: elimination, capture_the_flag, villager_defense"));
            return 0;
        }
        
        try {
            switch (setting.toLowerCase()) {
                case "voidlevel":
                    return setVoidLevel(source, value);
                case "uniformhearts":
                    return setUniformHearts(source, value);
                case "uniformspeed":
                    return setUniformSpeed(source, value);
                case "uniformjumpboost":
                    return setUniformJumpBoost(source, value);
                case "uniformattributes":
                    return setUniformAttributes(source, value);
                case "uniformhealth":
                    return setUniformHealth(source, value);
                case "ctfmode":
                    if (mode != BattleMode.CAPTURE_THE_FLAG) {
                        source.sendError(Text.literal("ctfmode setting is only available for capture_the_flag mode"));
                        return 0;
                    }
                    return setCTFMode(source, value);
                case "ctftargetscore":
                    if (mode != BattleMode.CAPTURE_THE_FLAG) {
                        source.sendError(Text.literal("ctftargetscore setting is only available for capture_the_flag mode"));
                        return 0;
                    }
                    return setCTFTargetScore(source, value);
                case "villagerhealth":
                    if (mode != BattleMode.VILLAGER_DEFENSE) {
                        source.sendError(Text.literal("villagerhealth setting is only available for villager_defense mode"));
                        return 0;
                    }
                    return setVillagerHealth(source, value);
                default:
                    source.sendError(Text.literal("Unknown setting: " + setting + ". Available settings: " + getAvailableSettings(mode)));
                    return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("Error setting " + setting + ": " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * Show current settings for a game mode
     */
    public int showSettings(ServerCommandSource source, String gamemode) {
        BattleMode mode = BattleMode.fromString(gamemode);
        if (mode == null) {
            source.sendError(Text.literal("Invalid game mode! Available modes: elimination, capture_the_flag, villager_defense"));
            return 0;
        }
        
        source.sendFeedback(() -> Text.literal("=== " + mode.getDisplayName() + " Settings ===").formatted(Formatting.GOLD), false);
        
        // Show global settings
        source.sendFeedback(() -> Text.literal("Global Settings:").formatted(Formatting.YELLOW), false);
        source.sendFeedback(() -> Text.literal("  voidlevel: " + voidLevel), false);
        source.sendFeedback(() -> Text.literal("  uniformhearts: " + (uniformHearts ? "enabled" : "disabled")), false);
        source.sendFeedback(() -> Text.literal("  uniformspeed: " + (uniformSpeed ? "enabled" : "disabled")), false);
        source.sendFeedback(() -> Text.literal("  uniformjumpboost: " + (uniformJumpBoost ? "enabled" : "disabled")), false);
        source.sendFeedback(() -> Text.literal("  uniformattributes: " + (uniformAttributes ? "enabled" : "disabled")), false);
        source.sendFeedback(() -> Text.literal("  uniformhealth: " + (uniformHealth ? "enabled" : "disabled")), false);
        
        // Show mode-specific settings
        switch (mode) {
            case CAPTURE_THE_FLAG:
                source.sendFeedback(() -> Text.literal("CTF Settings:").formatted(Formatting.YELLOW), false);
                source.sendFeedback(() -> Text.literal("  ctfmode: " + ctfMode.name().toLowerCase()), false);
                source.sendFeedback(() -> Text.literal("  ctftargetscore: " + ctfTargetScore), false);
                break;
            case VILLAGER_DEFENSE:
                source.sendFeedback(() -> Text.literal("Villager Defense Settings:").formatted(Formatting.YELLOW), false);
                source.sendFeedback(() -> Text.literal("  villagerhealth: " + villagerHealth), false);
                break;
        }
        
        return 1;
    }
    
    private int setVoidLevel(ServerCommandSource source, String value) {
        try {
            int level = Integer.parseInt(value);
            if (level > 320 || level < -64) {
                source.sendError(Text.literal("Void level must be between -64 and 320"));
                return 0;
            }
            this.voidLevel = level;
            source.sendFeedback(() -> Text.literal("Set void level to " + level).formatted(Formatting.GREEN), true);
            return 1;
        } catch (NumberFormatException e) {
            source.sendError(Text.literal("Invalid number: " + value));
            return 0;
        }
    }
    
    private int setUniformHearts(ServerCommandSource source, String value) {
        switch (value.toLowerCase()) {
            case "true":
            case "enabled":
            case "on":
                this.uniformHearts = true;
                source.sendFeedback(() -> Text.literal("Uniform hearts display enabled").formatted(Formatting.GREEN), true);
                return 1;
            case "false":
            case "disabled":
            case "off":
                this.uniformHearts = false;
                source.sendFeedback(() -> Text.literal("Uniform hearts display disabled").formatted(Formatting.GREEN), true);
                return 1;
            default:
                source.sendError(Text.literal("Invalid value: " + value + ". Use: true/false, enabled/disabled, or on/off"));
                return 0;
        }
    }
    
    private int setUniformSpeed(ServerCommandSource source, String value) {
        switch (value.toLowerCase()) {
            case "true":
            case "enabled":
            case "on":
                this.uniformSpeed = true;
                source.sendFeedback(() -> Text.literal("Uniform player speed enabled").formatted(Formatting.GREEN), true);
                
                // Apply speed changes to all active battle players
                Battle battle = Battle.getInstance();
                if (battle.getState() != Battle.BattleState.INACTIVE) {
                    battle.enforceUniformSpeedAll();
                }
                
                return 1;
            case "false":
            case "disabled":
            case "off":
                this.uniformSpeed = false;
                source.sendFeedback(() -> Text.literal("Uniform player speed disabled").formatted(Formatting.GREEN), true);
                
                // Reset speed changes for all active battle players
                Battle battle2 = Battle.getInstance();
                if (battle2.getState() != Battle.BattleState.INACTIVE) {
                    battle2.restoreOriginalSpeedAll();
                }
                
                return 1;
            default:
                source.sendError(Text.literal("Invalid value: " + value + ". Use: true/false, enabled/disabled, or on/off"));
                return 0;
        }
    }
    
    private int setUniformJumpBoost(ServerCommandSource source, String value) {
        switch (value.toLowerCase()) {
            case "true":
            case "enabled":
            case "on":
                this.uniformJumpBoost = true;
                source.sendFeedback(() -> Text.literal("Uniform player jump boost enabled").formatted(Formatting.GREEN), true);
                
                // Apply jump boost changes for all active battle players
                Battle battle = Battle.getInstance();
                if (battle.getState() != Battle.BattleState.INACTIVE) {
                    battle.applyUniformAttributes();
                }
                
                return 1;
            case "false":
            case "disabled":
            case "off":
                this.uniformJumpBoost = false;
                source.sendFeedback(() -> Text.literal("Uniform player jump boost disabled").formatted(Formatting.GREEN), true);
                
                // Reset attribute changes for all active battle players
                Battle battle2 = Battle.getInstance();
                if (battle2.getState() != Battle.BattleState.INACTIVE) {
                    battle2.resetPlayerAttributes();
                }
                
                return 1;
            default:
                source.sendError(Text.literal("Invalid value: " + value + ". Use: true/false, enabled/disabled, or on/off"));
                return 0;
        }
    }
    
    private int setUniformAttributes(ServerCommandSource source, String value) {
        switch (value.toLowerCase()) {
            case "true":
            case "enabled":
            case "on":
                this.uniformAttributes = true;
                source.sendFeedback(() -> Text.literal("Uniform player attributes enabled (overrides all skill tree bonuses)").formatted(Formatting.GREEN), true);
                
                // Apply all attribute overrides for all active battle players
                Battle battle = Battle.getInstance();
                if (battle.getState() != Battle.BattleState.INACTIVE) {
                    battle.applyUniformAttributes();
                }
                
                return 1;
            case "false":
            case "disabled":
            case "off":
                this.uniformAttributes = false;
                source.sendFeedback(() -> Text.literal("Uniform player attributes disabled (skill tree bonuses restored)").formatted(Formatting.GREEN), true);
                
                // Reset all attribute overrides for all active battle players
                Battle battle2 = Battle.getInstance();
                if (battle2.getState() != Battle.BattleState.INACTIVE) {
                    battle2.resetPlayerAttributes();
                }
                
                return 1;
            default:
                source.sendError(Text.literal("Invalid value: " + value + ". Use: true/false, enabled/disabled, or on/off"));
                return 0;
        }
    }
    
    private int setUniformHealth(ServerCommandSource source, String value) {
        switch (value.toLowerCase()) {
            case "true":
            case "enabled":
            case "on":
                this.uniformHealth = true;
                source.sendFeedback(() -> Text.literal("Uniform health enforcement enabled (all players locked to 20 HP)").formatted(Formatting.GREEN), true);
                
                // Apply aggressive health enforcement for all active battle players
                Battle battle = Battle.getInstance();
                if (battle.getState() != Battle.BattleState.INACTIVE) {
                    battle.enforceUniformHealthAll();
                }
                
                return 1;
            case "false":
            case "disabled":
            case "off":
                this.uniformHealth = false;
                source.sendFeedback(() -> Text.literal("Uniform health enforcement disabled (original health restored)").formatted(Formatting.GREEN), true);
                
                // Restore original health for all active battle players
                Battle battle2 = Battle.getInstance();
                if (battle2.getState() != Battle.BattleState.INACTIVE) {
                    battle2.restoreOriginalHealthAll();
                }
                
                return 1;
            default:
                source.sendError(Text.literal("Invalid value: " + value + ". Use: true/false, enabled/disabled, or on/off"));
                return 0;
        }
    }
    
    private int setCTFMode(ServerCommandSource source, String value) {
        try {
            CTFMode mode = CTFMode.valueOf(value.toUpperCase());
            this.ctfMode = mode;
            source.sendFeedback(() -> Text.literal("Set CTF mode to " + mode.name().toLowerCase()).formatted(Formatting.GREEN), true);
            
            // Update the active CTF manager if game is running
            Battle battle = Battle.getInstance();
            if (battle.getState() != Battle.BattleState.INACTIVE && battle.getBattleMode() == BattleMode.CAPTURE_THE_FLAG) {
                CaptureTheFlagManager ctfManager = battle.getCTFManager();
                if (ctfManager != null) {
                    ctfManager.setCTFMode(mode);
                }
            }
            
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Invalid CTF mode: " + value + ". Available modes: time, score"));
            return 0;
        }
    }
    
    private int setCTFTargetScore(ServerCommandSource source, String value) {
        try {
            int score = Integer.parseInt(value);
            if (score < 1 || score > 100) {
                source.sendError(Text.literal("Target score must be between 1 and 100"));
                return 0;
            }
            this.ctfTargetScore = score;
            source.sendFeedback(() -> Text.literal("Set CTF target score to " + score).formatted(Formatting.GREEN), true);
            
            // Update the active CTF manager if game is running
            Battle battle = Battle.getInstance();
            if (battle.getState() != Battle.BattleState.INACTIVE && battle.getBattleMode() == BattleMode.CAPTURE_THE_FLAG) {
                CaptureTheFlagManager ctfManager = battle.getCTFManager();
                if (ctfManager != null) {
                    ctfManager.setTargetScore(score);
                }
            }
            
            return 1;
        } catch (NumberFormatException e) {
            source.sendError(Text.literal("Invalid number: " + value));
            return 0;
        }
    }
    
    private int setVillagerHealth(ServerCommandSource source, String value) {
        try {
            int health = Integer.parseInt(value);
            if (health < 1 || health > 10000) {
                source.sendError(Text.literal("Villager health must be between 1 and 10000"));
                return 0;
            }
            this.villagerHealth = health;
            source.sendFeedback(() -> Text.literal("Set villager health to " + health).formatted(Formatting.GREEN), true);
            
            // Update active villager defense manager if game is running
            Battle battle = Battle.getInstance();
            if (battle.getState() != Battle.BattleState.INACTIVE && battle.getBattleMode() == BattleMode.VILLAGER_DEFENSE) {
                VillagerDefenseManager vdManager = battle.getVillagerDefenseManager();
                if (vdManager != null) {
                    vdManager.setVillagerHealth(health);
                }
            }
            
            return 1;
        } catch (NumberFormatException e) {
            source.sendError(Text.literal("Invalid number: " + value));
            return 0;
        }
    }
    
    private String getAvailableSettings(BattleMode mode) {
        StringBuilder settings = new StringBuilder("voidlevel, uniformhearts, uniformspeed, uniformjumpboost, uniformattributes, uniformhealth");
        
        switch (mode) {
            case CAPTURE_THE_FLAG:
                settings.append(", ctfmode, ctftargetscore");
                break;
            case VILLAGER_DEFENSE:
                settings.append(", villagerhealth");
                break;
        }
        
        return settings.toString();
    }
    
    /**
     * Get list of settings suggestions for tab completion
     */
    public List<String> getSettingSuggestions(BattleMode mode) {
        switch (mode) {
            case CAPTURE_THE_FLAG:
                return Arrays.asList("voidlevel", "uniformhearts", "uniformspeed", "ctfmode", "ctftargetscore");
            case VILLAGER_DEFENSE:
                return Arrays.asList("voidlevel", "uniformhearts", "uniformspeed", "villagerhealth");
            default:
                return Arrays.asList("voidlevel", "uniformhearts", "uniformspeed");
        }
    }
    
    /**
     * Get value suggestions for specific settings
     */
    public List<String> getValueSuggestions(String setting) {
        switch (setting.toLowerCase()) {
            case "uniformhearts":
            case "uniformspeed":
            case "uniformjumpboost":
            case "uniformattributes":
            case "uniformhealth":
                return Arrays.asList("enabled", "disabled");
            case "ctfmode":
                return Arrays.asList("time", "score");
            case "voidlevel":
                return Arrays.asList("-64", "-32", "0", "32", "64");
            case "ctftargetscore":
                return Arrays.asList("3", "5", "7", "10");
            case "villagerhealth":
                return Arrays.asList("500", "1000", "1500", "2000");
            default:
                return Arrays.asList();
        }
    }
    
    // Getters
    public int getVoidLevel() { return voidLevel; }
    public boolean isUniformHearts() { return uniformHearts; }
    public boolean isUniformSpeed() { return uniformSpeed; }
    public boolean isUniformJumpBoost() { return uniformJumpBoost; }
    public boolean isUniformAttributes() { return uniformAttributes; }
    public boolean isUniformHealth() { return uniformHealth; }
    public CTFMode getCTFMode() { return ctfMode; }
    public int getCTFTargetScore() { return ctfTargetScore; }
    public int getVillagerHealth() { return villagerHealth; }
}
