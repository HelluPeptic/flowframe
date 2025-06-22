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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
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
                    .then(CommandManager.literal("restore")
                        .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                            .suggests(MineTracerCommand::suggestPlayers)
                            .executes(MineTracerCommand::restore)
                        )
                    )
                    .then(CommandManager.literal("page")
                        .then(CommandManager.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(MineTracerCommand::lookupPage)
                        )
                    )
                )
            );
        });
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String input = builder.getInput();
        int start = builder.getStart();
        String[] parts = input.substring(start).split(" ");
        String last = parts.length > 0 ? parts[parts.length - 1] : "";
        int lastStart = input.lastIndexOf(last);
        SuggestionsBuilder subBuilder = builder.createOffset(lastStart);

        // Suggest main filter keywords if not already present in the input
        if (!input.contains("user:")) subBuilder.suggest("user:");
        if (!input.contains("time:")) subBuilder.suggest("time:");
        if (!input.contains("action:")) subBuilder.suggest("action:");
        if (!input.contains("range:")) subBuilder.suggest("range:");

        // Suggest player names after user:
        if (last.startsWith("user:")) {
            String afterUser = last.substring(5);
            for (String name : LogStorage.getAllPlayerNames()) {
                if (name.toLowerCase().startsWith(afterUser.toLowerCase())) {
                    subBuilder.suggest("user:" + name);
                }
            }
        } else if (last.startsWith("action:")) {
            String afterAction = last.substring(7);
            String[] actions = {"inventory", "deposited", "withdrew"};
            for (String act : actions) {
                if (act.startsWith(afterAction)) {
                    subBuilder.suggest("action:" + act);
                }
            }
        } else if (last.startsWith("time:")) {
            String afterTime = last.substring(5);
            String[] times = {"1m", "5m", "1h", "1d", "1w", "1mo", "1y"};
            for (String t : times) {
                if (t.startsWith(afterTime)) {
                    subBuilder.suggest("time:" + t);
                }
            }
        } else if (last.startsWith("range:")) {
            String afterRange = last.substring(6);
            String[] ranges = {"10", "50", "100", "200"};
            for (String r : ranges) {
                if (r.startsWith(afterRange)) {
                    subBuilder.suggest("range:" + r);
                }
            }
        } else {
            // If the last word is empty, suggest all main keywords
            if (last.isEmpty()) {
                subBuilder.suggest("user:");
                subBuilder.suggest("time:");
                subBuilder.suggest("action:");
                subBuilder.suggest("range:");
            }
        }
        return subBuilder.buildFuture();
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
        boolean showInventory = false;
        String actionFilter = null;
        // Accept time:, range:, user:, action: anywhere in the string
        for (String part : arg.split(" ")) {
            if (part.startsWith("user:")) {
                userFilter = part.substring(5);
            } else if (part.startsWith("time:")) {
                timeArg = part.substring(5);
            } else if (part.startsWith("range:")) {
                try { range = Integer.parseInt(part.substring(6)); } catch (Exception ignored) {}
            } else if (part.startsWith("action:")) {
                String act = part.substring(7).toLowerCase();
                if (act.equals("inventory")) showInventory = true;
                else actionFilter = act;
            }
        }
        BlockPos playerPos = source.getPlayer().getBlockPos();
        Instant cutoff = null;
        if (timeArg != null) {
            long seconds = parseTimeArg(timeArg);
            cutoff = Instant.now().minusSeconds(seconds);
        }
        // Gather all logs
        List<LogStorage.BlockLogEntry> blockLogs = LogStorage.getBlockLogsInRange(playerPos, range, userFilter);
        List<LogStorage.SignLogEntry> signLogs = LogStorage.getSignLogsInRange(playerPos, range, userFilter);
        List<LogStorage.LogEntry> containerLogs = LogStorage.getLogsInRange(playerPos, range);
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
        // Filter out inventory logs unless action:inventory is present
        if (!showInventory) {
            containerLogs.removeIf(entry -> "inventory".equals(entry.action));
        }
        // Filter by action if specified
        if (actionFilter != null) {
            final String actionFilterFinal = actionFilter;
            containerLogs.removeIf(entry -> !entry.action.equalsIgnoreCase(actionFilterFinal));
        }
        // Group by position
        java.util.Map<BlockPos, java.util.List<Object>> grouped = new java.util.HashMap<>();
        for (LogStorage.BlockLogEntry entry : blockLogs) grouped.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
        for (LogStorage.SignLogEntry entry : signLogs) grouped.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
        for (LogStorage.LogEntry entry : containerLogs) grouped.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
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
            BlockPos pos = groupedList.get(i).getKey();
            String header = "(x" + pos.getX() + "/y" + pos.getY() + "/z" + pos.getZ() + ")";
            source.sendFeedback(() -> Text.literal(header).formatted(Formatting.DARK_AQUA), false);
            List<Object> events = groupedList.get(i).getValue();
            events.sort((a, b) -> {
                Instant ta = a instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)a).timestamp :
                              a instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)a).timestamp :
                              ((LogStorage.LogEntry)a).timestamp;
                Instant tb = b instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)b).timestamp :
                              b instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)b).timestamp :
                              ((LogStorage.LogEntry)b).timestamp;
                return tb.compareTo(ta);
            });
            for (Object entry : events) {
                Text msg;
                String timeAgo = getTimeAgo(
                    entry instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)entry).timestamp :
                    entry instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)entry).timestamp :
                    ((LogStorage.LogEntry)entry).timestamp
                );
                if (entry instanceof LogStorage.BlockLogEntry be) {
                    String actionStr = be.action;
                    Formatting color;
                    if ("broke".equals(actionStr)) {
                        actionStr = "broke block";
                        color = Formatting.RED;
                    } else if ("place".equals(actionStr) || "placed".equals(actionStr)) {
                        actionStr = "placed block";
                        color = Formatting.GREEN;
                    } else {
                        color = Formatting.GRAY;
                    }
                    msg = Text.literal(timeAgo + " ago - ")
                        .append(Text.literal(be.playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" " + actionStr + " ").formatted(color))
                        .append(Text.literal("#" + be.blockId).formatted(Formatting.YELLOW))
                        .append(Text.literal(" (" + getBlockName(be.blockId) + ").").formatted(Formatting.GRAY));
                } else if (entry instanceof LogStorage.SignLogEntry se) {
                    // Try to parse previous and new message from se.text or se.nbt
                    String prevMsg = null;
                    String newMsg = null;
                    // If nbt contains both, try to extract them
                    if (se.nbt != null && se.nbt.contains("previous") && se.nbt.contains("new")) {
                        // Example: {previous:"old text",new:"new text"}
                        String nbt = se.nbt;
                        int prevIdx = nbt.indexOf("previous");
                        int newIdx = nbt.indexOf("new");
                        if (prevIdx != -1 && newIdx != -1) {
                            int prevStart = nbt.indexOf('"', prevIdx + 8) + 1;
                            int prevEnd = nbt.indexOf('"', prevStart);
                            int newStart = nbt.indexOf('"', newIdx + 3) + 1;
                            int newEnd = nbt.indexOf('"', newStart);
                            if (prevStart > 0 && prevEnd > prevStart) prevMsg = nbt.substring(prevStart, prevEnd);
                            if (newStart > 0 && newEnd > newStart) newMsg = nbt.substring(newStart, newEnd);
                        }
                    }
                    if (prevMsg == null) prevMsg = "(unknown)";
                    if (newMsg == null) newMsg = se.text;
                    msg = Text.literal(timeAgo + " ago - ")
                        .append(Text.literal(se.playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" edited sign: ").formatted(Formatting.YELLOW))
                        .append(Text.literal("[before] ").formatted(Formatting.GRAY))
                        .append(Text.literal(prevMsg).formatted(Formatting.RED))
                        .append(Text.literal(" [after] ").formatted(Formatting.GRAY))
                        .append(Text.literal(newMsg).formatted(Formatting.GREEN));
                } else if (entry instanceof LogStorage.LogEntry ce) {
                    String desc;
                    String containerName = "container";
                    String itemStr = (ce.stack.getCount() > 1 ? "#" + ce.stack.getCount() + " " : "") + ce.stack.getName().getString();
                    if ("deposited".equals(ce.action)) {
                        desc = "deposited " + itemStr + " into " + containerName;
                    } else if ("withdrew".equals(ce.action)) {
                        desc = "withdrew " + itemStr + " from " + containerName;
                    } else if ("inventory".equals(ce.action)) {
                        desc = "inventory transaction: " + itemStr;
                    } else {
                        desc = ce.action;
                    }
                    Formatting color = "withdrew".equals(ce.action) ? Formatting.RED : "deposited".equals(ce.action) ? Formatting.GREEN : Formatting.GRAY;
                    msg = Text.literal(timeAgo + " ago - ")
                        .append(Text.literal(ce.playerName).formatted(Formatting.AQUA))
                        .append(Text.literal(" " + desc).formatted(color))
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
        for (Object entry : events) {
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
        int blockRestored = 0;
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
            for (Map.Entry<BlockPos, java.util.List<LogStorage.LogEntry>> e : byPos.entrySet()) {
                restored += restoreToContainer(source, e.getKey(), e.getValue());
            }
            // --- Block restore logic ---
            java.util.List<LogStorage.BlockLogEntry> blockLogs = LogStorage.getBlockLogsInRange(playerPos, range, userFilter);
            for (LogStorage.BlockLogEntry entry : blockLogs) {
                if ("broke".equals(entry.action)) {
                    // Restore block that was broken
                    try {
                        net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(new net.minecraft.util.Identifier(entry.blockId));
                        net.minecraft.block.BlockState state = block.getDefaultState();
                        if (entry.nbt != null && !entry.nbt.isEmpty()) {
                            // Try to parse NBT for block entity
                            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(entry.nbt);
                            source.getWorld().setBlockState(entry.pos, state);
                            net.minecraft.block.entity.BlockEntity be = source.getWorld().getBlockEntity(entry.pos);
                            if (be != null) be.readNbt(nbt);
                        } else {
                            source.getWorld().setBlockState(entry.pos, state);
                        }
                        blockRestored++;
                    } catch (Exception e) { e.printStackTrace(); }
                } else if ("place".equals(entry.action)) {
                    // Undo block placement (set to air)
                    source.getWorld().setBlockState(entry.pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                    blockRestored++;
                }
            }
        } else if (userFilter != null) {
            final String userFilterFinal = userFilter;
            java.util.List<LogStorage.LogEntry> logs = LogStorage.getLogsInRange(source.getPlayer().getBlockPos(), 100);
            logs.removeIf(entry -> !entry.playerName.equalsIgnoreCase(userFilterFinal));
            java.util.Map<BlockPos, java.util.List<LogStorage.LogEntry>> byPos = new java.util.HashMap<>();
            for (LogStorage.LogEntry entry : logs) {
                byPos.computeIfAbsent(entry.pos, k -> new java.util.ArrayList<>()).add(entry);
            }
            for (Map.Entry<BlockPos, java.util.List<LogStorage.LogEntry>> e : byPos.entrySet()) {
                restored += restoreToContainer(source, e.getKey(), e.getValue());
            }
            // --- Block restore logic for user ---
            java.util.List<LogStorage.BlockLogEntry> blockLogs = LogStorage.getBlockLogsInRange(source.getPlayer().getBlockPos(), 100, userFilter);
            for (LogStorage.BlockLogEntry entry : blockLogs) {
                if ("broke".equals(entry.action)) {
                    try {
                        net.minecraft.block.Block block = net.minecraft.registry.Registries.BLOCK.get(new net.minecraft.util.Identifier(entry.blockId));
                        net.minecraft.block.BlockState state = block.getDefaultState();
                        if (entry.nbt != null && !entry.nbt.isEmpty()) {
                            net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(entry.nbt);
                            source.getWorld().setBlockState(entry.pos, state);
                            net.minecraft.block.entity.BlockEntity be = source.getWorld().getBlockEntity(entry.pos);
                            if (be != null) be.readNbt(nbt);
                        } else {
                            source.getWorld().setBlockState(entry.pos, state);
                        }
                        blockRestored++;
                    } catch (Exception e) { e.printStackTrace(); }
                } else if ("place".equals(entry.action)) {
                    source.getWorld().setBlockState(entry.pos, net.minecraft.block.Blocks.AIR.getDefaultState());
                    blockRestored++;
                }
            }
        } else {
            source.sendFeedback(() -> Text.literal("Usage: /flowframe minetracer restore range:<blocks> user:<name> or /flowframe minetracer restore user:<name>"), false);
            return Command.SINGLE_SUCCESS;
        }
        if (restored > 0 || blockRestored > 0) {
            int count = restored + blockRestored;
            source.sendFeedback(() -> Text.literal("Restored " + count + " items/blocks."), false);
        } else {
            source.sendFeedback(() -> Text.literal("No items or blocks to restore."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int restoreToContainer(ServerCommandSource source, BlockPos pos, java.util.List<LogStorage.LogEntry> logs) {
        Object blockEntity = source.getWorld().getBlockEntity(pos);
        if (!(blockEntity instanceof Inventory)) {
            return 0;
        }
        Inventory inv = (Inventory) blockEntity;
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
