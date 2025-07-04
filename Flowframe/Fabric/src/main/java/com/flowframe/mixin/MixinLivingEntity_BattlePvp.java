package com.flowframe.mixin;

import com.flowframe.features.gungame.Battle;
import com.flowframe.features.gungame.BattleTeam;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_BattlePvp {
    
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onBattleDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // Only handle ServerPlayerEntity instances
        if (!((Object)this instanceof ServerPlayerEntity)) {
            return;
        }
        
        ServerPlayerEntity targetPlayer = (ServerPlayerEntity) (Object) this;
        Battle game = Battle.getInstance();
        
        // Check if target player is in a battle (any state except INACTIVE)
        if (game.isPlayerInGame(targetPlayer.getUuid()) && game.getState() != Battle.BattleState.INACTIVE) {
            Entity attacker = source.getAttacker();
            
            // Handle player vs player damage
            if (attacker instanceof ServerPlayerEntity) {
                ServerPlayerEntity attackingPlayer = (ServerPlayerEntity) attacker;
                
                // If both players are in the battle
                if (game.isPlayerInGame(attackingPlayer.getUuid())) {
                    // Check if PvP is allowed (only during ACTIVE state)
                    if (game.getState() != Battle.BattleState.ACTIVE || !game.isPvpEnabled()) {
                        cir.setReturnValue(false);
                        return;
                    }
                    
                    // Check if players are on the same team (prevent friendly fire)
                    BattleTeam attackerTeam = game.getPlayerTeam(attackingPlayer.getUuid());
                    BattleTeam targetTeam = game.getPlayerTeam(targetPlayer.getUuid());
                    
                    if (attackerTeam != null && targetTeam != null && attackerTeam.equals(targetTeam)) {
                        cir.setReturnValue(false);
                        return;
                    }
                    
                    // Check if this damage would kill the player (with a safety margin)
                    if (targetPlayer.getHealth() - amount <= 0.5f) {
                        // Completely prevent the damage and handle elimination immediately
                        cir.setReturnValue(false);
                        
                        // Schedule immediate handling to avoid any potential death processing
                        targetPlayer.getServer().execute(() -> handleBattleDeath(targetPlayer, game));
                        return;
                    }
                } else {
                    // Attacker is not in battle but target is - prevent damage from non-participants
                    cir.setReturnValue(false);
                    return;
                }
            } else {
                // Non-player damage to battle participant during active game
                if (game.getState() == Battle.BattleState.ACTIVE) {
                    // Check if this would be lethal
                    if (targetPlayer.getHealth() - amount <= 0.5f) {
                        // Prevent any lethal damage from non-players during battle
                        cir.setReturnValue(false);
                        
                        // Schedule immediate handling
                        targetPlayer.getServer().execute(() -> handleBattleDeath(targetPlayer, game));
                        return;
                    }
                } else {
                    // Prevent all non-player damage during non-active states
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
    
    private void handleBattleDeath(ServerPlayerEntity player, Battle battle) {
        if (battle.getState() != Battle.BattleState.ACTIVE) return;
        
        java.util.UUID playerId = player.getUuid();
        if (!battle.isPlayerInGame(playerId)) return;
        
        BattleTeam team = battle.getPlayerTeam(playerId);
        if (team == null) return;
        
        // Add to spectators and eliminate from team
        team.eliminatePlayer(playerId);
        battle.getSpectators().add(playerId);
        
        // Immediately set to spectator mode
        player.changeGameMode(GameMode.SPECTATOR);
        
        // Heal the player to full health (since we prevented the death)
        player.setHealth(player.getMaxHealth());
        
        // No teleportation - let the player stay where they are as a spectator
        
        // Send elimination message
        player.sendMessage(Text.literal("You have been eliminated and are now spectating the battle.")
            .formatted(Formatting.GRAY), false);
        
        // Broadcast elimination to all battle players
        battle.broadcastToGamePlayers(Text.literal("[")
            .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
            .append(Text.literal("] " + player.getName().getString() + " has been eliminated!")));
        
        // Check if team is eliminated
        if (team.isEmpty()) {
            battle.broadcastToGamePlayers(Text.literal("Team ")
                .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                .append(Text.literal(" has been eliminated!")).formatted(Formatting.RED));
        }
        
        // Check for game end by calling public method
        battle.handlePlayerElimination();
    }
}
