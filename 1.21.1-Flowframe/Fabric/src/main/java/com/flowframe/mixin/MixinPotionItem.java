package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.registry.Registries;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PotionItem.class)
public abstract class MixinPotionItem {
    private static final ThreadLocal<Boolean> flowframe$potionFlag = ThreadLocal.withInitial(() -> false);
    private static final int MIN_DURATION = 2 * 60 * 60 * 20;

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
        flowframe$potionFlag.set(true);
        try {
            return entity.addStatusEffect(effect);
        } finally {
            flowframe$potionFlag.set(false);
        }
    }

    @Inject(
        method = "finishUsing",
        at = @At("RETURN")
    )
    private void flowframe$makePotionEffectsLonger(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (world.isClient) return;
        List<StatusEffectInstance> effects = null; // TODO: Use correct mapping for PotionUtil.getEffects(stack) in Yarn 1.21.1
        for (StatusEffectInstance effect : effects) {
            // Exception: Always extend 'feral', 'balanced', 'tough', 'lifeleech', 'fortune', 'bonding', 'deeprush', and 'regeneration' from HerbalBrews
            // Get the effect's registry id as a string
            String effectId = Registries.STATUS_EFFECT.getId(effect.getEffectType().get()).toString();
            boolean isFeral = effectId.equals("effect.herbalbrews.feral");
            boolean isBalanced = effectId.equals("effect.herbalbrews.balanced");
            boolean isTough = effectId.equals("effect.herbalbrews.tough");
            boolean isLifeleech = effectId.equals("effect.herbalbrews.lifeleech");
            boolean isFortune = effectId.equals("effect.herbalbrews.fortune");
            boolean isBonding = effectId.equals("effect.herbalbrews.bonding");
            boolean isDeeprush = effectId.equals("effect.herbalbrews.deeprush");
            boolean isHerbalRegen = effectId.equals("effect.minecraft.regeneration") && stack.getName().getString().startsWith("item.herbalbrews.rooibos_tea");
            if ((effect != null && !effect.isAmbient() && (effect.getDuration() < MIN_DURATION || isFeral || isBalanced || isTough || isLifeleech || isFortune || isBonding || isDeeprush)) || isHerbalRegen) {
                StatusEffectInstance longer = new StatusEffectInstance(
                    effect.getEffectType(),
                    MIN_DURATION,
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    false,
                    effect.shouldShowIcon()
                );
                // Remove the old effect first to prevent stacking issues
                user.removeStatusEffect(effect.getEffectType());
                user.addStatusEffect(longer);
            }
        }
    }
}
