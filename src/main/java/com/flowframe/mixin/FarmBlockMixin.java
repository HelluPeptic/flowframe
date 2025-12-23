package com.flowframe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public class FarmBlockMixin {
    
    /**
     * Prevents farmland from being trampled when entities fall or jump on it.
     * This completely disables the crop trampling mechanic.
     */
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void preventTrampling(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance, CallbackInfo ci) {
        // Cancel the fall damage to farmland, preventing trampling
        // Entity still takes fall damage normally via super.fallOn
        entity.causeFallDamage(fallDistance, 1.0F, level.damageSources().fall());
        ci.cancel();
    }
}
