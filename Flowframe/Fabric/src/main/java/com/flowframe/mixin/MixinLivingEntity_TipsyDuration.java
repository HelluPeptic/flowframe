package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_TipsyDuration {
    private static final int MIN_DURATION = 2 * 60 * 60 * 20;
    private static final Identifier TIPSY_ID = new Identifier("brewinandchewin", "tipsy");
    private int flowframe$lastTipsyDuration = -1;

    @Inject(method = "tickStatusEffects", at = @At("TAIL"))
    private void flowframe$extendTipsyDuration(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        StatusEffect tipsy = Registries.STATUS_EFFECT.get(TIPSY_ID);
        if (tipsy == null) return;
        StatusEffectInstance current = self.getStatusEffect(tipsy);
        if (current != null) {
            int curDuration = current.getDuration();
            if (curDuration < MIN_DURATION && (flowframe$lastTipsyDuration == -1 || curDuration > flowframe$lastTipsyDuration)) {
                // Only extend if newly applied or refreshed
                self.addStatusEffect(new StatusEffectInstance(
                    tipsy,
                    MIN_DURATION,
                    current.getAmplifier(),
                    current.isAmbient(),
                    current.shouldShowParticles(),
                    current.shouldShowIcon()
                ));
            }
            flowframe$lastTipsyDuration = self.getStatusEffect(tipsy) != null ? self.getStatusEffect(tipsy).getDuration() : -1;
        } else {
            flowframe$lastTipsyDuration = -1;
        }
    }
}
