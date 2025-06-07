package com.flowframe.mixin;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartEntity.class)
public abstract class MixinAbstractMinecartEntity {
    @Inject(method = "tick", at = @At("TAIL"))
    private void flowframe$doubleMinecartSpeed(CallbackInfo ci) {
        AbstractMinecartEntity minecart = (AbstractMinecartEntity) (Object) this;
        Vec3d velocity = minecart.getVelocity();
        // Only double if moving (prevents issues with stopped carts)
        if (velocity.lengthSquared() > 0.0001) {
            minecart.setVelocity(velocity.multiply(2.0));
        }
    }

    @Redirect(
        method = "moveOnRail(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getMaxSpeed()D")
    )
    private double flowframe$doubleMinecartMaxSpeed(AbstractMinecartEntity instance) {
        return 0.8D; // Double the vanilla max speed (0.4D)
    }
}
