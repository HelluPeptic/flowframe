package com.flowframe.mixin;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class PotionDurationMixin {

    /**
     * Extends potion durations to 2 hours when drinking potions, except for
     * poison, harming, and weakness
     */
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"), argsOnly = true)
    private MobEffectInstance flowframe$extendPotionDuration(MobEffectInstance effectInstance) {
        if (effectInstance == null) {
            return effectInstance;
        }

        LivingEntity entity = (LivingEntity) (Object) this;

        // Only extend effects for players
        if (!(entity instanceof Player player)) {
            return effectInstance;
        }

        // Only extend if player is currently using a potion item
        ItemStack useItem = player.getUseItem();
        if (useItem.isEmpty() || useItem.getItem() != Items.POTION) {
            return effectInstance;
        }

        MobEffect effect = effectInstance.getEffect().value();

        // Skip poison, harming, and weakness
        if (effect == MobEffects.POISON
                || effect == MobEffects.INSTANT_DAMAGE
                || effect == MobEffects.WEAKNESS) {
            return effectInstance;
        }

        // 2 hours = 2 * 60 * 60 * 20 ticks = 144000 ticks
        int twoHoursTicks = 144000;

        // Create new effect instance with extended duration
        return new MobEffectInstance(
                effectInstance.getEffect(),
                twoHoursTicks,
                effectInstance.getAmplifier(),
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon()
        );
    }
}
