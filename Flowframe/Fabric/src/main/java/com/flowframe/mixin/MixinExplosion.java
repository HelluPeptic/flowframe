package com.flowframe.mixin;

import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.CreeperEntity;
import java.util.List;

@Mixin(Explosion.class)
public abstract class MixinExplosion {
    @Inject(method = "collectBlocksAndDamageEntities", at = @At("RETURN"))
    private void onCollectBlocksAndDamageEntities(CallbackInfo ci) {
        Explosion self = (Explosion)(Object)this;
        Entity exploder = self.getEntity();
        if (exploder instanceof CreeperEntity) {
            List<?> affectedBlocks = self.getAffectedBlocks();
            affectedBlocks.clear();
        }
    }
}
