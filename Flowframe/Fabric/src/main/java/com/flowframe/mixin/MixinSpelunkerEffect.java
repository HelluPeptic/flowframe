package com.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = net.trique.mythicupgrades.effect.SpelunkerEffect.class, remap = false)
public abstract class MixinSpelunkerEffect {
    @Inject(method = "method_5552", at = @At("HEAD"), cancellable = true, remap = false)
    private void alwaysFalseDuration(int i, int j, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
