package com.flowframe.mixin;

import com.flowframe.features.gungame.GunGame;
import com.flowframe.features.gungame.GunGameTeam;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class MixinPlayerManager_GunGameChat {
    
    @Inject(method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V", 
            at = @At("HEAD"), cancellable = true)
    private void onChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params, CallbackInfo ci) {
        GunGame game = GunGame.getInstance();
        
        // Only modify chat if player is in gun game
        if (!game.isPlayerInGame(sender.getUuid())) {
            return;
        }
        
        GunGameTeam team = game.getPlayerTeam(sender.getUuid());
        if (team == null) {
            return;
        }
        
        // Cancel the original broadcast
        ci.cancel();
        
        // Create custom message with team prefix
        Text customMessage = Text.literal("[")
            .append(Text.literal(team.getDisplayName()).formatted(team.getFormatting()))
            .append(Text.literal("] "))
            .append(Text.literal(sender.getName().getString()))
            .append(Text.literal(": "))
            .append(Text.literal(message.getSignedContent()));
        
        // Broadcast the custom message
        PlayerManager playerManager = (PlayerManager) (Object) this;
        playerManager.broadcast(customMessage, false);
    }
}
