package com.flowframe.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ComposterBlock.class)
public abstract class MixinComposterBlock {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void denyEmptyHandInteract(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) {
            cir.setReturnValue(ActionResult.FAIL);
            cir.cancel(); // Ensure no further processing occurs
        }
    }
}
