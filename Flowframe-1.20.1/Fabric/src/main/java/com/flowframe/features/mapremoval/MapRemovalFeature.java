package com.flowframe.features.mapremoval;

import com.flowframe.config.FlowframeConfig;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

public class MapRemovalFeature {

   private static boolean isMapItem(ItemStack stack) {
      return stack.isOf(Items.FILLED_MAP) || stack.isOf(Items.MAP) || stack.getItem() instanceof FilledMapItem;
   }

   private static boolean isTrashBagItem(ItemStack stack) {
      Identifier itemId = Registries.ITEM.getId(stack.getItem());
      return itemId.toString().equals("furniture:trash_bag");
   }

   private static boolean isBanglumNukeCoreItem(ItemStack stack) {
      Identifier itemId = Registries.ITEM.getId(stack.getItem());
      return itemId.toString().equals("mythicmetals:banglum_nuke_core");
   }

   private static void handleBanglumNukeCoreRemoval(ServerPlayerEntity player, ItemStack stack) {
      player.sendMessage(Text.literal("§c[FLOWFRAME] Banglum Nuke Core has been disabled on the FlowSMP!"), false);
      ItemStack rawBanglumBlocks = createItemStack("mythicmetals:raw_banglum_block", 4);
      ItemStack morkiteBlocks = createItemStack("mythicmetals:morkite_block", 4);
      ItemStack banglumChunk = createItemStack("mythicmetals:banglum_chunk", 1);
      
      if (!rawBanglumBlocks.isEmpty()) {
         player.giveItemStack(rawBanglumBlocks);
      }
      if (!morkiteBlocks.isEmpty()) {
         player.giveItemStack(morkiteBlocks);
      }
      if (!banglumChunk.isEmpty()) {
         player.giveItemStack(banglumChunk);
      }
   }

   private static ItemStack createItemStack(String itemId, int count) {
      try {
         Identifier id = new Identifier(itemId);
         net.minecraft.item.Item item = Registries.ITEM.get(id);
         if (item != Items.AIR) {
            return new ItemStack(item, count);
         }
      } catch (Exception e) {
         System.out.println("[FLOWFRAME] Failed to create item: " + itemId + " - " + e.getMessage());
      }

      return ItemStack.EMPTY;
   }

   private static boolean isBlockedItem(ItemStack stack) {
      FlowframeConfig config = FlowframeConfig.getInstance();
      if (config.isMapRemovalEnabled() && isMapItem(stack)) {
         return true;
      } else if (config.isTrashBagRemovalEnabled() && isTrashBagItem(stack)) {
         return true;
      } else {
         return config.isBanglumNukeCoreRemovalEnabled() && isBanglumNukeCoreItem(stack);
      }
   }

