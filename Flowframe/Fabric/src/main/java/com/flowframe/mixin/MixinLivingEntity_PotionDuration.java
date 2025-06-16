package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PotionDuration {
    // Make all potions (vanilla and modded) last 2 hours (2*60*60*20 ticks = 144000)
    @ModifyArg(
        method = "addStatusEffect",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/effect/StatusEffectInstance;<init>(Lnet/minecraft/entity/effect/StatusEffect;I)V",
            ordinal = 0
        ),
        index = 1
    )
    private int makePotionLastLonger(int originalDuration) {
        // Only increase if not already longer than 2 hours
        int twoHours = 2 * 60 * 60 * 20;
        return Math.max(originalDuration, twoHours);
    }

    @ModifyArg(
        method = "addStatusEffect",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/effect/StatusEffectInstance;<init>(Lnet/minecraft/entity/effect/StatusEffect;IIZZZ)V",
            ordinal = 0
        ),
        index = 1
    )
    private int makePotionLastLongerFull(int originalDuration) {
        int twoHours = 2 * 60 * 60 * 20;
        return Math.max(originalDuration, twoHours);
    }
}
