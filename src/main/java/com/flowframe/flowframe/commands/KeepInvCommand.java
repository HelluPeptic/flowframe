package com.flowframe.flowframe.commands;

import com.flowframe.flowframe.DataManager;
import com.flowframe.flowframe.Flowframe;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class KeepInvCommand implements CommandExecutor {

    private final Flowframe plugin;
    private final DataManager dataManager;

    public KeepInvCommand(Flowframe plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Schedule on the player's region thread (Folia requirement for player API calls)
        player.getScheduler().run(plugin, scheduledTask -> {
            boolean nowOptedOut = dataManager.toggle(player.getUniqueId());

            if (nowOptedOut) {
                player.sendMessage(
                        ChatColor.RED + "Keep Inventory: " + "OFF"
                );
            } else {
                player.sendMessage(
                        ChatColor.GREEN + "Keep Inventory: " + "ON"
                );
            }
        }, null);

        return true;
    }
}
