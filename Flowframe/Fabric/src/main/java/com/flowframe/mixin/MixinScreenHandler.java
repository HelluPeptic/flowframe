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

import java.util.Objects;

@Mixin(ScreenHandler.class)
public abstract class MixinScreenHandler {
    // Store the previous state of only the container slots (not player inventory)
    private ItemStack[] flowframe$prevContainerState = null;
    private int flowframe$containerSize = 0;

    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void flowframe$logSlotClickHead(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        ScreenHandler self = (ScreenHandler)(Object)this;
        // Only track if interacting with a container (not player inventory)
        if (self != null && self.slots.size() > 0) {
            // Find container slots (non-player inventory slots)
            int containerSlots = 0;
            for (Slot slot : self.slots) {
                if (slot.inventory != player.getInventory()) {
                    containerSlots++;
                }
            }
            
            if (containerSlots > 0) {
                flowframe$containerSize = containerSlots;
                flowframe$prevContainerState = new ItemStack[containerSlots];
                int i = 0;
                for (Slot slot : self.slots) {
                    if (slot.inventory != player.getInventory()) {
                        flowframe$prevContainerState[i++] = slot.getStack().copy();
                    }
                }
            } else {
                flowframe$prevContainerState = null;
                flowframe$containerSize = 0;
            }
        } else {
            flowframe$prevContainerState = null;
            flowframe$containerSize = 0;
        }
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void flowframe$logSlotClickReturn(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (flowframe$prevContainerState == null || flowframe$containerSize == 0) {
            return;
        }
        
        ScreenHandler self = (ScreenHandler)(Object)this;
        if (self == null) return;
        
        // Get container position from the first container slot
        BlockPos pos = null;
        for (Slot slot : self.slots) {
            if (slot.inventory != player.getInventory()) {
                if (slot.inventory instanceof net.minecraft.block.entity.BlockEntity be) {
                    pos = be.getPos();
                    break;
                } else {
                    pos = player.getBlockPos(); // fallback
                    break;
                }
            }
        }
        
        if (pos == null) return;
        
        int i = 0;
        for (Slot slot : self.slots) {
            if (slot.inventory == player.getInventory()) {
                continue; // Skip player inventory slots
            }
            
            if (i >= flowframe$prevContainerState.length) break;
            
            ItemStack before = flowframe$prevContainerState[i++];
            ItemStack after = slot.getStack();
            
            // Only log significant changes (not tiny count differences)
            boolean sameItem = ItemStack.areItemsEqual(before, after) && Objects.equals(before.getNbt(), after.getNbt());
            int diff = after.getCount() - before.getCount();
            
            if (sameItem && Math.abs(diff) >= 1) { // Only log if at least 1 item changed
                if (diff > 0) {
                    ItemStack deposited = after.copy();
                    deposited.setCount(diff);
                    LogStorage.logContainerAction("deposited", player, pos, deposited);
                } else {
                    ItemStack withdrew = before.copy();
                    withdrew.setCount(-diff);
                    LogStorage.logContainerAction("withdrew", player, pos, withdrew);
                }
            }
            // Log complete item swaps only if items are actually different
            else if (!before.isEmpty() && !after.isEmpty() && !sameItem) {
                LogStorage.logContainerAction("withdrew", player, pos, before.copy());
                LogStorage.logContainerAction("deposited", player, pos, after.copy());
            }
            // Log new deposits (empty to non-empty)
            else if (before.isEmpty() && !after.isEmpty()) {
                LogStorage.logContainerAction("deposited", player, pos, after.copy());
            }
            // Log complete withdrawals (non-empty to empty)
            else if (!before.isEmpty() && after.isEmpty()) {
                LogStorage.logContainerAction("withdrew", player, pos, before.copy());
            }
        }
        
        // Clean up
        flowframe$prevContainerState = null;
        flowframe$containerSize = 0;
    }
}
