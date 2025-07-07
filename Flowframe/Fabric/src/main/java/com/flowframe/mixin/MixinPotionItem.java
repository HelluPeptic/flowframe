package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.potion.PotionUtil;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Mixin(PotionItem.class)
public abstract class MixinPotionItem {
    private static final ThreadLocal<Boolean> flowframe$potionFlag = ThreadLocal.withInitial(() -> false);
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

    // This method must be private for Mixin compatibility
    private static boolean flowframe$isApplyingPotionEffect() {
        return flowframe$potionFlag.get();
    }

    @Redirect(
        method = "finishUsing",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z"
        )
    )
    private boolean flowframe$onAddStatusEffect(LivingEntity entity, StatusEffectInstance effect) {
        // Set flag and apply extended duration directly here to avoid double processing
        flowframe$potionFlag.set(true);
        try {
            StatusEffectInstance extendedEffect = flowframe$createExtendedEffect(effect);
            return entity.addStatusEffect(extendedEffect);
        } finally {
            flowframe$potionFlag.set(false);
        }
    }
    
    private StatusEffectInstance flowframe$createExtendedEffect(StatusEffectInstance effect) {
        if (effect == null || effect.isAmbient()) {
            return effect;
        }
        
        String effectId = effect.getEffectType().getTranslationKey();
        boolean shouldExtend = effect.getDuration() < MIN_DURATION || HERBAL_BREWS_EFFECTS.contains(effectId);
        
        if (shouldExtend) {
            return new StatusEffectInstance(
                effect.getEffectType(),
                MIN_DURATION,
                effect.getAmplifier(),
                effect.isAmbient(),
                false, // Hide particles for performance
                effect.shouldShowIcon()
            );
        }
        
        return effect;
    }

    @Inject(
        method = "finishUsing",
        at = @At("RETURN")
    )
    private void flowframe$handleSpecialCases(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClient) return;
        
        // Special case: Handle Rooibos Tea regeneration
        if (stack.getTranslationKey().startsWith("item.herbalbrews.rooibos_tea")) {
            StatusEffectInstance regenEffect = user.getStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION);
            if (regenEffect != null && regenEffect.getDuration() < MIN_DURATION) {
                StatusEffectInstance extendedRegen = new StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.REGENERATION,
                    MIN_DURATION,
                    regenEffect.getAmplifier(),
                    regenEffect.isAmbient(),
                    false,
                    regenEffect.shouldShowIcon()
                );
                user.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION);
                user.addStatusEffect(extendedRegen);
            }
        }
    }
}
