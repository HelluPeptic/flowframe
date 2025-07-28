package com.flowframe.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {ShapedRecipe.class, ShapelessRecipe.class})
public class MixinRecipe_MapRemoval {
    @Inject(method = "getOutput", at = @At("RETURN"), cancellable = true)
    private void denyMapCrafting(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack output = cir.getReturnValue();
        if (output.getItem() == Items.MAP || 
            output.getItem() == Items.FILLED_MAP) {
            cir.setReturnValue(ItemStack.EMPTY); // Cancel crafting by returning empty stack
        }
    }
}
