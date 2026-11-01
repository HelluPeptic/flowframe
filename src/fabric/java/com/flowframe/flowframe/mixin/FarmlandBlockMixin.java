package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Prevents crop trampling by cancelling the farmland-to-dirt conversion when an
 * entity causes it. MOB_GRIEFING being forced off only stops mobs (vanilla's own
 * trample check is "entity instanceof Player || mobGriefing"), so players could
 * still trample crops even with that gamerule off — this mixin covers both.
 *
 * Only entity-caused conversions are cancelled: fallOn (trampling) passes the
 * entity, while tick/randomTick pass null for farmland drying out or getting a
 * solid block placed on top, which should still revert to dirt as normal.
 */
@Mixin(FarmlandBlock.class)
public class FarmlandBlockMixin {

    @Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true)
    private void flowframe$preventTrample(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (entity != null) {
            ci.cancel();
        }
    }
}
