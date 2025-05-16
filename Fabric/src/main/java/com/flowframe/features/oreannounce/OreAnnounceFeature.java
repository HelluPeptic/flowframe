package com.flowframe.features.oreannounce;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.io.IOException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.time.Duration;
import net.minecraft.text.ClickEvent;

public class OreAnnounceFeature {
    private static final HashMap<String, HashMap<String, Integer>> playerLastFoundTicks = new HashMap<>();

    private static final String LOG_FILE = "orelog.txt";
    private static final int LOG_DISPLAY_COUNT = 20;

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world.isClient) return;
            onBlockBreak((ServerWorld) world, (ServerPlayerEntity) player, pos, state);
        });

        // Register /orelog command for ops (v2 API, with optional time argument)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("orelog")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("hours", StringArgumentType.word())
                    .executes(ctx -> executeOreLogCommand(ctx, StringArgumentType.getString(ctx, "hours"))))
                .executes(ctx -> executeOreLogCommand(ctx, null))
            );
        });
    }

    private static int executeOreLogCommand(CommandContext<ServerCommandSource> context, String hoursArg) {
        ServerCommandSource source = context.getSource();
        Path logPath = getLogFilePath();
        long now = System.currentTimeMillis();
        long cutoffMillis = 0L;
        if (hoursArg != null) {
            try {
                String h = hoursArg.toLowerCase().replace("h", "");
                double hours = Double.parseDouble(h);
                cutoffMillis = now - (long)(hours * 3600000);
            } catch (Exception e) {
                source.sendError(Text.literal("Invalid argument. Use e.g. /orelog 2h or /orelog 0.5h"));
                return 1;
            }
        }
        try {
            if (!Files.exists(logPath)) {
                source.sendFeedback(() -> Text.literal("No ore log file found."), false);
                return 1;
            }
            List<String> allLines = Files.readAllLines(logPath);
            List<String> filtered = new java.util.ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (String line : allLines) {
                int tsStart = line.indexOf("[") + 1;
                int tsEnd = line.indexOf("]");
                if (tsStart < tsEnd && tsEnd > 0) {
                    String ts = line.substring(tsStart, tsEnd);
                    try {
                        long entryMillis = java.sql.Timestamp.valueOf(ts).getTime();
                        if (hoursArg == null || entryMillis >= cutoffMillis) {
                            filtered.add(line);
                        }
                    } catch (Exception ignore) {}
                }
            }
            if (filtered.isEmpty()) {
                source.sendFeedback(() -> Text.literal("Ore log is empty for the given time window."), false);
            } else {
                source.sendFeedback(() -> Text.literal("Showing " + filtered.size() + " ore log entries:"), false);
                for (String line : filtered) {
                    // Try to extract coordinates for click-to-tp and colorize log
                    String coords = null;
                    int atIdx = line.lastIndexOf(" at ");
                    String time = "", name = "", ore = "", rest = line;
                    // Parse log line: [time] name mined count ore at x,y,z
                    int timeStart = line.indexOf("[") + 1;
                    int timeEnd = line.indexOf("]");
                    if (timeStart > 0 && timeEnd > timeStart) {
                        time = line.substring(timeStart, timeEnd);
                        rest = line.substring(timeEnd + 1).trim();
                    }
                    int minedIdx = rest.indexOf(" mined ");
                    int atPos = rest.lastIndexOf(" at ");
                    if (minedIdx > 0 && atPos > minedIdx) {
                        name = rest.substring(0, minedIdx);
                        String afterMined = rest.substring(minedIdx + 7, atPos).trim();
                        // afterMined: e.g. "2 diamond ore"
                        String[] parts = afterMined.split(" ", 2);
                        String count = parts.length > 0 ? parts[0] : "";
                        ore = parts.length > 1 ? parts[1] : "";
                        // Compose colored message
                        String coordsStr = rest.substring(atPos + 4).trim();
                        MutableText text = Text.literal("")
                            .append(Text.literal("[" + time + "] ").formatted(Formatting.GRAY))
                            .append(Text.literal(name).formatted(Formatting.GOLD))
                            .append(Text.literal(" mined ").formatted(Formatting.YELLOW))
                            .append(Text.literal(count + " ").formatted(Formatting.YELLOW))
                            .append(Text.literal(ore).formatted(Formatting.AQUA))
                            .append(Text.literal(" at ").formatted(Formatting.YELLOW))
                            .append(Text.literal(coordsStr).formatted(Formatting.YELLOW));
                        // Teleport click event
                        String[] xyz = coordsStr.split(",");
                        if (xyz.length == 3) {
                            coords = xyz[0].trim() + " " + xyz[1].trim() + " " + xyz[2].trim();
                            final String coordsFinal = coords;
                            final MutableText finalText = text.styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @s " + coordsFinal))
                                .withColor(Formatting.AQUA)
                            );
                            source.sendFeedback(() -> finalText, false);
                        } else {
                            source.sendFeedback(() -> text, false);
                        }
                        continue;
                    }
                    // Fallback: no color parsing, just clickable if possible
                    if (coords != null) {
                        final String tpCommand = "/tp @s " + coords;
                        final MutableText fallbackText = Text.literal(line)
                            .styled(style -> style
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                                .withColor(Formatting.AQUA)
                            );
                        source.sendFeedback(() -> fallbackText, false);
                    } else {
                        final MutableText fallbackText = Text.literal(line);
                        source.sendFeedback(() -> fallbackText, false);
                    }
                }
            }
        } catch (IOException e) {
            source.sendError(Text.literal("Failed to read ore log: " + e.getMessage()));
        }
        return 1;
    }

    private static Path getLogFilePath() {
        String userDir = System.getProperty("user.dir");
        return Path.of(userDir, LOG_FILE);
    }

    private static void logOreBreak(String playerName, String oreName, BlockPos pos, int count) {
        Path logPath = getLogFilePath();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String entry = String.format("[%s] %s mined %d %s at %s,%s,%s", timestamp, playerName, count, oreName, pos.getX(), pos.getY(), pos.getZ());
        try {
            Files.writeString(logPath, entry + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[OreAnnounce] Failed to write ore log: " + e.getMessage());
        }
    }

    public static void onBlockBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
        // Only run on dedicated servers if needed (optional)
        // if (!world.getServer().isDedicated()) return;

        // Ignore creative players (optional, set to true if you want)
        if (player.isCreative()) return;

        // Only allow pickaxes (optional, always true here)
        // ItemStack handStack = player.getMainHandStack();
        // if (!isPickaxe(handStack)) return;

        Block block = state.getBlock();
        if (!isOre(state, block)) return;
        if (blockBlacklist().contains(block)) return;

        int playerTickCount = (int) player.age; // .age is in ticks
        String playerName = player.getName().getString();
        String oreName = block.getName().getString();

        // Hide "deepslate" from name if you want (optional)
        if (block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            oreName = Blocks.DIAMOND_ORE.getName().getString();
        }

        oreName = oreName.toLowerCase();

        boolean shouldBroadcast = true;
        if (!playerLastFoundTicks.containsKey(playerName)) {
            playerLastFoundTicks.put(playerName, new HashMap<>());
        } else {
            HashMap<String, Integer> lastFound = playerLastFoundTicks.get(playerName);
            if (lastFound.containsKey(oreName)) {
                int lastFoundTicks = lastFound.get(oreName);
                if (playerTickCount - lastFoundTicks <= 40) { // 40 ticks = 2 seconds, adjust as needed
                    shouldBroadcast = false;
                }
            }
        }

        if (shouldBroadcast) {
            int oreCount = countConnectedOres(world, pos, block, new HashSet<>());

            // Build the message with count and correct formatting
            Text message = Text.literal(playerName)
                .formatted(Formatting.GOLD)
                .append(Text.literal(" found ").formatted(Formatting.YELLOW))
                .append(Text.literal(oreCount + " ").formatted(Formatting.YELLOW))
                .append(Text.literal(oreName).formatted(Formatting.AQUA))
                .append(Text.literal("!").formatted(Formatting.YELLOW));

            for (ServerPlayerEntity op : world.getServer().getPlayerManager().getPlayerList()) {
                if (op.hasPermissionLevel(2)) {
                    op.sendMessage(message, false);
                }
            }
            // Log the ore break event
            logOreBreak(playerName, oreName, pos, oreCount);
        }

        playerLastFoundTicks.get(playerName).put(oreName, playerTickCount);
    }

    // Helper: is this block an ore?
    private static boolean isOre(BlockState state, Block block) {
        // Vanilla ores
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE || block == Blocks.ANCIENT_DEBRIS) {
            return true;
        }
        // Mythic Upgrades and Natures Spirit modded ores
        Identifier id = Registries.BLOCK.getId(block);
        if (id == null) return false;
        String name = id.toString();
        return name.equals("mythicupgrades:ametrine_ore")
            || name.equals("mythicupgrades:aquamarine_ore")
            || name.equals("mythicupgrades:deepslate_aquamarine_ore")
            || name.equals("mythicupgrades:deepslate_peridot_ore")
            || name.equals("mythicupgrades:deepslate_topaz_ore")
            || name.equals("mythicupgrades:jade_ore")
            || name.equals("mythicupgrades:necoium_ore")
            || name.equals("mythicupgrades:peridot_ore")
            || name.equals("mythicupgrades:raw_necoium_block")
            || name.equals("mythicupgrades:ruby_ore")
            || name.equals("mythicupgrades:sapphire_ore")
            || name.equals("mythicupgrades:topaz_ore")
            || name.equals("natures_spirit:chert_diamond_ore");
    }

    // Helper: blacklist (empty for now, add blocks if needed)
    private static Set<Block> blockBlacklist() {
        return new HashSet<>();
    }

    // Helper: count connected ores (6-directional)
    private static int countConnectedOres(ServerWorld world, BlockPos pos, Block targetBlock, Set<BlockPos> visited) {
        if (visited.contains(pos)) return 0;
        visited.add(pos);

        // Treat the starting position as ore even if already broken
        boolean isOre = world.getBlockState(pos).getBlock() == targetBlock || visited.size() == 1;
        if (!isOre) return 0;

        int count = 1;
        for (BlockPos offset : new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down()
        }) {
            if (!visited.contains(offset)) {
                count += countConnectedOres(world, offset, targetBlock, visited);
            }
        }
        return count;
    }
}
