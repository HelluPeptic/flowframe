package com.flowframe.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    
    /**
     * Clean up when players disconnect - no longer needed for simplified path block speed
     */
    @Inject(method = "removePlayerImmediately", at = @At("HEAD"))
    private void onPlayerDisconnect(ServerPlayer player, Entity.RemovalReason reason, CallbackInfo ci) {
        // Path block speed is now handled per-tick, no cleanup needed
    }
}
