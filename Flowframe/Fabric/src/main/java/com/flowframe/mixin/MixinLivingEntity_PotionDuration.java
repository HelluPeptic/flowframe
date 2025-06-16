package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PotionDuration {
    // Minimum duration: 2 hours in ticks
    private static final int MIN_DURATION = 2 * 60 * 60 * 20;

    @Inject(
        method = "addStatusEffect",
        at = @At("HEAD"),
        cancellable = true
    )
    private void makePotionLastLonger(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        // Skip beacon effects (ambient)
        if (effect == null || effect.isAmbient()) {
            return;
        }
        // Skip suspicious stew effects using ThreadLocal flag
        try {
            Class<?> stewMixin = Class.forName("com.flowframe.mixin.MixinSuspiciousStewItem");
            boolean isStew = (boolean) stewMixin.getMethod("flowframe$isApplyingStewEffect").invoke(null);
            if (isStew) {
                return;
            }
        } catch (Exception ignored) {}
        // Skip suspicious stew effects (duration 7 or 11 ticks)
        int duration = effect.getDuration();
        if (duration == 7 || duration == 11) {
            return;
        }
        if (effect.getDuration() < MIN_DURATION) {
            StatusEffectInstance longer = new StatusEffectInstance(
                effect.getEffectType(),
                MIN_DURATION,
                effect.getAmplifier(),
                effect.isAmbient(),
                effect.shouldShowParticles(),
                effect.shouldShowIcon()
            );
            LivingEntity self = (LivingEntity)(Object)this;
            cir.setReturnValue(self.addStatusEffect(longer));
        }
    }
}
