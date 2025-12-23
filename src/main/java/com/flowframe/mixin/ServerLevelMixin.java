package com.flowframe.mixin;

import com.flowframe.util.PathBlockSpeedTracker;
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
     * Clean up player data when they disconnect
     */
    @Inject(method = "removePlayerImmediately", at = @At("HEAD"))
    private void onPlayerDisconnect(ServerPlayer player, Entity.RemovalReason reason, CallbackInfo ci) {
        PathBlockSpeedTracker.removePlayer(player.getUUID());
    }
}
