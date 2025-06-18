package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
        if (effect == null || effect.isAmbient()) {
            return;
        }
        String effectId = effect.getEffectType().getTranslationKey();
        // --- Always extend HerbalBrews effects ---
        boolean isHerbalBrews = effectId.startsWith("effect.herbalbrews.");
        if (isHerbalBrews && effect.getDuration() < MIN_DURATION) {
            StatusEffectInstance longer = new StatusEffectInstance(
                effect.getEffectType(),
                MIN_DURATION,
                effect.getAmplifier(),
                effect.isAmbient(),
                false,
                effect.shouldShowIcon()
            );
            LivingEntity self = (LivingEntity)(Object)this;
            cir.setReturnValue(self.addStatusEffect(longer));
            return;
        }
        // Only apply for drinkable potions using the ThreadLocal flag
        try {
            Class<?> potionMixin = Class.forName("com.flowframe.mixin.MixinPotionItem");
            java.lang.reflect.Method flagMethod = potionMixin.getDeclaredMethod("flowframe$isApplyingPotionEffect");
            flagMethod.setAccessible(true);
            boolean isPotion = (boolean) flagMethod.invoke(null);
            if (isPotion && effect.getDuration() < MIN_DURATION) {
                StatusEffectInstance longer = new StatusEffectInstance(
                    effect.getEffectType(),
                    MIN_DURATION,
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    false, // Hide particles
                    effect.shouldShowIcon()
                );
                LivingEntity self = (LivingEntity)(Object)this;
                cir.setReturnValue(self.addStatusEffect(longer));
                return;
            }
        } catch (Exception ignored) {
            // If reflection fails, just continue to the next logic
        }
        // --- Special case: If holding Rooibos Tea, always extend regeneration to 2 hours ---
        if (effectId.equals("effect.minecraft.regeneration") && ((LivingEntity)(Object)this).getType().getTranslationKey().equals("entity.minecraft.player")) {
            PlayerEntity player = (PlayerEntity)(Object)this;
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();
            if (mainHand.getTranslationKey().equals("item.herbalbrews.rooibos_tea") || offHand.getTranslationKey().equals("item.herbalbrews.rooibos_tea")) {
                if (effect.getDuration() < MIN_DURATION) {
                    StatusEffectInstance longer = new StatusEffectInstance(
                        effect.getEffectType(),
                        MIN_DURATION,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        false, //Hide particles
                        effect.shouldShowIcon()
                    );
                    cir.setReturnValue(player.addStatusEffect(longer));
                    return;
                }
            }
        }
    }
}
