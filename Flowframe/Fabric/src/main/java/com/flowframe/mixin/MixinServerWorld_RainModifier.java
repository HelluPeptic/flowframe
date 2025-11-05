package com.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.features.rainmodifier.RainModifierFeature;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

@Mixin(ServerWorld.class)
public abstract class MixinServerWorld_RainModifier {
    
    @Inject(method = "setWeather", at = @At("HEAD"), cancellable = true)
    private void onSetWeather(int clearDuration, int rainDuration, boolean raining, boolean thundering, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        
        // Only interfere with natural weather changes when rain is starting
        // Don't interfere if rain is already happening or if it's stopping
        if (raining && !world.isRaining() && RainModifierFeature.shouldSkipRain()) {
            // Cancel the rain by setting it to clear weather instead
            world.setWeather(clearDuration + rainDuration, 0, false, false);
            
            // Notify players who have notifications enabled
            for (ServerPlayerEntity player : world.getPlayers()) {
                RainModifierFeature.notifyPlayer(player);
            }
            
            ci.cancel();
        }
    }
}