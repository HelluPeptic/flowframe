package com.flowframe.flowframe.commands;

import com.flowframe.flowframe.Flowframe;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class EndToggleCommand implements CommandExecutor {

    private final Flowframe plugin;

    public EndToggleCommand(Flowframe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("flowframe.endtoggle")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        // Run on global region thread so config saves and broadcastMessage are thread-safe
        plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> {
            boolean nowEnabled = !plugin.isEndPortalEnabled();
            plugin.setEndPortalEnabled(nowEnabled);

            String state = nowEnabled
                    ? ChatColor.GREEN + "ENABLED"
                    : ChatColor.RED + "DISABLED";

            Bukkit.broadcastMessage(
                    ChatColor.GOLD + "[Flowframe] " + ChatColor.RESET
                    + "End portals are now " + state + ChatColor.RESET + "."
            );
        });

        return true;
    }
}
