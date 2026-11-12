package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.flowframe.FlowframeFabricMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stops players from going INTO the Nether while Flowframe's Nether toggle is off.
 *
 * Covers players riding something through the portal too (e.g. a minecart), since
 * otherwise the vehicle would carry them through. Portals inside the Nether are
 * left alone, so anyone who was already there when it got closed can still leave.
 */
@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void flowframe$blockNetherPortalWhenDisabled(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise, CallbackInfo ci) {
        if (level.isClientSide() || level.dimension() == Level.NETHER) {
            return;
        }
        FlowframeFabricMod mod = FlowframeFabricMod.getInstance();
        if (mod == null || mod.isNetherPortalEnabled()) {
            return;
        }
        if (entity instanceof ServerPlayer || entity.hasPassenger(passenger -> passenger instanceof ServerPlayer)) {
            ci.cancel();
        }
    }
}
