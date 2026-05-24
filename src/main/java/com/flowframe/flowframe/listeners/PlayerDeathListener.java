package com.flowframe.flowframe.listeners;

import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.projectiles.ProjectileSource;

import com.flowframe.flowframe.DataManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerDeathListener implements Listener {

    private final DataManager dataManager;

    public PlayerDeathListener(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Gray death message
        Component deathMsg = event.deathMessage();
        if (deathMsg != null) {
            event.deathMessage(
                    Component.empty().color(NamedTextColor.GRAY).append(deathMsg)
            );
        }

        Player player = event.getEntity();
        boolean selfKill = isSelfProjectileKill(player);
        boolean optedOut = dataManager.isOptedOut(player.getUniqueId());

        // --- Opt-out keep inventory ---
        // Must be handled before the head drop so the head ends up in the drop list too.
        if (optedOut) {
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            // gamerule keepInventory=true means the server never put items in getDrops().
            // We manually populate the drop list so they scatter on the ground.
            PlayerInventory inv = player.getInventory();

            Arrays.stream(inv.getStorageContents())
                    .filter(i -> i != null && i.getType() != Material.AIR)
                    .forEach(event.getDrops()::add);

            Arrays.stream(inv.getArmorContents())
                    .filter(i -> i != null && i.getType() != Material.AIR)
                    .forEach(event.getDrops()::add);

            ItemStack offhand = inv.getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) {
                event.getDrops().add(offhand);
            }

            // Vanilla XP drop: min(7 * level, 100)
            event.setDroppedExp(Math.min(100, 7 * player.getLevel()));
        }

        // --- Player head on self-projectile kill ---
        // Always drops even with keep inventory enabled, and even if the player is opted out.
        if (selfKill) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(player);
                head.setItemMeta(meta);
            }
            // Adding to getDrops() guarantees the item lands in the world regardless
            // of the keepInventory setting.
            event.getDrops().add(head);
        }
    }

    /**
     * Returns true if the player's last damage source was a Projectile shot by themselves.
     * Covers arrows (bow/crossbow), spectral arrows, and tridents.
     */
    private boolean isSelfProjectileKill(Player player) {
        EntityDamageEvent cause = player.getLastDamageCause();
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) return false;

        Entity damager = byEntity.getDamager();
        if (!(damager instanceof Projectile projectile)) return false;

        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player shooterPlayer
                && shooterPlayer.getUniqueId().equals(player.getUniqueId());
    }
}
