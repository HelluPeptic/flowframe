package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.flowframe.FlowframeFabricMod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cancels EndPortalBlock's entity-collision handling outright when Flowframe's End
 * portal toggle is disabled, so walking into the portal truly does nothing — no
 * teleport happens in the first place, rather than happening and then being reversed
 * a moment later (which is what the old AFTER_PLAYER_CHANGE_LEVEL-based approach did).
 *
 * That reactive approach is kept in FlowframeFabricMod as a fallback for edge cases
 * this mixin doesn't cover (e.g. commands, other mods teleporting a player into The
 * End directly, End gateways), but under normal portal-walking this mixin is what
 * actually stops it before it starts.
 */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void flowframe$blockEndPortalWhenDisabled(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        FlowframeFabricMod mod = FlowframeFabricMod.getInstance();
        if (mod != null && !mod.isEndPortalEnabled()) {
            ci.cancel();
        }
    }
}
