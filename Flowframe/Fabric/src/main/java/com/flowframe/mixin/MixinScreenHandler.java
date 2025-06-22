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
    @Inject(method = "onSlotClick", at = @At("HEAD"))
    private void flowframe$logSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (slotIndex < 0) return;
        Slot slot = ((ScreenHandler)(Object)this).getSlot(slotIndex);
        if (slot == null || slot.inventory == null) return;
        BlockPos pos = player.getBlockPos(); // Approximate, not always the container's pos
        ItemStack stack = slot.getStack();
        if (!stack.isEmpty()) {
            // Log removal
            LogStorage.logContainerAction("remove", player, pos, stack);
        } else if (!player.getMainHandStack().isEmpty()) {
            // Log insertion
            LogStorage.logContainerAction("insert", player, pos, player.getMainHandStack());
        }
    }
}
