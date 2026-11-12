package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.flowframe.FlowframeFabricMod;

import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * Hooks the moment a rider makes their horse jump, for HorseTraining. The server
 * calls handleStartJump when it receives the rider's jump packet, which makes it a
 * reliable server-side signal (the jump movement itself is driven by the client).
 */
@Mixin(AbstractHorse.class)
public class AbstractHorseMixin {

    @Inject(method = "handleStartJump", at = @At("HEAD"))
    private void flowframe$trainJump(int power, CallbackInfo ci) {
        AbstractHorse horse = (AbstractHorse) (Object) this;
        if (horse.level().isClientSide()) {
            return;
        }
        FlowframeFabricMod mod = FlowframeFabricMod.getInstance();
        if (mod != null) {
            mod.getHorseTraining().onRiderJump(horse);
        }
    }
}
