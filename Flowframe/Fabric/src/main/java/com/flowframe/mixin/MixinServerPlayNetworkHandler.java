package com.flowframe.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    
    @Shadow
    public ServerPlayerEntity player;
    
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void onPlayerDisconnect(Text reason, CallbackInfo ci) {
        // Clean up scoreboard teams when player disconnects
        if (player != null) {
            // CRITICAL: Remove from all scoreboard teams to reset nametag color
            if (player.getServer() != null) {
                Scoreboard scoreboard = player.getServer().getScoreboard();
                Team playerTeam = scoreboard.getPlayerTeam(player.getEntityName());
                
                if (playerTeam != null) {
                    scoreboard.removePlayerFromTeam(player.getEntityName(), playerTeam);
                }
            }
        }
    }
    
    @Redirect(
        method = "onDisconnected",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"
        )
    )
    private void redirectBroadcastOnDisconnect(PlayerManager manager, Text message, boolean overlay) {
        // Do nothing - suppress vanilla leave message
    }
}

