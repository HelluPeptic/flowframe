package com.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.features.afkkick.AFKKickFeature;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinServerPlayNetworkHandler_AFKDetection {
    
    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        
        // Check if this packet contains rotation changes (camera movement)
        if (packet.changesLook()) {
            AFKKickFeature.updatePlayerActivity(handler.getPlayer());
        }
    }
}