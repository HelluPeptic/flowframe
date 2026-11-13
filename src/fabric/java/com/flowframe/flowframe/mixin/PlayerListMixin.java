package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.flowframe.FlowframeFabricMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Takes a leaving player out of any head-riding stack before vanilla removes them.
 * Vanilla assumes players are never vehicles: if a rider left while still on someone's
 * head, it would unload the player underneath them too.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void flowframe$leaveHeadRidingStack(ServerPlayer player, CallbackInfo ci) {
        FlowframeFabricMod mod = FlowframeFabricMod.getInstance();
        if (mod != null) {
            mod.getHeadRiding().onPlayerLeave(player);
        }
    }
}
