package com.flowframe.mixin;

import com.flowframe.features.gungame.Battle;
import com.flowframe.features.gungame.BattleMode;
import com.flowframe.features.gungame.CaptureTheFlagManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class MixinServerPlayerEntity_CTFMovement {
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Battle battle = Battle.getInstance();
        
        // Only process if in CTF mode and battle is active
        if (battle.getBattleMode() != BattleMode.CAPTURE_THE_FLAG || 
            battle.getState() != Battle.BattleState.ACTIVE) {
            return;
        }
        
        CaptureTheFlagManager ctf = battle.getCTFManager();
        if (ctf == null || !battle.isPlayerInGame(player.getUuid())) {
            return;
        }
        
        // Check for automatic flag interactions every few ticks to avoid spam
        if (player.age % 20 == 0) { // Check once per second
            ctf.handlePlayerMovement(player);
        }
    }
}
