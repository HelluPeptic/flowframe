package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.flowframe.FlowframeFabricMod;

import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Cancels EndPortalBlock's entity-collision handling outright when Flowframe's End
 * portal toggle is disabled, so walking into the portal truly does nothing — no
 * teleport happens in the first place, rather than happening and then being reversed
 * a moment later (which is what the old AFTER_PLAYER_CHANGE_WORLD-based approach did).
 *
 * That reactive approach is kept in FlowframeFabricMod as a fallback for edge cases
 * this mixin doesn't cover (e.g. commands, other mods teleporting a player into The
 * End directly, End gateways), but under normal portal-walking this mixin is what
 * actually stops it before it starts.
 */
@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {

    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void flowframe$blockEndPortalWhenDisabled(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler collisionHandler, boolean bl, CallbackInfo ci) {
        if (world.isClient()) {
            return;
        }
        FlowframeFabricMod mod = FlowframeFabricMod.getInstance();
        if (mod != null && !mod.isEndPortalEnabled()) {
            ci.cancel();
        }
    }
}