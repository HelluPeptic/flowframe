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

public class MapRemovalFeature {
    private static boolean isMapItem(ItemStack stack) {
        return stack.isOf(Items.MAP) || 
               stack.isOf(Items.FILLED_MAP) || 
               stack.getItem() instanceof FilledMapItem;
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
                        }))));
        });
        
        // Remove maps when used (only if enabled)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!FlowframeConfig.getInstance().isMapRemovalEnabled()) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }
            
            ItemStack stack = player.getStackInHand(hand);
            if (isMapItem(stack)) {
                stack.setCount(0);
                return TypedActionResult.fail(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // Check and remove maps from player inventory every tick (only if enabled)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!FlowframeConfig.getInstance().isMapRemovalEnabled()) {
                return;
            }
            
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // Check main inventory
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    if (isMapItem(stack)) {
                        player.getInventory().removeStack(i);
                    }
                }
                
                // Check offhand
                ItemStack offhandStack = player.getOffHandStack();
                if (isMapItem(offhandStack)) {
                    player.getInventory().offHand.set(0, ItemStack.EMPTY);
                }
            }
        });

        System.out.println("[FLOWFRAME] Map removal feature initialized (disabled by default)");
    }
}
