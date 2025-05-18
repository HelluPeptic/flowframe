package com.flowframe.features.chatformat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;

public class TablistUtil {
    public static void updateTablistForAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateTablistForPlayer(player, server);
        }
    }

    public static void updateTablistForPlayer(ServerPlayerEntity player, MinecraftServer server) {
        Text header = Text.literal("")
            .append(Text.literal("FlowSMP").styled(style -> style.withColor(Formatting.AQUA).withBold(true)))
            .append(Text.literal("\n" + player.getName().getString()).styled(style -> style.withColor(Formatting.WHITE)))
            .append(Text.literal("\nOnline players: " + server.getCurrentPlayerCount()).styled(style -> style.withColor(Formatting.GRAY)))
            .append(Text.literal("\n ")); // Add a blank line after header
        int ping = player.pingMilliseconds;
        Text footer = Text.literal("\n ") // Add a blank line before footer
            .append(Text.literal("Ping: " + ping).styled(style -> style.withColor(Formatting.GOLD)));
        player.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(header, footer));
        player.networkHandler.sendPacket(
            new PlayerListS2CPacket(
                java.util.EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME),
                java.util.Collections.singletonList(player)
            )
        );
    }
}
