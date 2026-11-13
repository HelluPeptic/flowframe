package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.flowframe.flowframe.HeadRiding;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Vanilla's Entity.startRiding refuses any vehicle whose entity type can't be saved,
 * which rules out players. This makes an exception for HeadRiding's seats only, so a
 * seat can sit on a player's head (and the rider sits on the seat).
 */
@Mixin(Entity.class)
public abstract class EntityRidingMixin {

    @ModifyExpressionValue(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z"))
    private boolean flowframe$letHeadSeatsRidePlayers(boolean canSerialize, @Local(argsOnly = true) Entity vehicle) {
        return canSerialize || (vehicle instanceof Player && HeadRiding.isSeat((Entity) (Object) this));
    }
}
