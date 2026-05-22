package com.flowframe.flowframe.listeners;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class PhantomSpawnListener implements Listener {

    /**
     * Cancels phantom spawns in any world that is not the End environment.
     * Phantoms are only allowed to naturally spawn in The End.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhantomSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.PHANTOM) {
            return;
        }

        World world = event.getEntity().getWorld();
        if (world.getEnvironment() != World.Environment.THE_END) {
            event.setCancelled(true);
        }
    }
}
