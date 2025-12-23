package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public class EndPortalBlockMixin {
    
    /**
     * Prevents players from entering the End portal if they have disabled it with /endtoggle
     */
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    public void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean fromInsideBlock, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            if (!FlowframeConfig.isEndPortalEnabled(player.getUUID())) {
                ci.cancel();
            }
        }
    }
}
