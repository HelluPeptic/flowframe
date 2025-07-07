package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_TipsyDuration {
    private static final int MIN_DURATION = 2 * 60 * 60 * 20;
    private static final Identifier TIPSY_ID = new Identifier("brewinandchewin", "tipsy");
    private static StatusEffect TIPSY_EFFECT = null;
    
    // Cache the tipsy effect for performance
    private static StatusEffect getTipsyEffect() {
        if (TIPSY_EFFECT == null) {
            TIPSY_EFFECT = Registries.STATUS_EFFECT.get(TIPSY_ID);
        }
        return TIPSY_EFFECT;
    }

    @Inject(
        method = "addStatusEffect",
        at = @At("HEAD"),
        cancellable = true
    )
    private void flowframe$extendTipsyDuration(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect == null || effect.isAmbient()) {
            return;
        }
        
        StatusEffect tipsyEffect = getTipsyEffect();
        if (tipsyEffect != null && effect.getEffectType() == tipsyEffect && effect.getDuration() < MIN_DURATION) {
            StatusEffectInstance extendedTipsy = new StatusEffectInstance(
                tipsyEffect,
                MIN_DURATION,
                effect.getAmplifier(),
                effect.isAmbient(),
                false, // Hide particles for performance
                effect.shouldShowIcon()
            );
            LivingEntity self = (LivingEntity)(Object)this;
            cir.setReturnValue(self.addStatusEffect(extendedTipsy));
        }
    }
}
