package com.flowframe.mixin;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.flowframe.features.minetracer.LogStorage;

@Mixin(ScreenHandler.class)
public abstract class MixinScreenHandler {
    // Store the previous stack for slot click detection
    private ItemStack flowframe$prevStack = null;

    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void flowframe$logSlotClickHead(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (slotIndex < 0) return;
        Slot slot = ((ScreenHandler)(Object)this).getSlot(slotIndex);
        if (slot == null || slot.inventory == null) return;
        // Only track container actions, not player inventory actions
        if (slot.inventory != player.getInventory()) {
            flowframe$prevStack = slot.getStack().copy();
        } else {
            flowframe$prevStack = null;
        }
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void flowframe$logSlotClickReturn(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (slotIndex < 0) return;
        Slot slot = ((ScreenHandler)(Object)this).getSlot(slotIndex);
        if (slot == null || slot.inventory == null) return;
        BlockPos pos = player.getBlockPos(); // Approximate, not always the container's pos
        if (slot.inventory != player.getInventory() && flowframe$prevStack != null) {
            ItemStack after = slot.getStack();
            int prevCount = flowframe$prevStack.getCount();
            int afterCount = after.getCount();
            // Withdrew: stack count decreased
            if (!flowframe$prevStack.isEmpty() && afterCount < prevCount) {
                ItemStack diff = flowframe$prevStack.copy();
                diff.setCount(prevCount - afterCount);
                LogStorage.logContainerAction("withdrew", player, pos, diff);
            }
            // Deposited: stack count increased
            if (!after.isEmpty() && afterCount > prevCount) {
                ItemStack diff = after.copy();
                diff.setCount(afterCount - prevCount);
                LogStorage.logContainerAction("deposited", player, pos, diff);
            }
        } else if (slot.inventory == player.getInventory() && flowframe$prevStack != null) {
            ItemStack after = slot.getStack();
            int prevCount = flowframe$prevStack.getCount();
            int afterCount = after.getCount();
            // Dropped: stack count decreased in player inventory
            if (!flowframe$prevStack.isEmpty() && afterCount < prevCount) {
                ItemStack diff = flowframe$prevStack.copy();
                diff.setCount(prevCount - afterCount);
                LogStorage.logInventoryAction("dropped", player, diff);
            }
            // Picked up: stack count increased in player inventory
            if (!after.isEmpty() && afterCount > prevCount) {
                ItemStack diff = after.copy();
                diff.setCount(afterCount - prevCount);
                LogStorage.logInventoryAction("picked up", player, diff);
            }
        }
    }
}
