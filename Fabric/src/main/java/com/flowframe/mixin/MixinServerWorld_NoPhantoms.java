package com.flowframe.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld_NoPhantoms {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void denyPhantomEntitySpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getType() == EntityType.PHANTOM) {
            cir.setReturnValue(false); // Cancel phantom spawn
        }
    }
}
