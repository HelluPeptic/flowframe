package com.flowframe.flowframe.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Custom join, leave, and first-join messages.
 *
 * Join  : <player> joined the server          (player name in #433d68)
 * Leave : <player> left the server            (player name in #433d68)
 * First : <player> joined for the first time! (gold, full line)
 */
public class ConnectionListener implements Listener {

    private static final TextColor NAME_COLOR = TextColor.fromHexString("#433d68");

    public ConnectionListener() {}

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPlayedBefore()) {
            // First-ever join
            event.joinMessage(
                    Component.text(player.getName() + " joined the server for the first time! Welcome them!",
                            NamedTextColor.GOLD)
            );
        } else {
            event.joinMessage(
                    Component.text()
                            .append(Component.text(player.getName(), NAME_COLOR))
                            .append(Component.text(" joined the server", NamedTextColor.GRAY))
                            .build()
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(
                Component.text()
                        .append(Component.text(player.getName(), NAME_COLOR))
                        .append(Component.text(" left the server", NamedTextColor.GRAY))
                        .build()
        );
    }
}
