package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ULTRA HIGH PERFORMANCE potion duration mixin
 * 
 * This version eliminates ALL expensive operations:
 * - No string operations (getTranslationKey() was causing 3.27% server load!)
 * - No reflection calls
 * - Direct object reference comparisons only
 * - Cached lookups
 * - Early exits to minimize CPU cycles
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PotionDuration_Fast {
    // 2 hours in ticks - performance critical: use final int for JVM optimization
    private static final int MIN_DURATION = 144000; // 2 * 60 * 60 * 20
    
    // Pre-computed effect references - NO runtime lookups!
    private static StatusEffect FERAL_EFFECT = null;
    private static StatusEffect BALANCED_EFFECT = null;
    private static StatusEffect TOUGH_EFFECT = null;
    private static StatusEffect LIFELEECH_EFFECT = null;
    private static StatusEffect FORTUNE_EFFECT = null;
    private static StatusEffect BONDING_EFFECT = null;
    private static StatusEffect DEEPRUSH_EFFECT = null;
    private static StatusEffect REGENERATION_EFFECT = null;
    private static Item ROOIBOS_TEA_ITEM = null;
    
    // Cache initialization - happens once at class load
    static {
        try {
            // Load all effects at startup to avoid ANY runtime registry lookups
            FERAL_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "feral"));
            BALANCED_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "balanced"));
            TOUGH_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "tough"));
            LIFELEECH_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "lifeleech"));
            FORTUNE_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "fortune"));
            BONDING_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "bonding"));
            DEEPRUSH_EFFECT = Registries.STATUS_EFFECT.get(new Identifier("herbalbrews", "deeprush"));
            
            REGENERATION_EFFECT = StatusEffects.REGENERATION;
            ROOIBOS_TEA_ITEM = Registries.ITEM.get(new Identifier("herbalbrews", "rooibos_tea"));
        } catch (Exception e) {
            // Fail silently if HerbalBrews isn't loaded
        }
    }

    @Inject(
        method = "addStatusEffect",
        at = @At("HEAD"),
        cancellable = true
    )
    private void makePotionLastLonger(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        // CRITICAL: Check duration FIRST to avoid all other work for effects that don't need extending
        if (effect == null || effect.isAmbient() || effect.getDuration() >= MIN_DURATION) {
            return;
        }
        
        StatusEffect effectType = effect.getEffectType();
        
        // ULTRA FAST: Direct memory address comparisons - no string operations!
        if (effectType == FERAL_EFFECT || effectType == BALANCED_EFFECT || 
            effectType == TOUGH_EFFECT || effectType == LIFELEECH_EFFECT ||
            effectType == FORTUNE_EFFECT || effectType == BONDING_EFFECT || 
            effectType == DEEPRUSH_EFFECT) {
            
            // Create extended effect with minimal object creation
            StatusEffectInstance extended = new StatusEffectInstance(
                effectType,
                MIN_DURATION,
                effect.getAmplifier(),
                false, // ambient = false
                false, // showParticles = false for performance
                effect.shouldShowIcon()
            );
            
            // Direct cast - no instanceof check needed for LivingEntity
            cir.setReturnValue(((LivingEntity)(Object)this).addStatusEffect(extended));
            return;
        }
        
        // Handle Rooibos Tea regeneration - also with direct object comparison
        if (effectType == REGENERATION_EFFECT && ROOIBOS_TEA_ITEM != null) {
            // Only check if this is a player (instanceof is fast for this check)
            if (((LivingEntity)(Object)this) instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity)(Object)this;
                
                // Direct item reference comparison - no strings!
                if (player.getMainHandStack().getItem() == ROOIBOS_TEA_ITEM || 
                    player.getOffHandStack().getItem() == ROOIBOS_TEA_ITEM) {
                    
                    StatusEffectInstance extended = new StatusEffectInstance(
                        effectType,
                        MIN_DURATION,
                        effect.getAmplifier(),
                        false, // ambient = false
                        false, // showParticles = false for performance
                        effect.shouldShowIcon()
                    );
                    
                    cir.setReturnValue(player.addStatusEffect(extended));
                    return;
                }
            }
        }
        
        // If we get here, no special handling needed - let vanilla handle it
    }
}
