package com.flowframe.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld_NoPhantoms {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void denyPhantomEntitySpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ServerWorld serverWorld = (ServerWorld) (Object) this;
        if (entity.getType() == EntityType.PHANTOM) {
            if (serverWorld.getRegistryKey().getValue().equals(new Identifier("minecraft", "overworld"))) {
                cir.setReturnValue(false); // Cancel phantom spawn in the overworld
            }
            // Do nothing in the nether or other dimensions, let vanilla handle it
        }
    }
}
