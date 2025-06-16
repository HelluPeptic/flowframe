package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PotionDuration {
    // Minimum duration: 2 hours in ticks
    private static final int MIN_DURATION = 2 * 60 * 60 * 20;
    private static final Identifier TIPSY_ID = new Identifier("brewinandchewin", "tipsy");
    private static final ThreadLocal<Boolean> flowframe$recursing = ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "addStatusEffect",
        at = @At("HEAD"),
        cancellable = true
    )
    private void makePotionLastLonger(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect == null || effect.isAmbient()) {
            return;
        }
        if (flowframe$recursing.get()) {
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
        int duration = effect.getDuration();
        if (duration == 7 || duration == 11) {
            return;
        }
        Identifier effectId = Registries.STATUS_EFFECT.getId(effect.getEffectType());
        if ((effectId != null && effectId.equals(TIPSY_ID)) || effect.getDuration() < MIN_DURATION) {
            flowframe$recursing.set(true);
            try {
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
            } finally {
                flowframe$recursing.set(false);
            }
        }
    }
}
