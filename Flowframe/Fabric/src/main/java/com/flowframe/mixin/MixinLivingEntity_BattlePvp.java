package com.flowframe.mixin;

import com.flowframe.features.gungame.Battle;
import com.flowframe.features.gungame.BattleTeam;
import com.flowframe.features.gungame.DeathTrackingUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.VillagerEntity;
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
        // Handle VillagerEntity damage in villager defense mode
        if ((Object)this instanceof VillagerEntity) {
            VillagerEntity villager = (VillagerEntity) (Object) this;
            
            // Check if there's an active battle with villager defense
            Battle activeBattle = Battle.getInstance();
            if (activeBattle != null && activeBattle.getBattleMode() == com.flowframe.features.gungame.BattleMode.VILLAGER_DEFENSE) {
                com.flowframe.features.gungame.VillagerDefenseManager villagerManager = activeBattle.getVillagerDefenseManager();
                if (villagerManager != null && villagerManager.shouldPreventVillagerDamage(villager)) {
                    // Cancel the damage during grace period
                    cir.setReturnValue(false);
                    return;
                }
                
                // If damage is allowed, notify the manager for tracking
                if (villagerManager != null && villagerManager.isTeamVillager(villager)) {
                    villagerManager.onVillagerDamaged(villager, source, amount);
                }
            }
            return; // Don't process further for villagers
        }
        
        // Only handle ServerPlayerEntity instances for battle PvP
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
                        
                        // Handle death immediately without scheduling to avoid lag
                        handleBattleDeath(targetPlayer, game);
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
                        
                        // Handle death immediately without scheduling to avoid lag
                        handleBattleDeath(targetPlayer, game);
                        return;
                    }
                } else {
                    // Prevent all non-player damage during non-active states
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }      private void handleBattleDeath(ServerPlayerEntity player, Battle battle) {
        if (battle.getState() != Battle.BattleState.ACTIVE) return;
        
        java.util.UUID playerId = player.getUuid();
        if (!battle.isPlayerInGame(playerId)) return;

        // CRITICAL: Check if player is already eliminated/spectator to prevent spam
        if (battle.isSpectator(playerId)) return;
        
        // CRITICAL: Check death cooldown to prevent rapid duplicate processing
        if (!DeathTrackingUtil.shouldProcessDeath(playerId)) {
            return; // Too soon since last death processing, ignore
        }

        BattleTeam team = battle.getPlayerTeam(playerId);
        if (team == null) return;
        
        // Handle differently for CTF vs Villager Defense vs Elimination modes
        if (battle.getBattleMode() == com.flowframe.features.gungame.BattleMode.CAPTURE_THE_FLAG) {
            handleCTFDeath(player, battle, team);
        } else if (battle.getBattleMode() == com.flowframe.features.gungame.BattleMode.VILLAGER_DEFENSE) {
            handleVillagerDefenseDeath(player, battle, team);
        } else {
            handleEliminationDeath(player, battle, team);
        }
    }
    
    private void handleCTFDeath(ServerPlayerEntity player, Battle battle, BattleTeam team) {
        java.util.UUID playerId = player.getUuid();
        
        // CRITICAL: Handle flag dropping FIRST before any other processing
        com.flowframe.features.gungame.CaptureTheFlagManager ctfManager = battle.getCTFManager();
        if (ctfManager != null) {
            // Force immediate flag drop to prevent flag duplication exploit
            ctfManager.handlePlayerLeave(player);
            // Then handle the CTF death with respawn delay
            ctfManager.handlePlayerDeath(player);
        }
        
        // CRITICAL: Refresh team color to prevent white nametag bug
        battle.refreshPlayerTeamColor(player, team);
        
        // Don't heal immediately - let the CTF manager handle respawn timing
        // The CTF manager will put player in spectator mode and respawn them after delay
    }
    
    private void handleVillagerDefenseDeath(ServerPlayerEntity player, Battle battle, BattleTeam team) {
        java.util.UUID playerId = player.getUuid();
        
        // Check if the team has a living villager
        com.flowframe.features.gungame.VillagerDefenseManager villagerManager = battle.getVillagerDefenseManager();
        if (villagerManager != null && villagerManager.hasLivingVillager(team.getName())) {
            // Team has a villager - respawn the player at villager base immediately
            BlockPos villagerBase = villagerManager.getVillagerBase(team.getName());
            if (villagerBase != null) {
                ServerWorld world = player.getServerWorld();
                player.teleport(world, villagerBase.getX() + 0.5, villagerBase.getY() + 1.0, villagerBase.getZ() + 0.5, 0.0f, 0.0f);
                player.changeGameMode(GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                
                player.sendMessage(Text.literal("You have respawned at your villager!").formatted(Formatting.GREEN), false);
                return; // Don't eliminate the player, they respawned
            }
        } else {
            // Team has no villager - player is eliminated permanently
            team.eliminatePlayer(playerId);
            battle.getSpectators().add(playerId);
            
            player.changeGameMode(GameMode.SPECTATOR);
            player.setHealth(player.getMaxHealth());
            
            // Send elimination message
            player.sendMessage(Text.literal("You have been eliminated! Your team's villager is dead so you cannot respawn.").formatted(Formatting.RED), false);
            
            // Broadcast elimination
            battle.broadcastToGamePlayers(Text.literal("Team ")
                .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
                .append(Text.literal(" player " + player.getName().getString() + " has been eliminated!")).formatted(Formatting.GRAY));
        }
        
        // CRITICAL: Refresh team color to prevent white nametag bug
        battle.refreshPlayerTeamColor(player, team);
    }
    
    private void handleEliminationDeath(ServerPlayerEntity player, Battle battle, BattleTeam team) {
        java.util.UUID playerId = player.getUuid();
        
        // Add to spectators and eliminate from team
        team.eliminatePlayer(playerId);
        battle.getSpectators().add(playerId);
        
        // Add to persistent spectators in case they disconnect
        com.flowframe.features.gungame.SpectatorPersistence.getInstance().addSpectator(playerId);
        
        // CRITICAL: Refresh team color to prevent white nametag bug before setting spectator mode
        battle.refreshPlayerTeamColor(player, team);
        
        // Immediately set to spectator mode
        player.changeGameMode(GameMode.SPECTATOR);
        
        // Heal the player to full health (since we prevented the death)
        player.setHealth(player.getMaxHealth());
        
        // Handle CTF cleanup if needed
        if (battle.getBattleMode() == com.flowframe.features.gungame.BattleMode.CAPTURE_THE_FLAG) {
            com.flowframe.features.gungame.CaptureTheFlagManager ctfManager = battle.getCTFManager();
            if (ctfManager != null) {
                ctfManager.handlePlayerElimination(playerId);
            }
        }
        
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
