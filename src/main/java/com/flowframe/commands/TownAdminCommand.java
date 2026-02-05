package com.flowframe.commands;

import com.flowframe.config.FlowframeConfig;
import com.flowframe.town.TownData;
import com.flowframe.town.TownManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class TownAdminCommand {
    
    private static final SuggestionProvider<CommandSourceStack> TOWN_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(TownManager.getAllTownNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townadmin")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // Requires OP level 2
                .then(Commands.literal("forcedisband")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .executes(TownAdminCommand::forceDisbandTown)))
                .then(Commands.literal("forcemembercount")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                        .executes(TownAdminCommand::forceMemberCount))))
                .then(Commands.literal("forcetransferownership")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(TownAdminCommand::forceTransferOwnership))))
                .then(Commands.literal("autodisband")
                        .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                .executes(TownAdminCommand::setAutoDisbandDays)))
                .then(Commands.literal("autodisbandcountdown")
                        .then(Commands.argument("town", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .executes(TownAdminCommand::checkAutoDisbandCountdown)))
                .then(Commands.literal("refreshteams")
                        .executes(TownAdminCommand::refreshTeams)));
    }

    private static int forceDisbandTown(CommandContext<CommandSourceStack> context) {
        String townName = StringArgumentType.getString(context, "name");

        if (!TownManager.townExists(townName)) {
            context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Town '" + townName + "' does not exist!"));
            return 0;
        }

        int memberCount = TownManager.disbandTown(townName);
        TownManager.updateAllPlayerTeams();

        context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] Successfully disbanded town '" + townName + "' and removed " + memberCount + " members!"), true);
        
        return 1;
    }

    private static int forceMemberCount(CommandContext<CommandSourceStack> context) {
        String townName = StringArgumentType.getString(context, "name");
        int count = IntegerArgumentType.getInteger(context, "count");

        if (!TownManager.townExists(townName)) {
            context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Town '" + townName + "' does not exist!"));
            return 0;
        }

        TownData town = TownManager.getTown(townName);
        if (town == null) {
            context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Error loading town data!"));
            return 0;
        }

        TownManager.setFakeMemberCount(townName, count);
        String rank = town.getRank(count);
        
        context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] Set fake member count for '" + townName + "' to " + count + " (Rank: " + rank + ")"), true);
        
        return 1;
    }

    private static int refreshTeams(CommandContext<CommandSourceStack> context) {
        TownManager.updateAllPlayerTeams();
        context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] All team data refreshed!"), true);
        return 1;
    }    
    private static int forceTransferOwnership(CommandContext<CommandSourceStack> context) {
        String townName = StringArgumentType.getString(context, "town");
        
        try {
            ServerPlayer newOwner = EntityArgument.getPlayer(context, "player");
            
            if (!TownManager.townExists(townName)) {
                context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Town '" + townName + "' does not exist!"));
                return 0;
            }
            
            TownData town = TownManager.getTown(townName);
            UUID oldOwnerUUID = town.getOwner();
            UUID newOwnerUUID = newOwner.getUUID();
            String newOwnerName = newOwner.getDisplayName().getString();
            
            // Check if new owner is already the current owner
            if (oldOwnerUUID.equals(newOwnerUUID)) {
                context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] " + newOwnerName + " is already the owner of town '" + townName + "'!"));
                return 0;
            }
            
            // Add the new owner to the town if they aren't already a member
            if (TownManager.getPlayerTown(newOwnerUUID) == null) {
                TownManager.setPlayerTown(newOwnerUUID, townName);
                context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] Added " + newOwnerName + " to town '" + townName + "'"), true);
            }
            
            // Transfer ownership - use the admin force transfer version
            boolean success = TownManager.transferTownOwnership(townName, oldOwnerUUID, newOwnerUUID, newOwnerName);
            
            if (success) {
                // Try to get old owner name from cache
                String oldOwnerName = TownManager.getCachedPlayerName(oldOwnerUUID);
                
                context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] Successfully transferred ownership of town '" + townName + "' from " + oldOwnerName + " to " + newOwnerName + "!"), true);
                
                // Notify the new owner if they're online
                newOwner.sendSystemMessage(Component.literal("§6[TOWN] You are now the owner of town '" + townName + "'!"));
                
                return 1;
            } else {
                context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Failed to transfer ownership of town '" + townName + "'!"));
                return 0;
            }
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Error transferring ownership: " + e.getMessage()));
            return 0;
        }
    }

    private static int setAutoDisbandDays(CommandContext<CommandSourceStack> context) {
        int days = IntegerArgumentType.getInteger(context, "days");
        FlowframeConfig.setTownAutoDisbandDays(days);
        context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] Set auto-disband period to " + days + " days for founder-only towns."), true);
        return 1;
    }

    private static int checkAutoDisbandCountdown(CommandContext<CommandSourceStack> context) {
        String townName = StringArgumentType.getString(context, "town");
        
        TownData town = TownManager.getTown(townName);
        if (town == null) {
            context.getSource().sendFailure(Component.literal("§c[TOWNADMIN] Town '" + townName + "' not found!"));
            return 0;
        }
        
        boolean isFounderOnly = TownManager.isFounderOnlyTown(townName);
        
        if (!isFounderOnly) {
            context.getSource().sendSuccess(() -> Component.literal("§e[TOWNADMIN] Town '" + townName + "' has multiple members. It will not be auto-disbanded."), false);
            return 1;
        }
        
        long founderOnlySince = town.getFounderOnlySince();
        
        // If founder-only but no timestamp, start it now
        if (founderOnlySince == -1) {
            town.setFounderOnlySince(System.currentTimeMillis());
            TownManager.saveTowns();
            founderOnlySince = town.getFounderOnlySince();
            context.getSource().sendSuccess(() -> Component.literal("§a[TOWNADMIN] Started autodisband countdown for founder-only town '" + townName + "'!"), false);
        }
        
        int autoDisbandDays = FlowframeConfig.getTownAutoDisbandDays();
        long currentTime = System.currentTimeMillis();
        long timeElapsed = currentTime - founderOnlySince;
        long timeUntilDisband = (autoDisbandDays * 24L * 60L * 60L * 1000L) - timeElapsed;
        
        if (timeUntilDisband <= 0) {
            context.getSource().sendSuccess(() -> Component.literal("§c[TOWNADMIN] Town '" + townName + "' is overdue for auto-disbanding (will be disbanded on next check)"), false);
        } else {
            long days = timeUntilDisband / (24L * 60L * 60L * 1000L);
            long hours = (timeUntilDisband % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L);
            long minutes = (timeUntilDisband % (60L * 60L * 1000L)) / (60L * 1000L);
            
            if (days > 0) {
                context.getSource().sendSuccess(() -> Component.literal("§e[TOWNADMIN] Founder-only town '" + townName + "' will be auto-disbanded in " + days + " days, " + hours + " hours"), false);
            } else if (hours > 0) {
                context.getSource().sendSuccess(() -> Component.literal("§e[TOWNADMIN] Founder-only town '" + townName + "' will be auto-disbanded in " + hours + " hours, " + minutes + " minutes"), false);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("§e[TOWNADMIN] Founder-only town '" + townName + "' will be auto-disbanded in " + minutes + " minutes"), false);
            }
        }
        
        return 1;
    }
}