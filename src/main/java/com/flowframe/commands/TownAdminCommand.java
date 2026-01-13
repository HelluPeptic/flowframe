package com.flowframe.commands;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
}