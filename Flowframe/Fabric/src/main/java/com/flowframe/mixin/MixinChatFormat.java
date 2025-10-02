package com.flowframe.mixin;

import com.flowframe.features.chatformat.GroupColorUtil;
import com.flowframe.features.chatformat.TablistUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class MixinChatFormat {
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        // DISABLED: Custom chat formatting
        /*
        net.minecraft.server.network.ServerPlayNetworkHandler handler = (net.minecraft.server.network.ServerPlayNetworkHandler) (Object) this;
        net.minecraft.server.network.ServerPlayerEntity player = handler.player;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server != null) {
            net.minecraft.text.Text formatted = net.minecraft.text.Text.literal(player.getName().getString())
                .styled(style -> style.withColor(com.flowframe.features.chatformat.GroupColorUtil.getPlayerGroupTextColor(player)))
                .append(net.minecraft.text.Text.literal(" » ").formatted(net.minecraft.util.Formatting.DARK_GRAY))
                .append(net.minecraft.text.Text.literal(packet.chatMessage()).formatted(net.minecraft.util.Formatting.WHITE));
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(formatted, false);
            }
            // Relay to Discord Integration if available
            try {
                Class<?> clazz = Class.forName("de.erdbeerbaerlp.dcintegration.architectury.DiscordIntegrationMod");
                for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals("onFlowframeChat") && m.getParameterCount() == 2) {
                        m.invoke(null, player, packet.chatMessage());
                        break;
                    }
                }
            } catch (Throwable ignored) {}
            // Update tablist for all players after chat (demonstration)
            com.flowframe.features.chatformat.TablistUtil.updateTablistForAll(server);
            ci.cancel();
        }
        */
    }
}