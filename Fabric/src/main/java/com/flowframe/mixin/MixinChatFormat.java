package com.flowframe.mixin;

import com.flowframe.features.chatformat.GroupColorUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinChatFormat {
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        ServerPlayNetworkHandler handler = (ServerPlayNetworkHandler) (Object) this;
        ServerPlayerEntity player = handler.player;
        MinecraftServer server = player.getServer();
        if (server != null) {
            // Use group color for player name
            Text formatted = Text.literal(player.getName().getString())
                .styled(style -> style.withColor(GroupColorUtil.getPlayerGroupTextColor(player)))
                .append(Text.literal(" » ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(packet.chatMessage()).formatted(Formatting.WHITE));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(formatted, false);
            }
            ci.cancel();
        }
    }
}
