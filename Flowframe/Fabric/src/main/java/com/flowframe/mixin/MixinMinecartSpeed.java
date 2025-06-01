package com.flowframe.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinMinecartSpeed {
    @Redirect(
        method = "moveOnRail",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getMaxSpeed()D"
        )
    )
    private double redirectGetMaxSpeed(AbstractMinecartEntity instance) {
        return 0.8D; // double the default speed
    }
}
