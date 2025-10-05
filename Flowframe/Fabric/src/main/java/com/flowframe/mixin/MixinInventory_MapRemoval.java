package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class MixinInventory_MapRemoval {
    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void preventBlockedItemInsertion(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        FlowframeConfig config = FlowframeConfig.getInstance();
        
        // Check for maps (only if map removal is enabled)
        if (config.isMapRemovalEnabled() && (stack.getItem() == Items.MAP || stack.getItem() == Items.FILLED_MAP)) {
            stack.setCount(0); // Destroy the item
            cir.setReturnValue(true); // Pretend we accepted it
            return;
        }
        
        // Check for trash bag (only if trash bag removal is enabled)
        if (config.isTrashBagRemovalEnabled()) {
            Identifier itemId = Registries.ITEM.getId(stack.getItem());
            if (itemId.toString().equals("furniture:trash_bag")) {
                stack.setCount(0); // Destroy the item
                cir.setReturnValue(true); // Pretend we accepted it
            }
        }
    }
}
