package com.flowframe.features.chatformat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

public class ChatFormatFeature {
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // Set tablist header/footer for the joining player
            com.flowframe.features.chatformat.TablistUtil.updateTablistForPlayer(player, server);
            // Update tablist display names for all players (to apply prefixes)
            com.flowframe.features.chatformat.TablistUtil.updateTablistDisplayNamesForAll(server);
            Text msg = Text.literal(player.getName().getString())
                .styled(style -> style.withColor(TextColor.fromRgb(0x443e69)))
                .append(Text.literal(" joined the server.").styled(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            server.getPlayerManager().broadcast(msg, false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            Text msg = Text.literal(player.getName().getString())
                .styled(style -> style.withColor(TextColor.fromRgb(0x443e69)))
                .append(Text.literal(" left the server.").styled(style -> style.withColor(TextColor.fromRgb(0xAAAAAA))));
            server.getPlayerManager().broadcast(msg, false);
        });
    }
}
