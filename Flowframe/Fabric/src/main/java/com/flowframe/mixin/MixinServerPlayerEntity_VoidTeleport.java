package com.flowframe.mixin;

import com.flowframe.features.gungame.Battle;
import com.flowframe.features.gungame.BattleSettings;
import com.flowframe.features.gungame.BattleTeam;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class MixinServerPlayerEntity_VoidTeleport {
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTickVoidCheck(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Battle battle = Battle.getInstance();
        
        // Only process if battle is active and player is in game
        if (battle.getState() != Battle.BattleState.ACTIVE || 
            !battle.isPlayerInGame(player.getUuid())) {
            return;
        }
        
        BattleSettings settings = BattleSettings.getInstance();
        double voidLevel = settings.getVoidLevel();
        
        // Check if player has fallen below void level
        if (player.getY() < voidLevel) {
            teleportPlayerToBase(player, battle);
        }
    }
    
    private void teleportPlayerToBase(ServerPlayerEntity player, Battle battle) {
        BattleTeam team = battle.getPlayerTeam(player.getUuid());
        if (team != null) {
            // Get the appropriate base location based on game mode and team
            BlockPos baseLocation = battle.getTeamBase(team.getName());
            if (baseLocation == null) {
                // Fallback to game spawn point if team base not set
                baseLocation = battle.getGameSpawnPoint();
            }
            
            if (baseLocation != null) {
                // Teleport player to the base location
                player.teleport(baseLocation.getX() + 0.5, baseLocation.getY() + 1, baseLocation.getZ() + 0.5);
                
                // Send message to player
                player.sendMessage(Text.literal("You fell into the void and were teleported back to your base!")
                    .formatted(Formatting.YELLOW), false);
                
                // Reset fall damage and velocity
                player.fallDistance = 0.0f;
                player.setVelocity(0, 0, 0);
            }
        }
    }
}
