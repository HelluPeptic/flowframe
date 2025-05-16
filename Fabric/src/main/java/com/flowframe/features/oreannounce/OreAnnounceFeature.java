package com.flowframe.features.oreannounce;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class OreAnnounceFeature {
    private static final HashMap<String, HashMap<String, Integer>> playerLastFoundTicks = new HashMap<>();

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (world.isClient) return;
            onBlockBreak((ServerWorld) world, (ServerPlayerEntity) player, pos, state);
        });

        // The following lambda parameter types may be incorrect for your version of Collective. If you get a build error here, try removing or commenting out this event registration, or check the documentation for the correct parameter types for BLOCK_PLACE in com.natamus.collective.fabric.callbacks.CollectiveBlockEvents.
        // CollectiveBlockEvents.BLOCK_PLACE.register((World world, BlockPos blockPos, BlockState blockState,
        //        LivingEntity livingEntity, ItemStack itemStack) -> {
        //    // No debug output, no logic needed for now
        //    return false;
        //});
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
