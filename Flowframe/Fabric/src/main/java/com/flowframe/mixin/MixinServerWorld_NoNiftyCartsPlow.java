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
public abstract class MixinServerWorld_NoNiftyCartsPlow {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void denyNiftyCartsPlowSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Nifty Carts plow entity is usually registered as "niftycarts:plow" or similar
        Identifier id = EntityType.getId(entity.getType());
        if (id != null && id.getNamespace().equals("niftycarts") && id.getPath().contains("plow")) {
            cir.setReturnValue(false); // Cancel plow spawn
        }
    }
}
