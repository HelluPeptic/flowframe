package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public class MixinScreenHandler_MapRemoval {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        ScreenHandler self = (ScreenHandler) (Object) this;
        if (slotIndex >= 0 && slotIndex < self.slots.size()) {
            ItemStack stack = self.slots.get(slotIndex).getStack();
            FlowframeConfig config = FlowframeConfig.getInstance();
            
            // Check for maps (only if map removal is enabled)
            if (config.isMapRemovalEnabled() && (stack.isOf(net.minecraft.item.Items.MAP) || stack.isOf(net.minecraft.item.Items.FILLED_MAP))) {
                ci.cancel();
                self.slots.get(slotIndex).setStack(ItemStack.EMPTY);
                return;
            }
            
            // Check for trash bag (only if trash bag removal is enabled)
            if (config.isTrashBagRemovalEnabled()) {
                Identifier itemId = Registries.ITEM.getId(stack.getItem());
                if (itemId.toString().equals("furniture:trash_bag")) {
                    ci.cancel();
                    self.slots.get(slotIndex).setStack(ItemStack.EMPTY);
                }
            }
        }
    }
}
