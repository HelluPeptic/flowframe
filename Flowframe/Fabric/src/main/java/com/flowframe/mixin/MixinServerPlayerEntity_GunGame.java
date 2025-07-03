package com.flowframe.mixin;

import com.flowframe.features.gungame.GunGame;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity_GunGame {
    
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onPlayerDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        GunGame game = GunGame.getInstance();
        
        // Only handle death if player is in an active gun game
        if (game.isPlayerInGame(player.getUuid()) && 
            game.getState() == GunGame.GunGameState.ACTIVE) {
            game.handlePlayerDeath(player);
        }
    }
}
