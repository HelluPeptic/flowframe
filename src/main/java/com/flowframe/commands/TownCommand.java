package com.flowframe.commands;

import com.flowframe.town.TownManager;
import com.flowframe.town.TownData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;

import java.util.Arrays;

public class TownCommand {

    // Color suggestions provider
    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();
        
        String[] colors = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", 
                          "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", 
                          "yellow", "white"};
        
        for (String color : colors) {
            if (color.startsWith(input)) {
                builder.suggest(color);
            }
        }
        
        return builder.buildFuture();
    };

    // Town name suggestions provider
    private static final SuggestionProvider<CommandSourceStack> TOWN_SUGGESTIONS = (context, builder) -> {
        String input = builder.getRemaining().toLowerCase();
        
        for (String townName : TownManager.getAllTownNames()) {
            if (townName.toLowerCase().startsWith(input)) {
                builder.suggest(townName);
            }
        }
        
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("town")
                .then(Commands.literal("create")
                        .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests(COLOR_SUGGESTIONS)
                                        .then(Commands.argument("coords", BlockPosArgument.blockPos())
                                                .executes(TownCommand::createTown)))))
                .then(Commands.literal("join")
                        .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .executes(TownCommand::joinTown)))
                .then(Commands.literal("leave")
                        .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                        .executes(TownCommand::leaveTown))
                .then(Commands.literal("disband")
                        .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .executes(TownCommand::disbandTown)))
                .then(Commands.literal("edit")
                        .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .suggests(COLOR_SUGGESTIONS)
                                        .executes(TownCommand::editTown))))
                .then(Commands.literal("info")
                        .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(TOWN_SUGGESTIONS)
                                .executes(TownCommand::townInfo))));
    }

    private static int createTown(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String name = StringArgumentType.getString(context, "name");
        String colorName = StringArgumentType.getString(context, "color");
        BlockPos coords = BlockPosArgument.getBlockPos(context, "coords");

        try {
            // Check town name length
            if (name.length() > 20) {
                player.sendSystemMessage(Component.literal("§cTown name must be 20 characters or less!"));
                return 0;
            }

            // Parse color
            ChatFormatting color = parseChatColor(colorName);
            if (color == null) {
                player.sendSystemMessage(Component.literal("§cInvalid color: " + colorName));
                return 0;
            }

            // Check if town already exists
            if (TownManager.townExists(name)) {
                player.sendSystemMessage(Component.literal("§cTown '" + name + "' already exists!"));
                return 0;
            }

            // Check if player already owns a town
            if (TownManager.getPlayerOwnedTown(player.getUUID()) != null) {
                player.sendSystemMessage(Component.literal("§cYou already own a town!"));
                return 0;
            }

            // Create town
            TownData townData = new TownData(name, player.getUUID(), color, coords, player.level().dimension().toString());
            TownManager.createTown(townData);
            TownManager.setPlayerTown(player.getUUID(), name);

            return 1;

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§cError creating town: " + e.getMessage()));
            return 0;
        }
    }

    private static int joinTown(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String townName = StringArgumentType.getString(context, "name");

        // Check if town exists
        if (!TownManager.townExists(townName)) {
            player.sendSystemMessage(Component.literal("§cTown '" + townName + "' does not exist!"));
            return 0;
        }

        // Check if player is already in a town
        String currentTown = TownManager.getPlayerTown(player.getUUID());
        if (currentTown != null) {
            player.sendSystemMessage(Component.literal("§cYou are already in town '" + currentTown + "'! Leave first."));
            return 0;
        }

        // Join town
        TownData townData = TownManager.getTown(townName);
        String oldRank = null;
        int oldMemberCount = 0;
        
        if (townData != null) {
            oldMemberCount = TownManager.getTownMemberCount(townName);
            oldRank = townData.getRank(oldMemberCount);
        }
        
        TownManager.setPlayerTown(player.getUUID(), townName);

        if (townData != null) {
            player.sendSystemMessage(Component.literal("§7Town location: " + 
                    townData.getCoords().getX() + ", " + 
                    townData.getCoords().getY() + ", " + 
                    townData.getCoords().getZ()));
            
            // Check if town reached a new rank
            int newMemberCount = TownManager.getTownMemberCount(townName);
            String newRank = townData.getRank(newMemberCount);
            
            if (oldRank != null && !oldRank.equals(newRank)) {
                player.sendSystemMessage(Component.literal("§6Your town has grown into a " + newRank + "!"));
            }
        }

        return 1;
    }

    private static int leaveTown(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String currentTown = TownManager.getPlayerTown(player.getUUID());
        if (currentTown == null) {
            player.sendSystemMessage(Component.literal("§cYou are not in any town!"));
            return 0;
        }

        // Check if player owns the town
        if (TownManager.getPlayerOwnedTown(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("§cYou cannot leave a town you own! Disband it instead."));
            return 0;
        }

        TownManager.removePlayerFromTown(player.getUUID());
        player.sendSystemMessage(Component.literal("§aYou left '" + currentTown + "'!"));

        return 1;
    }

    private static int disbandTown(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String townName = StringArgumentType.getString(context, "name");

        // Check if town exists
        if (!TownManager.townExists(townName)) {
            player.sendSystemMessage(Component.literal("§cTown '" + townName + "' does not exist!"));
            return 0;
        }

        // Check if player owns the town
        String ownedTown = TownManager.getPlayerOwnedTown(player.getUUID());
        if (ownedTown == null || !ownedTown.equals(townName)) {
            player.sendSystemMessage(Component.literal("§cYou do not own town '" + townName + "'!"));
            return 0;
        }

        // Disband town
        int memberCount = TownManager.disbandTown(townName);
        player.sendSystemMessage(Component.literal("§aSuccessfully disbanded '" + townName + "'!"));
        player.sendSystemMessage(Component.literal("§7Removed " + memberCount + " members from the town."));

        return 1;
    }

    private static int editTown(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String townName = StringArgumentType.getString(context, "name");
        String colorName = StringArgumentType.getString(context, "color");

        // Check if town exists
        if (!TownManager.townExists(townName)) {
            player.sendSystemMessage(Component.literal("§cTown '" + townName + "' does not exist!"));
            return 0;
        }

        // Check if player owns the town
        String ownedTown = TownManager.getPlayerOwnedTown(player.getUUID());
        if (ownedTown == null || !ownedTown.equals(townName)) {
            player.sendSystemMessage(Component.literal("§cYou do not own town '" + townName + "'!"));
            return 0;
        }

        // Parse color
        ChatFormatting color = parseChatColor(colorName);
        if (color == null) {
            player.sendSystemMessage(Component.literal("§cInvalid color: " + colorName));
            return 0;
        }

        // Update town color
        TownManager.updateTownColor(townName, color);
        player.sendSystemMessage(Component.literal("§aUpdated color for '" + townName + "' to " + color + colorName + "§r!"));

        return 1;
    }

    private static int townInfo(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("This command can only be executed by a player"));
            return 0;
        }

        String townName = StringArgumentType.getString(context, "name");

        if (!TownManager.townExists(townName)) {
            player.sendSystemMessage(Component.literal("§cTown '" + townName + "' does not exist!"));
            return 0;
        }

        TownData town = TownManager.getTown(townName);
        if (town == null) {
            player.sendSystemMessage(Component.literal("§cError loading town data!"));
            return 0;
        }

        int memberCount = TownManager.getTownMemberCount(townName);
        String rank = town.getRank(memberCount);
        int membersToNext = town.getMembersToNextRank(memberCount);
        
        player.sendSystemMessage(Component.literal("§6-- " + town.getColor() + "§l" + townName.toUpperCase() + " §6--"));
        player.sendSystemMessage(Component.literal("§7Rank: " + "§6" + rank));
        player.sendSystemMessage(Component.literal("§7Members: " + "§6" + memberCount));
        
        if (membersToNext > 0) {
            player.sendSystemMessage(Component.literal("§7Members to Next Rank: " + "§6" + membersToNext));
        } else {
            player.sendSystemMessage(Component.literal("§7Members to Next Rank: §6Max Rank Reached"));
        }
        
        player.sendSystemMessage(Component.literal("§7Location: " +  "§6" + town.getCoords().getX() + ", " + "§6" + town.getCoords().getY() + ", " + "§6" + town.getCoords().getZ()));

        return 1;
    }

    private static ChatFormatting parseChatColor(String colorName) {
        return switch (colorName.toLowerCase()) {
            case "black" -> ChatFormatting.BLACK;
            case "dark_blue" -> ChatFormatting.DARK_BLUE;
            case "dark_green" -> ChatFormatting.DARK_GREEN;
            case "dark_aqua" -> ChatFormatting.DARK_AQUA;
            case "dark_red" -> ChatFormatting.DARK_RED;
            case "dark_purple" -> ChatFormatting.DARK_PURPLE;
            case "gold" -> ChatFormatting.GOLD;
            case "gray" -> ChatFormatting.GRAY;
            case "dark_gray" -> ChatFormatting.DARK_GRAY;
            case "blue" -> ChatFormatting.BLUE;
            case "green" -> ChatFormatting.GREEN;
            case "aqua" -> ChatFormatting.AQUA;
            case "red" -> ChatFormatting.RED;
            case "light_purple" -> ChatFormatting.LIGHT_PURPLE;
            case "yellow" -> ChatFormatting.YELLOW;
            case "white" -> ChatFormatting.WHITE;
            default -> null;
        };
    }
}