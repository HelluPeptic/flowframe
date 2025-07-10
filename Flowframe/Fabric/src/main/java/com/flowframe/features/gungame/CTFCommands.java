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

public class CTFCommands {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("battle")
                    .then(CommandManager.literal("ctf")
                        .then(CommandManager.literal("setbase")
                            .requires(source -> hasLuckPermsPermission(source, "flowframe.command.battle.ctf"))
                            .executes(CTFCommands::setBaseHere))
                        .then(CommandManager.literal("status")
                            .executes(CTFCommands::ctfStatus))
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
            
            if (battle.getBattleMode() != BattleMode.CAPTURE_THE_FLAG) {
                source.sendError(Text.literal("This command only works in Capture the Flag mode."));
                return 0;
            }
            
            BattleTeam playerTeam = battle.getPlayerTeam(player.getUuid());
            if (playerTeam == null) {
                source.sendError(Text.literal("You must be on a team to set a base."));
                return 0;
            }
            
            // Check if player is team leader
            if (!playerTeam.isTeamLeader(player.getUuid())) {
                source.sendError(Text.literal("Only the team leader can set the flag base."));
                return 0;
            }
            
            BlockPos basePos = player.getBlockPos();
            CaptureTheFlagManager ctf = battle.getCTFManager();
            if (ctf != null) {
                // Check if base already exists
                BlockPos existingBase = ctf.getTeamBase(playerTeam.getName());
                boolean isReplacing = existingBase != null;
                
                // Try to set the base
                boolean success = ctf.setFlagBase(playerTeam.getName(), basePos, player.getUuid());
                
                if (success) {
                    if (isReplacing) {
                        source.sendFeedback(() -> Text.literal("Updated " + playerTeam.getDisplayName() + " team flag base to " + basePos.getX() + ", " + basePos.getY() + ", " + basePos.getZ())
                            .formatted(Formatting.GREEN), false);
                    } else {
                        source.sendFeedback(() -> Text.literal("Set " + playerTeam.getDisplayName() + " team flag base at " + basePos.getX() + ", " + basePos.getY() + ", " + basePos.getZ())
                            .formatted(Formatting.GREEN), false);
                    }
                    return 1;
                } else {
                    if (battle.getState() == Battle.BattleState.ACTIVE) {
                        source.sendError(Text.literal("Cannot move flag base during an active game!"));
                    } else {
                        source.sendError(Text.literal("Failed to set flag base."));
                    }
                    return 0;
                }
            }
            
            source.sendError(Text.literal("CTF manager not initialized."));
            return 0;
            
        } catch (Exception e) {
            source.sendError(Text.literal("Only players can use this command."));
            return 0;
        }
    }
    
    private static int ctfStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Battle battle = Battle.getInstance();
        
        if (battle.getBattleMode() != BattleMode.CAPTURE_THE_FLAG) {
            source.sendError(Text.literal("This command only works in Capture the Flag mode."));
            return 0;
        }
        
        CaptureTheFlagManager ctf = battle.getCTFManager();
        if (ctf == null) {
            source.sendError(Text.literal("CTF manager not initialized."));
            return 0;
        }
        
        // Show team scores
        source.sendFeedback(() -> Text.literal("=== Capture the Flag Status ===")
            .formatted(Formatting.GOLD, Formatting.BOLD), false);
        
        for (String teamName : battle.getAvailableTeams()) {
            BattleTeam team = battle.getTeam(teamName);
            if (team != null) {
                int score = ctf.getTeamScores().getOrDefault(teamName, 0);
                source.sendFeedback(() -> Text.literal(teamName + ": " + score + " captures")
                    .formatted(team.getFormatting()), false);
            }
        }
        
        // Show flag status
        String flagStatus = ctf.getFlagStatus();
        source.sendFeedback(() -> Text.literal("Flag Status: " + flagStatus)
            .formatted(Formatting.YELLOW), false);
        
        return 1;
    }
    
    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
