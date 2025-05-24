package com.flowframe.features.chatformat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.PlayerListHeaderS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.EnumSet;
import java.util.Collections;

public class TablistUtil {
    private static int tablistUpdateTick = 0;

    public static void updateTablistForAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateTablistForPlayer(player, server);
        }
    }

    public static void updateTablistForPlayer(ServerPlayerEntity player, MinecraftServer server) {
        // Get LuckPerms prefix
        String prefix = "";
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                CachedMetaData meta = user.getCachedData().getMetaData();
                String lpPrefix = meta.getPrefix();
                if (lpPrefix != null) {
                    prefix = lpPrefix.replace('&', '§');
                }
            }
        } catch (Throwable ignored) {}
        String displayName = prefix + player.getName().getString();
        Text tablistName = Text.literal(displayName);

        int online = server.getPlayerManager().getCurrentPlayerCount();
        // Add +1 to include yourself in the count if not already included
        // Vanilla getCurrentPlayerCount() does not include the player for whom the header is being set
        Text header = Text.literal("")
            .append(Text.literal("FlowSMP").styled(style -> style.withColor(Formatting.AQUA).withBold(true)))
            .append(Text.literal("\n" + player.getName().getString()).styled(style -> style.withColor(Formatting.WHITE)))
            .append(Text.literal("\nOnline players: " + online).styled(style -> style.withColor(Formatting.GRAY)))
            .append(Text.literal("\n "));
        int ping = player.pingMilliseconds;
        Text footer = Text.literal("\n ")
            .append(Text.literal("Ping: " + ping).styled(style -> style.withColor(Formatting.GOLD)));
        player.networkHandler.sendPacket(new PlayerListHeaderS2CPacket(header, footer));
        // Tablist display name update is not possible here without a mixin. Header/footer will still update correctly.
    }

    // Returns the custom tablist name (with prefix) for a player
    public static Text getTablistName(ServerPlayerEntity player) {
        String prefix = "";
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                CachedMetaData meta = user.getCachedData().getMetaData();
                String lpPrefix = meta.getPrefix();
                if (lpPrefix != null) {
                    prefix = lpPrefix.replace('&', '§');
                }
            }
        } catch (Throwable ignored) {}
        String displayName = prefix + player.getName().getString();
        return Text.literal(displayName);
    }

    public static void updateTablistDisplayNamesForAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
        }
    }

    public static void registerTablistAutoUpdate(MinecraftServer server) {
        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv != server) return;
            tablistUpdateTick++;
            if (tablistUpdateTick >= 100) { // 5 seconds at 20 TPS
                tablistUpdateTick = 0;
                updateTablistForAll(server);
            }
        });
    }
}
