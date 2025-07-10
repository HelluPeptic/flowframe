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
            
            if (!battle.isBattleLeader(player.getUuid()) && !hasLuckPermsPermission(source, "flowframe.command.battle.ctf")) {
                source.sendError(Text.literal("Only the battle leader can set flag bases."));
                return 0;
            }
            
            BattleTeam playerTeam = battle.getPlayerTeam(player.getUuid());
            if (playerTeam == null) {
                source.sendError(Text.literal("You must be on a team to set a base."));
                return 0;
            }
            
            BlockPos basePos = player.getBlockPos();
            CaptureTheFlagManager ctf = battle.getCTFManager();
            if (ctf != null) {
                // Debug: Show exactly what we're setting
                source.sendFeedback(() -> Text.literal("DEBUG: Setting base for team '" + playerTeam.getName() + "' at position " + basePos)
                    .formatted(Formatting.YELLOW), false);
                
                ctf.setFlagBase(playerTeam.getName(), basePos);
                
                // Verify the base was set correctly
                BlockPos retrievedBase = ctf.getTeamBase(playerTeam.getName());
                source.sendFeedback(() -> Text.literal("DEBUG: Retrieved base for team '" + playerTeam.getName() + "' is " + retrievedBase)
                    .formatted(Formatting.YELLOW), false);
                
                source.sendFeedback(() -> Text.literal("Set " + playerTeam.getDisplayName() + " team flag base at " + basePos)
                    .formatted(Formatting.GREEN), false);
                return 1;
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
