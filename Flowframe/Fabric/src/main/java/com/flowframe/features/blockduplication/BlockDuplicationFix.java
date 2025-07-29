package com.flowframe.features.blockduplication;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.After;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import java.util.List;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import java.util.HashMap;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class BlockDuplicationFix {
    private static final Identifier[] TOOLS = {
        new Identifier("betternether", "flaming_ruby_axe"),
        new Identifier("betternether", "flaming_ruby_pickaxe"),
        new Identifier("betternether", "flaming_ruby_shovel")
    };
    private static final Identifier[] ENCHANTMENTS = {
        new Identifier("majruszsenchantments", "telekinesis"),
        new Identifier("vanillatweaks", "siphon")
    };

    private static final HashMap<UUID, String> pendingBlockType = new HashMap<>();
    private static final HashMap<UUID, Integer> pendingPreCount = new HashMap<>();

    public static void register() {
        // Remove BEFORE handler, use AFTER to filter drops
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;
            if (serverPlayer.interactionManager.getGameMode() != net.minecraft.world.GameMode.SURVIVAL) return true;
            ItemStack stack = serverPlayer.getMainHandStack();
            Identifier toolId = Registries.ITEM.getId(stack.getItem());

            boolean isBadTool = false;
            for (Identifier id : TOOLS) {
                if (id.equals(toolId)) {
                    isBadTool = true;
                    break;
                }
            }
            if (!isBadTool) return true;

            var enchants = EnchantmentHelper.get(stack).keySet();
            boolean hasBadEnchant = false;
            for (Identifier enchId : ENCHANTMENTS) {
                if (enchants.stream().anyMatch(e -> Registries.ENCHANTMENT.getId(e).equals(enchId))) {
                    hasBadEnchant = true;
                    break;
                }
            }
            if (!hasBadEnchant) return true;

            // Save the count of the block in the player's inventory before breaking
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            int preCount = 0;
            for (int i = 0; i < serverPlayer.getInventory().size(); i++) {
                ItemStack invStack = serverPlayer.getInventory().getStack(i);
                if (Registries.ITEM.getId(invStack.getItem()).toString().equals(blockId)) {
                    preCount += invStack.getCount();
                }
            }
            // Store in static maps for access in tick event
            pendingBlockType.put(serverPlayer.getUuid(), blockId);
            pendingPreCount.put(serverPlayer.getUuid(), preCount);
            return true;
        });
        // Remove all but one matching dropped item entity at the position (optional, can keep for safety)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
            if (serverPlayer.interactionManager.getGameMode() != net.minecraft.world.GameMode.SURVIVAL) return;
            ItemStack stack = serverPlayer.getMainHandStack();
            Identifier toolId = Registries.ITEM.getId(stack.getItem());

            boolean isBadTool = false;
            for (Identifier id : TOOLS) {
                if (id.equals(toolId)) {
                    isBadTool = true;
                    break;
                }
            }
            if (!isBadTool) return;

            var enchants = EnchantmentHelper.get(stack).keySet();
            boolean hasBadEnchant = false;
            for (Identifier enchId : ENCHANTMENTS) {
                if (enchants.stream().anyMatch(e -> Registries.ENCHANTMENT.getId(e).equals(enchId))) {
                    hasBadEnchant = true;
                    break;
                }
            }
            if (!hasBadEnchant) return;

            // Remove all but one matching dropped item entity at the position
            List<net.minecraft.entity.ItemEntity> drops = ((ServerWorld)world).getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(1.5),
                itemEntity -> true
            );
            java.util.Map<String, java.util.List<net.minecraft.entity.ItemEntity>> grouped = new java.util.HashMap<>();
            for (var item : drops) {
                String key = item.getStack().getItem().toString();
                grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(item);
            }
            for (var entry : grouped.values()) {
                if (entry.size() > 1) {
                    for (int i = 1; i < entry.size(); i++) {
                        entry.get(i).discard();
                    }
                }
            }
            // Clean up map entry
            pendingBlockType.remove(serverPlayer.getUuid());
            pendingPreCount.remove(serverPlayer.getUuid());
        });
        // Tick event to clean up inventory after all mod logic
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                if (pendingBlockType.containsKey(uuid) && pendingPreCount.containsKey(uuid)) {
                    String blockId = pendingBlockType.get(uuid);
                    int preCount = pendingPreCount.get(uuid);
                    int postCount = 0;
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        ItemStack invStack = player.getInventory().getStack(i);
                        if (Registries.ITEM.getId(invStack.getItem()).toString().equals(blockId)) {
                            postCount += invStack.getCount();
                        }
                    }
                    int excess = postCount - preCount - 1;
                    if (excess > 0) {
                        // Remove excess
                        for (int i = 0; i < player.getInventory().size() && excess > 0; i++) {
                            ItemStack invStack = player.getInventory().getStack(i);
                            if (Registries.ITEM.getId(invStack.getItem()).toString().equals(blockId)) {
                                int remove = Math.min(invStack.getCount(), excess);
                                invStack.decrement(remove);
                                excess -= remove;
                            }
                        }
                    }
                    // Clean up
                    pendingBlockType.remove(uuid);
                    pendingPreCount.remove(uuid);
                }
            }
        });
        System.out.println("[FLOWFRAME] Block duplication fix enabled");
    }
}
