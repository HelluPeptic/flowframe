package com.flowframe.flowframe.listeners;

import com.flowframe.flowframe.Flowframe;
import org.bukkit.ChatColor;
import org.bukkit.PortalType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;

/**
 * Blocks end portal entry when the portal is disabled.
 *
 * EntityPortalEnterEvent is Cancellable in Folia 26.1.2 and fires inside
 * EndPortalBlock#entityInside() BEFORE findOrCreatePortalAsync() is called.
 * Cancelling it prevents any further portal processing for that tick.
 *
 * The watch-task in Flowframe is kept as a safety net for edge cases (e.g.
 * a player riding a vehicle into the portal).
 */
public class EndPortalListener implements Listener {

    private final Flowframe plugin;

    public EndPortalListener(Flowframe plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (plugin.isEndPortalEnabled()) return;
        if (event.getPortalType() != PortalType.ENDER) return;
        if (!(event.getEntity() instanceof Player player)) return;

        event.setCancelled(true);
    }
}
