package com.flowframe.mixin;

import com.flowframe.features.gungame.GunGame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_GunGamePvp {
    
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onGunGameDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = source.getAttacker();
        
        // Only handle player vs player damage
        if (attacker instanceof ServerPlayerEntity && (Object)this instanceof ServerPlayerEntity) {
            ServerPlayerEntity attackingPlayer = (ServerPlayerEntity) attacker;
            ServerPlayerEntity targetPlayer = (ServerPlayerEntity) (Object) this;
            
            GunGame game = GunGame.getInstance();
            
            // If both players are in a gun game
            if (game.isPlayerInGame(attackingPlayer.getUuid()) && game.isPlayerInGame(targetPlayer.getUuid())) {
                
                // Check if PvP is allowed (only during ACTIVE state)
                if (game.getState() != GunGame.GunGameState.ACTIVE || !game.isPvpEnabled()) {
                    cir.setReturnValue(false);
                    return;
                }
                
                // Check if players are on the same team
                var attackerTeam = game.getPlayerTeam(attackingPlayer.getUuid());
                var targetTeam = game.getPlayerTeam(targetPlayer.getUuid());
                
                if (attackerTeam != null && targetTeam != null && attackerTeam.equals(targetTeam)) {
                    // Prevent friendly fire
                    cir.setReturnValue(false);
                    return;
                }
            }
            // If only one player is in gun game, and the game is active, prevent damage from non-participants
            else if ((game.isPlayerInGame(attackingPlayer.getUuid()) || game.isPlayerInGame(targetPlayer.getUuid())) &&
                     game.getState() == GunGame.GunGameState.ACTIVE) {
                cir.setReturnValue(false);
            }
        }
    }
}
