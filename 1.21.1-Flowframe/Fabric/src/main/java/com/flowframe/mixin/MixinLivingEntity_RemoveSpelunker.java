package com.flowframe.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_RemoveSpelunker {
    @Inject(method = "addStatusEffect", at = @At("HEAD"), cancellable = true)
    private void removeSpelunker(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        StatusEffect spelunker = Registries.STATUS_EFFECT.get(Identifier.of("mythicupgrades", "spelunker"));
        if (effect.getEffectType() == spelunker) {
            cir.setReturnValue(false); // Prevent spelunker from being applied
        }
    }
}
