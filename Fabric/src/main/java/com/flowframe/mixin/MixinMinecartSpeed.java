package com.flowframe.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AbstractMinecartEntity.class)
public class MixinMinecartSpeed {
    // Double the minecart speed by modifying the velocity multiplier
    @ModifyVariable(method = "moveOnRail", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private double doubleMinecartSpeed(double original) {
        return original * 2;
    }
}
