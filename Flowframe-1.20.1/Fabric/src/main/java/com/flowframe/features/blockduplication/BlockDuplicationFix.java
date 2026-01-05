package com.flowframe.features.blockduplication;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

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
    private static class PendingCheck {
        String blockId;
        int preCount;
        int ticksLeft;
        PendingCheck(String blockId, int preCount) {
            this.blockId = blockId;
            this.preCount = preCount;
            this.ticksLeft = 5;
        }
    }
    private static final HashMap<UUID, PendingCheck> pendingChecks = new HashMap<>();

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity)) return true;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
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

            Set<Enchantment> enchants = EnchantmentHelper.get(stack).keySet();
            boolean hasBadEnchant = false;
            for (Identifier enchId : ENCHANTMENTS) {
                for (Enchantment e : enchants) {
                    if (Registries.ENCHANTMENT.getId(e).equals(enchId)) {
                        hasBadEnchant = true;
                        break;
                    }
                }
                if (hasBadEnchant) break;
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
            pendingChecks.put(serverPlayer.getUuid(), new PendingCheck(blockId, preCount));
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();
                if (pendingChecks.containsKey(uuid)) {
                    PendingCheck check = pendingChecks.get(uuid);
                    String blockId = check.blockId;
                    int preCount = check.preCount;
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
                    check.ticksLeft--;
                    if (check.ticksLeft <= 0) {
                        pendingChecks.remove(uuid);
                    }
                }
            }
        });
        System.out.println("[FLOWFRAME] Block duplication fix enabled");
    }
}
