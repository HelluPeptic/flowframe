package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.HashSet;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PotionDuration {
    // Minimum duration: 2 hours in ticks
    private static final int MIN_DURATION = 2 * 60 * 60 * 20;
    
    // Cache for HerbalBrews effects to avoid string comparisons
    private static final Set<String> HERBAL_BREWS_EFFECTS = new HashSet<>();
    static {
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.feral");
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.balanced");
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.tough");
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.lifeleech");
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.fortune");
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.bonding");
        HERBAL_BREWS_EFFECTS.add("effect.herbalbrews.deeprush");
    }

    @Inject(
        method = "addStatusEffect",
        at = @At("HEAD"),
        cancellable = true
    )
    private void makePotionLastLonger(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if (effect == null || effect.isAmbient()) {
            return;
        }
        
        String effectId = effect.getEffectType().getTranslationKey();
        
        // Check if this is a HerbalBrews effect that should always be extended
        if (HERBAL_BREWS_EFFECTS.contains(effectId) && effect.getDuration() < MIN_DURATION) {
            StatusEffectInstance longer = new StatusEffectInstance(
                effect.getEffectType(),
                MIN_DURATION,
                effect.getAmplifier(),
                effect.isAmbient(),
                false, // Hide particles for performance
                effect.shouldShowIcon()
            );
            LivingEntity self = (LivingEntity)(Object)this;
            cir.setReturnValue(self.addStatusEffect(longer));
            return;
        }
        
        // Handle special case for Rooibos Tea regeneration
        if (effectId.equals("effect.minecraft.regeneration") && 
            ((LivingEntity)(Object)this) instanceof PlayerEntity && 
            effect.getDuration() < MIN_DURATION) {
            
            PlayerEntity player = (PlayerEntity)(LivingEntity)(Object)this;
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();
            
            if (mainHand.getTranslationKey().equals("item.herbalbrews.rooibos_tea") || 
                offHand.getTranslationKey().equals("item.herbalbrews.rooibos_tea")) {
                
                StatusEffectInstance longer = new StatusEffectInstance(
                    effect.getEffectType(),
                    MIN_DURATION,
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    false, // Hide particles for performance
                    effect.shouldShowIcon()
                );
                cir.setReturnValue(player.addStatusEffect(longer));
                return;
            }
        }
        
        // For potion effects applied by drinking (handled by PotionItem mixin),
        // we don't need to do anything here as they're already processed
    }
}
