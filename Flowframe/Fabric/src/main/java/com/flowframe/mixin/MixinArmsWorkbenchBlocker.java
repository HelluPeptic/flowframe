package com.flowframe.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import me.lucko.fabric.api.permissions.v0.Permissions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class MixinArmsWorkbenchBlocker {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void preventArmsWorkbenchPlacement(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        // Debug logging
        ItemStack stack = context.getStack();
        Identifier id = Registries.ITEM.getId(stack.getItem());
        System.out.println("[FLOWFRAME] Mixin called for item: " + (id != null ? id.toString() : "null"));
        
        // Check if this is an Arms Dealers Workbench item
        if (isArmsWorkbenchItem(stack)) {
            System.out.println("[FLOWFRAME] Found Arms Dealers Workbench item: " + id.toString());
            // Check if player has permission
            if (context.getPlayer() instanceof ServerPlayerEntity serverPlayer) {
                if (!hasLuckPermsPermission(serverPlayer, "flowframe.feature.armsworkbench")) {
                    System.out.println("[FLOWFRAME] Blocking placement for player: " + serverPlayer.getName().getString());
                    serverPlayer.sendMessage(Text.literal("The Arms Dealers Workbench is disabled on this server!").formatted(Formatting.RED), false);
                    cir.setReturnValue(ActionResult.FAIL);
                    return;
                } else {
                    System.out.println("[FLOWFRAME] Player has permission, allowing placement");
                }
            }
        }
    }

    private static boolean isArmsWorkbenchItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id == null) return false;
        
        String name = id.toString();
        return name.equals("pointblank:workstation") || 
               name.equals("pointblank:arms_dealer_workbench") || 
               name.equals("vics_point_blank:arms_dealer_workbench") ||
               name.equals("vicspointblank:arms_dealer_workbench") ||
               name.equals("pointblank:arms_dealers_workbench") ||
               name.equals("vics_point_blank:arms_dealers_workbench") ||
               name.equals("vicspointblank:arms_dealers_workbench") ||
               name.contains("arms_dealer_workbench") ||
               name.contains("arms_dealers_workbench") ||
               name.contains("workstation");
    }

    private static boolean hasLuckPermsPermission(ServerPlayerEntity player, String permission) {
        return Permissions.check(player.getCommandSource(), permission) || player.hasPermissionLevel(2);
    }
}
