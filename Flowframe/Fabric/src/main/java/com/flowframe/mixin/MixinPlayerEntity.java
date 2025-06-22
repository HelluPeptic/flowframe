package com.flowframe.mixin;

import com.flowframe.features.minetracer.InventoryEventListener;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class MixinPlayerEntity {
    @Inject(method = "tick", at = @At("TAIL"))
    private void flowframe$logInventoryPickup(CallbackInfo ci) {
        InventoryEventListener.onPlayerTick((PlayerEntity)(Object)this);
    }
}
