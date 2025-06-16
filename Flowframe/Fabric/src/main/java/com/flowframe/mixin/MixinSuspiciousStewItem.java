package com.flowframe.mixin;

import com.flowframe.playerflags.PlayerEntityStewFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.SuspiciousStewItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SuspiciousStewItem.class)
public abstract class MixinSuspiciousStewItem {
    private static final ThreadLocal<Boolean> flowframe$stewFlag = ThreadLocal.withInitial(() -> false);

    public static boolean flowframe$isApplyingStewEffect() {
        return flowframe$stewFlag.get();
    }

    @Redirect(
        method = "finishUsing",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z"
        )
    )
    private boolean flowframe$onAddStatusEffect(LivingEntity entity, StatusEffectInstance effect) {
        if (entity instanceof PlayerEntity) {
            ((PlayerEntityStewFlag)entity).flowframe$setJustAteSuspiciousStew(true);
        }
        flowframe$stewFlag.set(true);
        try {
            return entity.addStatusEffect(effect);
        } finally {
            flowframe$stewFlag.set(false);
        }
    }
}
