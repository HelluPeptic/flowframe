package com.flowframe.features.mapremoval;

import com.flowframe.config.FlowframeConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.FilledMapItem;
import net.minecraft.util.TypedActionResult;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

public class MapRemovalFeature {
    private static boolean isMapItem(ItemStack stack) {
        return stack.isOf(Items.MAP) || 
               stack.isOf(Items.FILLED_MAP) || 
               stack.getItem() instanceof FilledMapItem;
    }
    
    private static boolean isTrashBagItem(ItemStack stack) {
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId.toString().equals("furniture:trash_bag");
    }
    
    private static boolean isBlockedItem(ItemStack stack) {
        FlowframeConfig config = FlowframeConfig.getInstance();
        
        if (config.isMapRemovalEnabled() && isMapItem(stack)) {
            return true;
        }
        
        if (config.isTrashBagRemovalEnabled() && isTrashBagItem(stack)) {
            return true;
        }
        
        return false;
    }
    
    public static void register() {
        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("flowframe")
                .then(CommandManager.literal("mapremoval")
                    .then(CommandManager.literal("enable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setMapRemovalEnabled(true);
                            context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Map removal enabled"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("disable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setMapRemovalEnabled(false);
                            context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Map removal disabled"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("status")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            boolean enabled = FlowframeConfig.getInstance().isMapRemovalEnabled();
                            String status = enabled ? "§aenabled" : "§cdisabled";
                            context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Map removal is " + status), false);
                            return 1;
                        })))
                .then(CommandManager.literal("trashbagremoval")
                    .then(CommandManager.literal("enable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setTrashBagRemovalEnabled(true);
                            context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Trash bag removal enabled"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("disable")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            FlowframeConfig.getInstance().setTrashBagRemovalEnabled(false);
                            context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Trash bag removal disabled"), true);
                            return 1;
                        }))
                    .then(CommandManager.literal("status")
                        .requires(source -> source.hasPermissionLevel(3))
                        .executes(context -> {
                            boolean enabled = FlowframeConfig.getInstance().isTrashBagRemovalEnabled();
                            String status = enabled ? "§aenabled" : "§cdisabled";
                            context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Trash bag removal is " + status), false);
                            return 1;
                        })))
                .then(CommandManager.literal("itemstatus")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> {
                        FlowframeConfig config = FlowframeConfig.getInstance();
                        String mapStatus = config.isMapRemovalEnabled() ? "§aenabled" : "§cdisabled";
                        String trashStatus = config.isTrashBagRemovalEnabled() ? "§aenabled" : "§cdisabled";
                        context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Item removal status:"), false);
                        context.getSource().sendFeedback(() -> Text.literal("§e  Maps: " + mapStatus), false);
                        context.getSource().sendFeedback(() -> Text.literal("§e  Trash bags: " + trashStatus), false);
                        return 1;
                    })));
        });
        
        // Remove blocked items when used (only if individually enabled)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (isBlockedItem(stack)) {
                stack.setCount(0);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // Check and remove blocked items from player inventory every tick (only if individually enabled)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FlowframeConfig config = FlowframeConfig.getInstance();
            
            // Skip if both features are disabled
            if (!config.isMapRemovalEnabled() && !config.isTrashBagRemovalEnabled()) {
                return;
            }
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // Check main inventory
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (isBlockedItem(stack)) {
                        player.getInventory().removeStack(i);
                    }
                }
                
                // Check offhand
                ItemStack offhandStack = player.getOffHandStack();
                if (isBlockedItem(offhandStack)) {
                    player.getInventory().offHand.set(0, ItemStack.EMPTY);
                }
            }
        });

        System.out.println("[FLOWFRAME] Item removal feature initialized (both maps and trash bags disabled by default)");
    }
}
