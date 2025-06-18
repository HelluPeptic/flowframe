package com.flowframe.features.obtaincard;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import me.lucko.fabric.api.permissions.v0.Permissions;

public class ObtainCardFeature {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.server.command.CommandManager.literal("obtain")
                .requires(source -> hasLuckPermsPermission(source, "flowframe.command.obtain"))
                .then(net.minecraft.server.command.CommandManager.literal("removal_card")
                    .executes(context -> obtainCard(context.getSource().getPlayer(), "removal_card", 1)))
                .then(net.minecraft.server.command.CommandManager.literal("gambling_card")
                    .executes(context -> obtainCard(context.getSource().getPlayer(), "gambling_card", 3)))
                .then(net.minecraft.server.command.CommandManager.literal("reverse_card")
                    .executes(context -> obtainCard(context.getSource().getPlayer(), "reverse_card", 10))));
        });
    }

    private static int obtainCard(ServerPlayerEntity player, String cardType, int cost) {
        if (player == null) return 0;
        int found = 0;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.getItem() == Items.NETHERITE_INGOT) {
                found += stack.getCount();
            }
        }
        if (found < cost) {
            player.sendMessage(Text.literal("Not enough Netherite Ingots! Required: " + cost).formatted(Formatting.RED), false);
            return 0;
        }
        int toRemove = cost;
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (stack.getItem() == Items.NETHERITE_INGOT) {
                int remove = Math.min(stack.getCount(), toRemove);
                stack.decrement(remove);
                toRemove -= remove;
                if (toRemove <= 0) break;
            }
        }
        // Use Majrusz's Accessories card item
        Identifier cardId = Identifier.of("majruszsaccessories", cardType);
        ItemStack card = new ItemStack(Registries.ITEM.get(cardId));
        player.getInventory().insertStack(card);
        player.sendMessage(Text.literal("You obtained a " + cardType.replace('_', ' ') + "!").formatted(Formatting.GREEN), false);
        return 1;
    }

    // LuckPerms/Fabric Permissions API check helper
    private static boolean hasLuckPermsPermission(ServerCommandSource source, String permission) {
        return me.lucko.fabric.api.permissions.v0.Permissions.check(source, permission) || source.hasPermissionLevel(2);
    }
}
