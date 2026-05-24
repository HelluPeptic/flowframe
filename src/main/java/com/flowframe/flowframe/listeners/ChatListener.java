package com.flowframe.flowframe.listeners;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;

/**
 * Formats chat messages as:  [prefix] coloredName » message
 *
 * Player name colour comes from the LuckPerms group meta key "chat-color".
 * Set it on a group with:  /lp group <group> meta set chat-color <minecraft-colour-name>
 * e.g. /lp group admin meta set chat-color red
 *
 * Compatible with plugins that inject a chat prefix at NORMAL priority
 * (e.g. TownCore's [TownName] tag): this handler runs at HIGHEST, extracts
 * the prefix by calling the upstream renderer with empty sentinels, then
 * rebuilds the full line with our own " » " separator and white message.
 */
public class ChatListener implements Listener {

    public ChatListener() {}

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        TextColor groupColor = resolveGroupColor(player);
        ChatRenderer upstream = event.renderer();
        boolean hasCustomRenderer = upstream != ChatRenderer.defaultRenderer();

        event.renderer((source, displayName, message, viewer) -> {
            Component coloredName = Component.text(source.getName()).color(groupColor);
            Component whiteMessage = message.colorIfAbsent(NamedTextColor.WHITE);
            Component arrow = Component.text(" \u00bb ", NamedTextColor.DARK_GRAY);

            TextComponent.Builder builder = Component.text();

            if (hasCustomRenderer) {
                // Extract the upstream prefix (e.g. TownCore's hoverable [TownName] tag)
                // by rendering with empty sentinel values.  The upstream renderer builds:
                //   prefix + displayName + separator + message
                // With empty displayName and empty message the result's first child is
                // the raw prefix component, which we prepend verbatim.
                Component sentinel = upstream.render(source, Component.empty(), Component.empty(), viewer);
                List<Component> parts = sentinel.children();
                if (!parts.isEmpty()) {
                    builder.append(parts.get(0)); // e.g. the [TownName] tag
                }
            }

            return builder
                    .append(coloredName)
                    .append(arrow)
                    .append(whiteMessage)
                    .build();
        });
    }

    /**
     * Looks up the LuckPerms "chat-color" meta value from the player's primary
     * group and converts it to a TextColor.  Falls back to white if LuckPerms
     * is absent or the meta key is not set.
     *
     * Set the color on a group with:
     *   /lp group <group> meta set chat-color <minecraft-colour-name>
     * e.g. /lp group admin meta set chat-color red
     */
    private TextColor resolveGroupColor(Player player) {
        RegisteredServiceProvider<LuckPerms> rsp =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (rsp == null) return NamedTextColor.WHITE;

        LuckPerms lp = rsp.getProvider();
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return NamedTextColor.WHITE;

        Group group = lp.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group == null) return NamedTextColor.WHITE;

        String colorName = group.getNodes(NodeType.META).stream()
                .filter(n -> n.getMetaKey().equals("chat-color"))
                .map(MetaNode::getMetaValue)
                .findFirst()
                .orElse(null);
        if (colorName == null) return NamedTextColor.WHITE;

        NamedTextColor named = NamedTextColor.NAMES.value(colorName.toLowerCase());
        return named != null ? named : NamedTextColor.WHITE;
    }
}
