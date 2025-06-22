package com.flowframe.features.minetracer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

// Handles /flowframe minetracer commands (lookup, restore)
public class MineTracerCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("minetracer")
                    .then(CommandManager.literal("lookup")
                        .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                            .suggests(MineTracerCommand::suggestPlayers)
                            .executes(MineTracerCommand::lookup)
                        )
                    )
                    .then(CommandManager.literal("lookupblocks")
                        .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                            .suggests(MineTracerCommand::suggestPlayers)
                            .executes(MineTracerCommand::lookupBlocks)
                        )
                    )
                    .then(CommandManager.literal("lookupsigns")
                        .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                            .suggests(MineTracerCommand::suggestPlayers)
                            .executes(MineTracerCommand::lookupSigns)
                        )
                    )
                    .then(CommandManager.literal("restore")
                        .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                            .suggests(MineTracerCommand::suggestPlayers)
                            .executes(MineTracerCommand::restore)
                        )
                    )
                )
            );
        });
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        if (input.startsWith("range:")) {
            builder.suggest("range:");
        }
        if (input.contains("user:")) {
            for (String name : LogStorage.getAllPlayerNames()) {
                if (name.toLowerCase().startsWith(input.replaceFirst(".*user:", ""))) {
                    builder.suggest("user:" + name);
                }
            }
        } else if (input.endsWith(" ") || input.isEmpty() || input.startsWith("user:")) {
            for (String name : LogStorage.getAllPlayerNames()) {
                builder.suggest("user:" + name);
            }
        }
        return builder.buildFuture();
    }

    // Store last query for each player (UUID -> QueryContext)
    private static final Map<UUID, QueryContext> lastQueries = new HashMap<>();
    private static class QueryContext {
        public final java.util.List<java.util.Map.Entry<BlockPos, java.util.List<Object>>> groupedList;
        public final int entriesPerPage;
        public QueryContext(java.util.List<java.util.Map.Entry<BlockPos, java.util.List<Object>>> groupedList, int entriesPerPage) {
            this.groupedList = groupedList;
            this.entriesPerPage = entriesPerPage;
        }
    }

    public static int lookup(CommandContext<ServerCommandSource> ctx) {
        String arg = StringArgumentType.getString(ctx, "arg");
        ServerCommandSource source = ctx.getSource();
        String userFilter = null;
        String timeArg = null;
        int range = 100;
        if (arg.contains("user:")) {
            int idx = arg.indexOf("user:");
            userFilter = arg.substring(idx + 5).split(" ")[0];
            arg = arg.replace("user:" + userFilter, "").trim();
        }
        if (arg.contains("time:")) {
            int idx = arg.indexOf("time:");
            int end = arg.indexOf(' ', idx);
            timeArg = end == -1 ? arg.substring(idx + 5) : arg.substring(idx + 5, end);
            arg = arg.replace("time:" + timeArg, "").trim();
        }
        if (arg.startsWith("range:")) {
            range = Integer.parseInt(arg.substring("range:".length()).split(" ")[0]);
        }
        BlockPos playerPos = source.getPlayer().getBlockPos();
        Instant cutoff = null;
        if (timeArg != null) {
            long seconds = parseTimeArg(timeArg);
            cutoff = Instant.now().minusSeconds(seconds);
        }
        // Gather all logs
        var blockLogs = LogStorage.getBlockLogsInRange(playerPos, range, userFilter);
        var signLogs = LogStorage.getSignLogsInRange(playerPos, range, userFilter);
        var containerLogs = LogStorage.getLogsInRange(playerPos, range);
        if (userFilter != null) {
            final String userFilterFinal = userFilter;
            containerLogs.removeIf(entry -> !entry.playerName.equalsIgnoreCase(userFilterFinal));
        }
        // Apply time filter
        if (cutoff != null) {
            final Instant cutoffFinal = cutoff;
            blockLogs.removeIf(entry -> entry.timestamp.isBefore(cutoffFinal));
            signLogs.removeIf(entry -> entry.timestamp.isBefore(cutoffFinal));
            containerLogs.removeIf(entry -> entry.timestamp.isBefore(cutoffFinal));
        }
        // Group by position
        java.util.Map<BlockPos, java.util.List<Object>> grouped = new java.util.HashMap<>();
        for (var entry : blockLogs) grouped.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
        for (var entry : signLogs) grouped.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
        for (var entry : containerLogs) grouped.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
        java.util.List<java.util.Map.Entry<BlockPos, java.util.List<Object>>> groupedList = new java.util.ArrayList<>(grouped.entrySet());
        // Sort by most recent event in each group
        groupedList.sort((a, b) -> {
            Instant ta = getMostRecentTimestamp(a.getValue());
            Instant tb = getMostRecentTimestamp(b.getValue());
            return tb.compareTo(ta);
        });
        int entriesPerPage = 10;
        // Store this query for paging
        lastQueries.put(source.getPlayer().getUuid(), new QueryContext(groupedList, entriesPerPage));
        // Show first page
        return showLookupPage(source, groupedList, 1, entriesPerPage);
    }

    public static int lookupPage(CommandContext<ServerCommandSource> ctx) {
        int page = ctx.getArgument("page", Integer.class);
        ServerCommandSource source = ctx.getSource();
        QueryContext context = lastQueries.get(source.getPlayer().getUuid());
        if (context == null) {
            source.sendFeedback(() -> Text.literal("No previous lookup found. Use /flowframe minetracer [filters] first.").formatted(Formatting.RED), false);
            return Command.SINGLE_SUCCESS;
        }
        return showLookupPage(source, context.groupedList, page, context.entriesPerPage);
    }

    private static int showLookupPage(ServerCommandSource source, java.util.List<java.util.Map.Entry<BlockPos, java.util.List<Object>>> groupedList, int page, int entriesPerPage) {
        int totalPages = (int)Math.ceil((double)groupedList.size() / entriesPerPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * entriesPerPage;
        int end = Math.min(start + entriesPerPage, groupedList.size());
        if (groupedList.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No logs found.").formatted(Formatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(() -> Text.literal("----- MineTracer Lookup Results -----").formatted(Formatting.AQUA), false);
        for (int i = start; i < end; i++) {
            var pos = groupedList.get(i).getKey();
            String header = "(x" + pos.getX() + "/y" + pos.getY() + "/z" + pos.getZ() + ")";
            source.sendFeedback(() -> Text.literal(header).formatted(Formatting.DARK_AQUA), false);
            var events = groupedList.get(i).getValue();
            events.sort((a, b) -> {
                Instant ta = a instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)a).timestamp :
                              a instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)a).timestamp :
                              ((LogStorage.LogEntry)a).timestamp;
                Instant tb = b instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)b).timestamp :
                              b instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)b).timestamp :
                              ((LogStorage.LogEntry)b).timestamp;
                return tb.compareTo(ta);
            });
            for (var entry : events) {
                Text msg;
                String timeAgo = getTimeAgo(
                    entry instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)entry).timestamp :
                    entry instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)entry).timestamp :
                    ((LogStorage.LogEntry)entry).timestamp
                );
                if (entry instanceof LogStorage.BlockLogEntry be) {
                    msg = Text.literal(timeAgo + " ago - ")
                        .append(Text.literal(be.playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" " + (be.action.equals("remove") ? "removed" : "placed") + " ").formatted(be.action.equals("remove") ? Formatting.RED : Formatting.GREEN))
                        .append(Text.literal("#" + be.blockId).formatted(Formatting.YELLOW))
                        .append(Text.literal(" (" + getBlockName(be.blockId) + ").").formatted(Formatting.GRAY));
                } else if (entry instanceof LogStorage.SignLogEntry se) {
                    msg = Text.literal(timeAgo + " ago - ")
                        .append(Text.literal(se.playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" " + (se.action.equals("remove") ? "removed" : se.action.equals("edit") ? "edited" : "placed") + " sign: ").formatted(se.action.equals("remove") ? Formatting.RED : se.action.equals("edit") ? Formatting.YELLOW : Formatting.GREEN))
                        .append(Text.literal(se.text).formatted(Formatting.GRAY));
                } else if (entry instanceof LogStorage.LogEntry ce) {
                    msg = Text.literal(timeAgo + " ago - ")
                        .append(Text.literal(ce.playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" " + (ce.action.equals("remove") ? "removed" : "inserted") + " ").formatted(ce.action.equals("remove") ? Formatting.RED : Formatting.GREEN))
                        .append(Text.literal(ce.stack.getCount() > 1 ? "#" + ce.stack.getCount() + " " : ""))
                        .append(Text.literal(ce.stack.getName().getString()).formatted(Formatting.YELLOW))
                        .append(Text.literal(".").formatted(Formatting.GRAY));
                } else {
                    msg = Text.literal("[?] Unknown log entry");
                }
                source.sendFeedback(() -> msg, false);
            }
        }
        String pageMsg = "Page " + page + "/" + totalPages + ". View older data by typing \"/flowframe minetracer page <page>\".";
        source.sendFeedback(() -> Text.literal(pageMsg).formatted(Formatting.GRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    private static Instant getMostRecentTimestamp(java.util.List<Object> events) {
        Instant mostRecent = Instant.EPOCH;
        for (var entry : events) {
            Instant t = entry instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)entry).timestamp :
                        entry instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)entry).timestamp :
                        ((LogStorage.LogEntry)entry).timestamp;
            if (t.isAfter(mostRecent)) mostRecent = t;
        }
        return mostRecent;
    }

    private static String getTimeAgo(Instant timestamp) {
        long seconds = java.time.Duration.between(timestamp, Instant.now()).getSeconds();
        if (seconds < 60) return String.format("%.2fs", (double)seconds);
        long minutes = seconds / 60;
        if (minutes < 60) return String.format("%.2fm", (double)minutes + (seconds % 60) / 60.0);
        long hours = minutes / 60;
        if (hours < 24) return String.format("%.2fh", (double)hours + (minutes % 60) / 60.0);
        long days = hours / 24;
        if (days < 30) return String.format("%.2fd", (double)days + (hours % 24) / 24.0);
        long months = days / 30;
        if (months < 12) return String.format("%.2fmo", (double)months + (days % 30) / 30.0);
        long years = months / 12;
        return String.format("%.2fy", (double)years + (months % 12) / 12.0);
    }

    private static String getBlockName(String blockId) {
        // Try to get a readable block name from the block registry
        try {
            net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(new net.minecraft.util.Identifier(blockId));
            return block.getName().getString();
        } catch (Exception e) {
            return blockId;
        }
    }

    // Example for lookupBlocks feedback formatting
    public static int lookupBlocks(CommandContext<ServerCommandSource> ctx) {
        String arg = StringArgumentType.getString(ctx, "arg");
        ServerCommandSource source = ctx.getSource();
        String userFilter = null;
        String timeArg = null;
        if (arg.contains("user:")) {
            int idx = arg.indexOf("user:");
            userFilter = arg.substring(idx + 5).split(" ")[0];
            arg = arg.replace("user:" + userFilter, "").trim();
        }
        if (arg.contains("time:")) {
            int idx = arg.indexOf("time:");
            int end = arg.indexOf(' ', idx);
            timeArg = end == -1 ? arg.substring(idx + 5) : arg.substring(idx + 5, end);
            arg = arg.replace("time:" + timeArg, "").trim();
        }
        int range = 100;
        if (arg.startsWith("range:")) {
            range = Integer.parseInt(arg.substring("range:".length()).split(" ")[0]);
        }
        BlockPos playerPos = source.getPlayer().getBlockPos();
        var logs = LogStorage.getBlockLogsInRange(playerPos, range, userFilter);
        if (timeArg != null) {
            long seconds = parseTimeArg(timeArg);
            Instant cutoff = Instant.now().minusSeconds(seconds);
            logs.removeIf(entry -> entry.timestamp.isBefore(cutoff));
        }
        if (logs.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No block logs found.").formatted(Formatting.GRAY), false);
        } else {
            for (var entry : logs) {
                Text msg = Text.literal("[" + entry.timestamp + "] ")
                    .formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(entry.action + " ").formatted(entry.action.equals("remove") ? Formatting.RED : Formatting.GREEN))
                    .append(Text.literal(entry.playerName + " ").formatted(Formatting.AQUA))
                    .append(Text.literal(entry.blockId + " ").formatted(Formatting.YELLOW))
                    .append(Text.literal("at "))
                    .append(Text.literal(entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ())
                        .styled(style -> style.withColor(Formatting.GOLD).withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/tp " + entry.pos.getX() + " " + entry.pos.getY() + " " + entry.pos.getZ()))
                        .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport"))))) ;
                source.sendFeedback(() -> msg, false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }
    public static int lookupSigns(CommandContext<ServerCommandSource> ctx) {
        String arg = StringArgumentType.getString(ctx, "arg");
        ServerCommandSource source = ctx.getSource();
        String userFilter = null;
        if (arg.contains("user:")) {
            int idx = arg.indexOf("user:");
            userFilter = arg.substring(idx + 5).split(" ")[0];
            arg = arg.replace("user:" + userFilter, "").trim();
        }
        int range = 100;
        if (arg.startsWith("range:")) {
            range = Integer.parseInt(arg.substring("range:".length()).split(" ")[0]);
        }
        BlockPos playerPos = source.getPlayer().getBlockPos();
        var logs = LogStorage.getSignLogsInRange(playerPos, range, userFilter);
        if (logs.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No sign logs found."), false);
        } else {
            for (var entry : logs) {
                source.sendFeedback(() -> Text.literal(entry.timestamp + " " + entry.action + " " + entry.playerName + " at " + entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ() + ": " + entry.text), false);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int restore(CommandContext<ServerCommandSource> ctx) {
        String arg = StringArgumentType.getString(ctx, "arg");
        ServerCommandSource source = ctx.getSource();
        String userFilter = null;
        if (arg.contains("user:")) {
            int idx = arg.indexOf("user:");
            userFilter = arg.substring(idx + 5).split(" ")[0];
            arg = arg.replace("user:" + userFilter, "").trim();
        }
        int restored = 0;
        if (arg.startsWith("range:")) {
            final int range = Integer.parseInt(arg.substring("range:".length()).split(" ")[0]);
            BlockPos playerPos = source.getPlayer().getBlockPos();
            java.util.List<LogStorage.LogEntry> logs = LogStorage.getLogsInRange(playerPos, range);
            final String userFilterFinal = userFilter;
            if (userFilterFinal != null) {
                logs.removeIf(entry -> !entry.playerName.equalsIgnoreCase(userFilterFinal));
            }
            // Group by position
            java.util.Map<BlockPos, java.util.List<LogStorage.LogEntry>> byPos = new java.util.HashMap<>();
            for (LogStorage.LogEntry entry : logs) {
                byPos.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
            }
            for (var e : byPos.entrySet()) {
                restored += restoreToContainer(source, e.getKey(), e.getValue());
            }
        } else if (userFilter != null) {
            final String userFilterFinal = userFilter;
            java.util.List<LogStorage.LogEntry> logs = LogStorage.getLogsInRange(source.getPlayer().getBlockPos(), 100);
            logs.removeIf(entry -> !entry.playerName.equalsIgnoreCase(userFilterFinal));
            java.util.Map<BlockPos, java.util.List<LogStorage.LogEntry>> byPos = new java.util.HashMap<>();
            for (LogStorage.LogEntry entry : logs) {
                byPos.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
            }
            for (var e : byPos.entrySet()) {
                restored += restoreToContainer(source, e.getKey(), e.getValue());
            }
        } else {
            source.sendFeedback(() -> Text.literal("Usage: /flowframe minetracer restore range:<blocks> user:<name> or /flowframe minetracer restore user:<name>"), false);
            return Command.SINGLE_SUCCESS;
        }
        if (restored > 0) {
            int count = restored;
            source.sendFeedback(() -> Text.literal("Restored " + count + " items to containers."), false);
        } else {
            source.sendFeedback(() -> Text.literal("No items to restore or containers are full."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int restoreToContainer(ServerCommandSource source, BlockPos pos, java.util.List<LogStorage.LogEntry> logs) {
        var world = source.getWorld();
        var blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof net.minecraft.inventory.Inventory)) {
            return 0;
        }
        net.minecraft.inventory.Inventory inv = (net.minecraft.inventory.Inventory) blockEntity;
        int restored = 0;
        for (LogStorage.LogEntry entry : logs) {
            if ("remove".equals(entry.action)) {
                for (int i = 0; i < inv.size(); i++) {
                    if (inv.getStack(i).isEmpty()) {
                        inv.setStack(i, entry.stack.copy());
                        restored++;
                        break;
                    }
                }
            }
        }
        return restored;
    }

    private static long parseTimeArg(String arg) {
        // Accepts e.g. "time:1h,5m,2mo,1y,50s"
        Pattern p = Pattern.compile("(\\d+)([smhdwyo]|mo)");
        Matcher m = p.matcher(arg);
        long seconds = 0;
        while (m.find()) {
            int val = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            switch (unit) {
                case "s": seconds += val; break;
                case "m": seconds += val * 60L; break;
                case "h": seconds += val * 60L * 60L; break;
                case "d": seconds += val * 60L * 60L * 24L; break;
                case "w": seconds += val * 60L * 60L * 24L * 7L; break;
                case "mo": seconds += val * 60L * 60L * 24L * 30L; break;
                case "y": seconds += val * 60L * 60L * 24L * 365L; break;
                case "o": break; // ignore
            }
        }
        return seconds;
    }
}
