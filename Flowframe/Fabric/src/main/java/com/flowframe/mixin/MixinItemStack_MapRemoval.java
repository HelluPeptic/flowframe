package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class MixinItemStack_MapRemoval {
    @Inject(method = "isEmpty", at = @At("HEAD"), cancellable = true)
    private void makeBlockedItemsEmpty(CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack)(Object)this;
        FlowframeConfig config = FlowframeConfig.getInstance();
        
        // Check for maps (only if map removal is enabled)
        if (config.isMapRemovalEnabled() && (self.getItem() == Items.MAP || self.getItem() == Items.FILLED_MAP)) {
            cir.setReturnValue(true); // Make maps appear empty
            return;
        }
        
        // Check for trash bag (only if trash bag removal is enabled)
        if (config.isTrashBagRemovalEnabled()) {
            Identifier itemId = Registries.ITEM.getId(self.getItem());
            if (itemId.toString().equals("furniture:trash_bag")) {
                cir.setReturnValue(true); // Make trash bags appear empty
            }
        }
    }
}
