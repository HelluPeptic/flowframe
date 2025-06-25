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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import net.minecraft.item.ItemStack;

// Handles /flowframe minetracer commands (lookup, rollback)
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
                    .then(CommandManager.literal("rollback")
                        .then(CommandManager.argument("arg", StringArgumentType.greedyString())
                            .suggests(MineTracerCommand::suggestPlayers)
                            .executes(MineTracerCommand::rollback)
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
            String[] actions = {"inventory", "deposited", "withdrew", "kill"};
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
    private static class FlatLogEntry {
        public final BlockPos pos;
        public final Object entry;
        public FlatLogEntry(BlockPos pos, Object entry) {
            this.pos = pos;
            this.entry = entry;
        }
    }
    private static class QueryContext {
        public final List<FlatLogEntry> flatList;
        public final int entriesPerPage;
        public QueryContext(List<FlatLogEntry> flatList, int entriesPerPage) {
            this.flatList = flatList;
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
        List<LogStorage.KillLogEntry> killLogs = LogStorage.getKillLogsInRange(playerPos, range, userFilter);
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
            killLogs.removeIf(entry -> entry.timestamp.isBefore(cutoffFinal));
        }
        // Filter out inventory logs unless action:inventory is present
        if (!showInventory) {
            containerLogs.removeIf(entry -> "inventory".equals(entry.action));
        }
        // Filter by action if specified
        if (actionFilter != null) {
            final String actionFilterFinal = actionFilter;
            containerLogs.removeIf(entry -> !entry.action.equalsIgnoreCase(actionFilterFinal));
            blockLogs.removeIf(entry -> !entry.action.equalsIgnoreCase(actionFilterFinal));
            signLogs.removeIf(entry -> !entry.action.equalsIgnoreCase(actionFilterFinal));
            killLogs.removeIf(entry -> !entry.action.equalsIgnoreCase(actionFilterFinal));
        }
        // Flatten all logs into a single list with position
        List<FlatLogEntry> flatList = new ArrayList<>();
        for (LogStorage.BlockLogEntry entry : blockLogs) flatList.add(new FlatLogEntry(entry.pos, entry));
        for (LogStorage.SignLogEntry entry : signLogs) flatList.add(new FlatLogEntry(entry.pos, entry));
        for (LogStorage.LogEntry entry : containerLogs) flatList.add(new FlatLogEntry(entry.pos, entry));
        for (LogStorage.KillLogEntry entry : killLogs) flatList.add(new FlatLogEntry(entry.pos, entry));
        // Sort by timestamp descending
        flatList.sort((a, b) -> {
            Instant ta =
                a.entry instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)a.entry).timestamp :
                a.entry instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)a.entry).timestamp :
                a.entry instanceof LogStorage.KillLogEntry ? ((LogStorage.KillLogEntry)a.entry).timestamp :
                ((LogStorage.LogEntry)a.entry).timestamp;
            Instant tb =
                b.entry instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)b.entry).timestamp :
                b.entry instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)b.entry).timestamp :
                b.entry instanceof LogStorage.KillLogEntry ? ((LogStorage.KillLogEntry)b.entry).timestamp :
                ((LogStorage.LogEntry)b.entry).timestamp;
            return tb.compareTo(ta);
        });
        int entriesPerPage = 5; // Reduced from 10 to 5 for less logs per page
        lastQueries.put(source.getPlayer().getUuid(), new QueryContext(flatList, entriesPerPage));
        // Show first page
        return showLookupPage(source, flatList, 1, entriesPerPage);
    }

    public static int lookupPage(CommandContext<ServerCommandSource> ctx) {
        int page = ctx.getArgument("page", Integer.class);
        ServerCommandSource source = ctx.getSource();
        QueryContext context = lastQueries.get(source.getPlayer().getUuid());
        if (context == null) {
            source.sendFeedback(() -> Text.literal("No previous lookup found. Use /flowframe minetracer lookup [filters] first.").formatted(Formatting.RED), false);
            return Command.SINGLE_SUCCESS;
        }
        return showLookupPage(source, context.flatList, page, context.entriesPerPage);
    }

    private static int showLookupPage(ServerCommandSource source, List<FlatLogEntry> flatList, int page, int entriesPerPage) {
        int totalPages = (int)Math.ceil((double)flatList.size() / entriesPerPage);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        int start = (page - 1) * entriesPerPage;
        int end = Math.min(start + entriesPerPage, flatList.size());
        if (flatList.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No logs found.").formatted(Formatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(() -> Text.literal("----- MineTracer Lookup Results -----").formatted(Formatting.AQUA), false);
        for (int i = start; i < end; i++) {
            FlatLogEntry fle = flatList.get(i);
            BlockPos pos = fle.pos;
            Object entry = fle.entry;
            String header = "(x" + pos.getX() + "/y" + pos.getY() + "/z" + pos.getZ() + ")";
            source.sendFeedback(() -> Text.literal(header).formatted(Formatting.DARK_AQUA), false);
            Text msg;
            String timeAgo = getTimeAgo(
                entry instanceof LogStorage.BlockLogEntry ? ((LogStorage.BlockLogEntry)entry).timestamp :
                entry instanceof LogStorage.SignLogEntry ? ((LogStorage.SignLogEntry)entry).timestamp :
                entry instanceof LogStorage.KillLogEntry ? ((LogStorage.KillLogEntry)entry).timestamp :
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
                // Parse before/after from nbt JSON robustly
                String prevMsg = null;
                String newMsg = null;
                if (se.nbt != null && se.nbt.contains("before") && se.nbt.contains("after")) {
                    try {
                        JsonObject nbtObj = JsonParser.parseString(se.nbt).getAsJsonObject();
                        if (nbtObj.has("before")) {
                            if (nbtObj.get("before").isJsonArray()) {
                                StringBuilder sb = new StringBuilder();
                                for (JsonElement el : nbtObj.getAsJsonArray("before")) {
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append(el.getAsString());
                                }
                                prevMsg = sb.toString();
                            } else {
                                prevMsg = nbtObj.get("before").getAsString();
                            }
                        }
                        if (nbtObj.has("after")) {
                            if (nbtObj.get("after").isJsonArray()) {
                                StringBuilder sb = new StringBuilder();
                                for (JsonElement el : nbtObj.getAsJsonArray("after")) {
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append(el.getAsString());
                                }
                                newMsg = sb.toString();
                            } else {
                                newMsg = nbtObj.get("after").getAsString();
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (prevMsg == null) prevMsg = "(unknown)";
                if (newMsg == null) newMsg = "(unknown)";
                // Group all changed lines under a single [before] and [after] block
                String[] beforeLines = prevMsg.split("\n", -1);
                String[] afterLines = newMsg.split("\n", -1);
                List<String> changedBefore = new ArrayList<>();
                List<String> changedAfter = new ArrayList<>();
                for (int j = 0; j < Math.max(beforeLines.length, afterLines.length); j++) {
                    String beforeLine = j < beforeLines.length ? beforeLines[j] : "";
                    String afterLine = j < afterLines.length ? afterLines[j] : "";
                    if (!beforeLine.equals(afterLine)) {
                        changedBefore.add(beforeLine);
                        changedAfter.add(afterLine);
                    } else if (j >= afterLines.length && j < beforeLines.length) {
                        // Line was removed, show it in before, blank in after
                        changedBefore.add(beforeLine);
                        changedAfter.add("");
                    }
                }
                StringBuilder diffBuilder = new StringBuilder();
                if (!changedBefore.isEmpty()) {
                    diffBuilder.append("§4[before]\n"); // yellow
                    for (String line : changedBefore) diffBuilder.append(line.isEmpty() ? "\n" : line + "\n");
                    diffBuilder.append("§a[after]\n"); // green
                    for (String line : changedAfter) diffBuilder.append(line.isEmpty() ? "\n" : line + "\n");
                } else {
                    diffBuilder.append("(no visible change)");
                }
                String diffMsg = diffBuilder.toString().trim();
                msg = Text.literal(timeAgo + " ago - ")
                    .append(Text.literal(se.playerName).formatted(Formatting.AQUA))
                    .append(Text.literal(" edited sign:").formatted(Formatting.YELLOW))
                    .append(Text.literal("\n" + diffMsg));
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
            } else if (entry instanceof LogStorage.KillLogEntry ke) {
                Formatting color = ke.victimName.startsWith("Player") ? Formatting.RED : Formatting.YELLOW;
                msg = Text.literal(timeAgo + " ago - ")
                    .append(Text.literal(ke.killerName).formatted(Formatting.AQUA))
                    .append(Text.literal(" killed ").formatted(Formatting.RED))
                    .append(Text.literal(ke.victimName).formatted(color))
                    .append(Text.literal(" in world " + ke.world).formatted(Formatting.GRAY));
            } else {
                msg = Text.literal("[?] Unknown log entry");
            }
            source.sendFeedback(() -> msg, false);
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

    private static int rollback(CommandContext<ServerCommandSource> ctx) {
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
                restored += rollbackToContainer(source, e.getKey(), e.getValue());
            }
            // --- Block rollback logic ---
            java.util.List<LogStorage.BlockLogEntry> blockLogs = LogStorage.getBlockLogsInRange(playerPos, range, userFilter);
            for (LogStorage.BlockLogEntry entry : blockLogs) {
                if ("broke".equals(entry.action)) {
                    // Restore block that was broken
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
                restored += rollbackToContainer(source, e.getKey(), e.getValue());
            }
            // --- Block rollback logic for user ---
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
            source.sendFeedback(() -> Text.literal("Usage: /flowframe minetracer rollback range:<blocks> user:<name> or /flowframe minetracer rollback user:<name>"), false);
            return Command.SINGLE_SUCCESS;
        }
        if (restored > 0 || blockRestored > 0) {
            int count = restored + blockRestored;
            source.sendFeedback(() -> Text.literal("Rolled back " + count + " items/blocks."), false);
        } else {
            source.sendFeedback(() -> Text.literal("No items or blocks to rollback."), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int rollbackToContainer(ServerCommandSource source, BlockPos pos, java.util.List<LogStorage.LogEntry> logs) {
        Object blockEntity = source.getWorld().getBlockEntity(pos);
        if (!(blockEntity instanceof Inventory)) {
            System.out.println("[MineTracer] Block entity at " + pos + " is not an Inventory: " + (blockEntity == null ? "null" : blockEntity.getClass().getName()));
            return 0;
        }
        Inventory inv = (Inventory) blockEntity;
        System.out.println("[MineTracer] Rollback at " + pos + ", inventory size: " + inv.size());
        printInventory(inv, "Before rollback");
        int restored = 0;
        for (LogStorage.LogEntry entry : logs) {
            if ("remove".equals(entry.action) || "withdrew".equals(entry.action)) {
                System.out.println("[MineTracer] Restoring log entry: action=" + entry.action + ", player=" + entry.playerName + ", item=" + entry.stack + ", count=" + entry.stack.getCount());
                int toRestore = entry.stack.getCount();
                // First, try to merge with existing stacks
                for (int i = 0; i < inv.size() && toRestore > 0; i++) {
                    ItemStack slotStack = inv.getStack(i);
                    if (!slotStack.isEmpty() &&
                        ItemStack.areItemsEqual(slotStack, entry.stack) &&
                        java.util.Objects.equals(slotStack.getNbt(), entry.stack.getNbt())) {
                        int max = Math.min(slotStack.getMaxCount(), inv.getMaxCountPerStack());
                        int canAdd = max - slotStack.getCount();
                        if (canAdd > 0) {
                            int add = Math.min(canAdd, toRestore);
                            slotStack.increment(add);
                            toRestore -= add;
                            System.out.println("[MineTracer] Merged " + add + " into slot " + i + ", now " + slotStack.getCount());
                        }
                    }
                }
                // Then, fill empty slots
                for (int i = 0; i < inv.size() && toRestore > 0; i++) {
                    if (inv.getStack(i).isEmpty()) {
                        ItemStack newStack = entry.stack.copy();
                        newStack.setCount(toRestore);
                        inv.setStack(i, newStack);
                        restored++;
                        System.out.println("[MineTracer] Placed " + toRestore + " in empty slot " + i);
                        toRestore = 0;
                        break;
                    }
                }
                if (toRestore > 0) {
                    System.out.println("[MineTracer] Could not restore all items for entry: " + entry.stack + ", remaining: " + toRestore);
                }
            }
        }
        printInventory(inv, "After rollback");
        // Mark block entity dirty and sync if possible
        if (blockEntity instanceof net.minecraft.block.entity.BlockEntity) {
            ((net.minecraft.block.entity.BlockEntity) blockEntity).markDirty();
            System.out.println("[MineTracer] Marked block entity dirty at " + pos);
            // Try to sync to client if possible
            if (blockEntity instanceof net.minecraft.block.entity.LootableContainerBlockEntity) {
                ((net.minecraft.block.entity.LootableContainerBlockEntity) blockEntity).markDirty();
                source.getWorld().updateListeners(pos, source.getWorld().getBlockState(pos), source.getWorld().getBlockState(pos), 3);
                System.out.println("[MineTracer] Synced block entity at " + pos);
            }
        }
        return restored;
    }

    private static void printInventory(Inventory inv, String label) {
        System.out.println("[MineTracer] " + label + ":");
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                System.out.println("  Slot " + i + ": " + stack + " x" + stack.getCount());
            }
        }
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
