package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.BlockState;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Prevents crop trampling outright by cancelling the farmland-to-dirt
 * conversion at its source. DO_MOB_GRIEFING being forced off only stops mobs
 * (vanilla's own trample check is "entity instanceof Player || mobGriefing"),
 * so players could still trample crops even with that gamerule off — this
 * mixin covers both, unconditionally.
 */
@Mixin(FarmlandBlock.class)
public class FarmlandBlockMixin {

    @Inject(method = "setToDirt", at = @At("HEAD"), cancellable = true)
    private static void flowframe$preventTrample(Entity entity, BlockState state, World world, BlockPos pos, CallbackInfo ci) {
        ci.cancel();
    }
}
