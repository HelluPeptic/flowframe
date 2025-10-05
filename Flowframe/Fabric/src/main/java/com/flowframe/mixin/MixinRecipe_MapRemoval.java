package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ShapedRecipe.class, ShapelessRecipe.class})
public class MixinRecipe_MapRemoval {
    @Inject(method = "getOutput", at = @At("RETURN"), cancellable = true)
    private void denyBlockedItemCrafting(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack output = cir.getReturnValue();
        FlowframeConfig config = FlowframeConfig.getInstance();
        
        // Check for maps (only if map removal is enabled)
        if (config.isMapRemovalEnabled() && (output.getItem() == Items.MAP || output.getItem() == Items.FILLED_MAP)) {
            cir.setReturnValue(ItemStack.EMPTY); // Cancel crafting by returning empty stack
            return;
        }
        
        // Check for trash bag (only if trash bag removal is enabled)
        if (config.isTrashBagRemovalEnabled()) {
            Identifier itemId = Registries.ITEM.getId(output.getItem());
            if (itemId.toString().equals("furniture:trash_bag")) {
                cir.setReturnValue(ItemStack.EMPTY); // Cancel crafting by returning empty stack
            }
        }
    }
}
