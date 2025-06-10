package com.flowframe.mixin;

import com.flowframe.features.togglepvp.TogglePvpFeature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PvpToggle {
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity && (Object)this instanceof ServerPlayerEntity) {
            ServerPlayerEntity attackingPlayer = (ServerPlayerEntity) attacker;
            ServerPlayerEntity targetPlayer = (ServerPlayerEntity) (Object) this;
            // If either attacker or target has PVP disabled, cancel damage
            if (!TogglePvpFeature.isPvpEnabled(attackingPlayer) || !TogglePvpFeature.isPvpEnabled(targetPlayer)) {
                cir.setReturnValue(false);
            }
        }
    }
}