   public static void register() {
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
         dispatcher.register(CommandManager.literal("flowframe")
            .then(CommandManager.literal("mapremoval")
               .then(CommandManager.literal("enable")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     FlowframeConfig.getInstance().setMapRemovalEnabled(true);
                     context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Map removal enabled"), true);
                     return 1;
                  })
               )
               .then(CommandManager.literal("disable")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     FlowframeConfig.getInstance().setMapRemovalEnabled(false);
                     context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Map removal disabled"), true);
                     return 1;
                  })
               )
               .then(CommandManager.literal("status")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     boolean enabled = FlowframeConfig.getInstance().isMapRemovalEnabled();
                     String status = enabled ? "§aenabled" : "§cdisabled";
                     context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Map removal is " + status), false);
                     return 1;
                  })
               )
            )
            .then(CommandManager.literal("trashbagremoval")
               .then(CommandManager.literal("enable")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     FlowframeConfig.getInstance().setTrashBagRemovalEnabled(true);
                     context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Trash bag removal enabled"), true);
                     return 1;
                  })
               )
               .then(CommandManager.literal("disable")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     FlowframeConfig.getInstance().setTrashBagRemovalEnabled(false);
                     context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Trash bag removal disabled"), true);
                     return 1;
                  })
               )
               .then(CommandManager.literal("status")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     boolean enabled = FlowframeConfig.getInstance().isTrashBagRemovalEnabled();
                     String status = enabled ? "§aenabled" : "§cdisabled";
                     context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Trash bag removal is " + status), false);
                     return 1;
                  })
               )
            )
            .then(CommandManager.literal("banglumnukecoreremoval")
               .then(CommandManager.literal("enable")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     FlowframeConfig.getInstance().setBanglumNukeCoreRemovalEnabled(true);
                     context.getSource().sendFeedback(() -> Text.literal("§a[FLOWFRAME] Banglum Nuke Core removal enabled"), true);
                     return 1;
                  })
               )
               .then(CommandManager.literal("disable")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     FlowframeConfig.getInstance().setBanglumNukeCoreRemovalEnabled(false);
                     context.getSource().sendFeedback(() -> Text.literal("§c[FLOWFRAME] Banglum Nuke Core removal disabled"), true);
                     return 1;
                  })
               )
               .then(CommandManager.literal("status")
                  .requires(source -> source.hasPermissionLevel(3))
                  .executes(context -> {
                     boolean enabled = FlowframeConfig.getInstance().isBanglumNukeCoreRemovalEnabled();
                     String status = enabled ? "§aenabled" : "§cdisabled";
                     context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Banglum Nuke Core removal is " + status), false);
                     return 1;
                  })
               )
            )
            .then(CommandManager.literal("itemstatus")
               .requires(source -> source.hasPermissionLevel(3))
               .executes(context -> {
                  FlowframeConfig config = FlowframeConfig.getInstance();
                  String mapStatus = config.isMapRemovalEnabled() ? "§aenabled" : "§cdisabled";
                  String trashStatus = config.isTrashBagRemovalEnabled() ? "§aenabled" : "§cdisabled";
                  String banglumStatus = config.isBanglumNukeCoreRemovalEnabled() ? "§aenabled" : "§cdisabled";
                  context.getSource().sendFeedback(() -> Text.literal("§e[FLOWFRAME] Item removal status:"), false);
                  context.getSource().sendFeedback(() -> Text.literal("§e  Maps: " + mapStatus), false);
                  context.getSource().sendFeedback(() -> Text.literal("§e  Trash bags: " + trashStatus), false);
                  context.getSource().sendFeedback(() -> Text.literal("§e  Banglum Nuke Core: " + banglumStatus), false);
                  return 1;
               })
            )
         );
      });
      UseItemCallback.EVENT.register((player, world, hand) -> {
         ItemStack stack = player.getStackInHand(hand);
         if (isBlockedItem(stack)) {
            if (FlowframeConfig.getInstance().isBanglumNukeCoreRemovalEnabled() && isBanglumNukeCoreItem(stack)) {
               handleBanglumNukeCoreRemoval((ServerPlayerEntity)player, stack);
            }
            stack.setCount(0);
            return TypedActionResult.success(stack);
         } else {
            return TypedActionResult.pass(stack);
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((server) -> {
         FlowframeConfig config = FlowframeConfig.getInstance();
         if (config.isMapRemovalEnabled() || config.isTrashBagRemovalEnabled() || config.isBanglumNukeCoreRemovalEnabled()) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               // Check main inventory
               for (int i = 0; i < player.getInventory().size(); ++i) {
                  ItemStack stack = player.getInventory().getStack(i);
                  if (isBlockedItem(stack)) {
                     if (config.isBanglumNukeCoreRemovalEnabled() && isBanglumNukeCoreItem(stack)) {
                        handleBanglumNukeCoreRemoval(player, stack);
                     }
                     player.getInventory().removeStack(i);
                  }
               }

               // Check offhand
               ItemStack offhandStack = player.getOffHandStack();
               if (isBlockedItem(offhandStack)) {
                  if (config.isBanglumNukeCoreRemovalEnabled() && isBanglumNukeCoreItem(offhandStack)) {
                     handleBanglumNukeCoreRemoval(player, offhandStack);
                  }
                  player.getInventory().offHand.set(0, ItemStack.EMPTY);
               }
            }
         }
      });
      System.out.println("[FLOWFRAME] Item removal feature initialized (maps, trash bags, and banglum nuke core disabled by default)");
   }
}
