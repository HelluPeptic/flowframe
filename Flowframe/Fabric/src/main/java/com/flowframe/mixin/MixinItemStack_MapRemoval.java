package com.flowframe.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class MixinItemStack_MapRemoval {
    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void makeMapEmpty(CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack)(Object)this;
        if (self.getItem() == Items.MAP || self.getItem() == Items.FILLED_MAP) {
            cir.setReturnValue(true); // Make maps appear empty
        }
    }
}
