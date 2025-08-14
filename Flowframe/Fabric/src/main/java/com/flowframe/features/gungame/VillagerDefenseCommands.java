package com.flowframe.features.gungame;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class VillagerDefenseCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("battle")
                    .then(CommandManager.literal("villagerdefense")
                        .then(CommandManager.literal("setbase")
                            .requires(source -> hasLuckPermsPermission(source, "flowframe.command.battle.villagerdefense"))
                            .executes(VillagerDefenseCommands::setBaseHere))
                        .then(CommandManager.literal("status")
                            .executes(VillagerDefenseCommands::villagerDefenseStatus))
                    )
                )
            );
        });
    }
    
    private static int setBaseHere(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayer();
            Battle battle = Battle.getInstance();
            
            if (battle.getState() == Battle.BattleState.INACTIVE) {
                source.sendError(Text.literal("No battle is currently running."));
                return 0;
            }
            
            if (battle.getBattleMode() != BattleMode.VILLAGER_DEFENSE) {
                source.sendError(Text.literal("This command only works in Villager Defense mode."));
                return 0;
            }
            
            BattleTeam playerTeam = battle.getPlayerTeam(player.getUuid());
            if (playerTeam == null) {
                source.sendError(Text.literal("You must be on a team to set a villager base."));
                return 0;
            }
            
            BlockPos basePos = player.getBlockPos();
            VillagerDefenseManager villagerDefenseManager = battle.getVillagerDefenseManager();
            
            if (villagerDefenseManager == null) {
                source.sendError(Text.literal("Villager Defense is not initialized."));
                return 0;
            }
            
            boolean success = villagerDefenseManager.setVillagerBase(
                playerTeam.getName(), 
                basePos, 
                player.getUuid()
            );
            
            if (success) {
                source.sendFeedback(() -> Text.literal("Villager base set for " + playerTeam.getName() + 
                    " team at " + basePos.getX() + ", " + basePos.getY() + ", " + basePos.getZ())
                    .formatted(Formatting.GREEN), true);
            } else {
                source.sendError(Text.literal("Failed to set villager base. Only team leaders can set bases, " + 
                    "and bases cannot be moved during active games."));
            }
            
            return success ? 1 : 0;
            
        } catch (Exception e) {
            source.sendError(Text.literal("An error occurred while setting the villager base."));
            return 0;
        }
    }
    
    private static int villagerDefenseStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            Battle battle = Battle.getInstance();
            
            if (battle.getState() == Battle.BattleState.INACTIVE) {
                source.sendError(Text.literal("No battle is currently running."));
                return 0;
            }
            
            if (battle.getBattleMode() != BattleMode.VILLAGER_DEFENSE) {
                source.sendError(Text.literal("This command only works in Villager Defense mode."));
                return 0;
            }
            
            VillagerDefenseManager villagerDefenseManager = battle.getVillagerDefenseManager();
            if (villagerDefenseManager == null) {
                source.sendError(Text.literal("Villager Defense is not initialized."));
                return 0;
            }
            
            source.sendFeedback(() -> Text.literal("=== Villager Defense Status ===").formatted(Formatting.GOLD), false);
            source.sendFeedback(() -> Text.literal("Battle State: " + battle.getState().name()).formatted(Formatting.YELLOW), false);
            source.sendFeedback(() -> Text.literal("Round Timer Active: " + (villagerDefenseManager.isRoundTimerActive() ? "Yes" : "No")).formatted(Formatting.YELLOW), false);
            
            // Display villager bases
            source.sendFeedback(() -> Text.literal("\nVillager Bases:").formatted(Formatting.AQUA), false);
            for (var entry : villagerDefenseManager.getVillagerBases().entrySet()) {
                String teamName = entry.getKey();
                BlockPos basePos = entry.getValue();
                float health = villagerDefenseManager.getVillagerHealth(teamName);
                float maxHealth = villagerDefenseManager.getVillagerMaxHealth(teamName);
                
                String healthStatus = health > 0 ? 
                    "Health: " + Math.round(health) + "/" + Math.round(maxHealth) : "DEAD";
                
                source.sendFeedback(() -> Text.literal("  " + teamName.toUpperCase() + " Team: " + 
                    basePos.getX() + ", " + basePos.getY() + ", " + basePos.getZ() + " - " + healthStatus)
                    .formatted(health > 0 ? Formatting.GREEN : Formatting.RED), false);
            }
            
            // Display team scores if applicable
            source.sendFeedback(() -> Text.literal("\nTeam Scores:").formatted(Formatting.AQUA), false);
            for (var entry : villagerDefenseManager.getTeamScores().entrySet()) {
                String teamName = entry.getKey();
                int score = entry.getValue();
                source.sendFeedback(() -> Text.literal("  " + teamName.toUpperCase() + " Team: " + score)
                    .formatted(Formatting.WHITE), false);
            }
            
            return 1;
            
        } catch (Exception e) {
            source.sendError(Text.literal("An error occurred while fetching villager defense status."));
            return 0;
        }
    }
    
    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission, 3);
    }
}
