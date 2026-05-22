package com.flowframe.flowframe;

import com.flowframe.flowframe.commands.EndToggleCommand;
import com.flowframe.flowframe.commands.KeepInvCommand;
import com.flowframe.flowframe.listeners.EndPortalListener;
import com.flowframe.flowframe.listeners.GriefListener;
import com.flowframe.flowframe.listeners.PhantomSpawnListener;
import com.flowframe.flowframe.listeners.PlayerDeathListener;
import com.flowframe.flowframe.listeners.RightClickHarvestListener;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Flowframe extends JavaPlugin {

    private DataManager dataManager;

    // Volatile so reads/writes from any Folia region thread see the latest value.
    private volatile boolean endPortalEnabled = false;
    private ScheduledTask endPortalWatchTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        endPortalEnabled = getConfig().getBoolean("end-portal-enabled", false);

        dataManager = new DataManager(this);

        getServer().getPluginManager().registerEvents(new GriefListener(), this);
        getServer().getPluginManager().registerEvents(new PhantomSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new RightClickHarvestListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(dataManager), this);
        getServer().getPluginManager().registerEvents(new EndPortalListener(this), this);

        getCommand("keepinv").setExecutor(new KeepInvCommand(this, dataManager));
        getCommand("endtoggle").setExecutor(new EndToggleCommand(this));

        // Folia's async portal path (findOrCreatePortalAsync) bypasses all Bukkit portal
        // events, so we cannot rely on PlayerTeleportEvent / PlayerPortalEvent to block
        // end portal entry. Instead, we run a repeating task that ejects any player who
        // ends up in The End while the portal is disabled.
        endPortalWatchTask = getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            if (endPortalEnabled) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Must schedule onto the player's own region thread to safely access
                // their world and call teleportAsync.
                player.getScheduler().run(this, pt -> {
                    if (player.getWorld().getEnvironment() != World.Environment.THE_END) return;
                    World overworld = getServer().getWorlds().stream()
                            .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                            .findFirst()
                            .orElse(null);
                    if (overworld == null) return;
                    // Prefer bed / respawn anchor over world spawn.
                    Location returnLoc = player.getRespawnLocation();
                    if (returnLoc == null || returnLoc.getWorld() == null
                            || returnLoc.getWorld().getEnvironment() != World.Environment.NORMAL) {
                        returnLoc = overworld.getSpawnLocation();
                    }
                    player.sendMessage(ChatColor.RED + "The End is currently disabled.");
                    player.teleportAsync(returnLoc);
                }, null);
            }
        }, 1L, 5L); // initial delay 1 tick, repeat every 5 ticks (0.25 s)

        getLogger().info("Flowframe enabled.");
    }

    @Override
    public void onDisable() {
        if (endPortalWatchTask != null) {
            endPortalWatchTask.cancel();
        }
        getLogger().info("Flowframe disabled.");
    }

    public boolean isEndPortalEnabled() {
        return endPortalEnabled;
    }

    public void setEndPortalEnabled(boolean enabled) {
        endPortalEnabled = enabled;
        getConfig().set("end-portal-enabled", enabled);
        saveConfig();
    }
}
