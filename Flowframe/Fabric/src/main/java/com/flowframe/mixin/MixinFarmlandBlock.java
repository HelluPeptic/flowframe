package com.flowframe.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmlandBlock.class)
public abstract class MixinFarmlandBlock {
    @Inject(method = "onLandedUpon", cancellable = true, at = @At("HEAD"))
    private void denyTrample(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance,
            CallbackInfo ci) {
        // Cancel the trampling entirely
        ci.cancel();
    }
}
