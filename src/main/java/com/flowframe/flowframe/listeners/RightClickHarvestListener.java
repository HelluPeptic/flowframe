package com.flowframe.flowframe.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class RightClickHarvestListener implements Listener {

    // Maps each crop material to its seed material for replanting
    private static final Map<Material, Material> CROP_SEEDS = new HashMap<>();

    static {
        CROP_SEEDS.put(Material.WHEAT,       Material.WHEAT_SEEDS);
        CROP_SEEDS.put(Material.POTATOES,    Material.POTATO);
        CROP_SEEDS.put(Material.CARROTS,     Material.CARROT);
        CROP_SEEDS.put(Material.BEETROOTS,   Material.BEETROOT_SEEDS);
        CROP_SEEDS.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_SEEDS.put(Material.PITCHER_CROP, Material.PITCHER_POD);
        CROP_SEEDS.put(Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickCrop(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();
        if (!CROP_SEEDS.containsKey(type)) {
            return;
        }

        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return;
        }

        // Only harvest fully grown crops
        if (ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        // Prevent item-in-hand interaction from firing (e.g. placing a block)
        event.setCancelled(true);

        Player player = event.getPlayer();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        World world = block.getWorld();

        // Drop the crop's loot naturally
        Collection<ItemStack> drops = block.getDrops();

        // Remove one seed from drops so we can replant without consuming inventory
        Material seedMaterial = CROP_SEEDS.get(type);
        boolean seedRemoved = false;
        for (ItemStack drop : drops) {
            if (!seedRemoved && drop.getType() == seedMaterial) {
                if (drop.getAmount() > 1) {
                    drop.setAmount(drop.getAmount() - 1);
                } else {
                    drops.remove(drop);
                }
                seedRemoved = true;
                break;
            }
        }

        // Replant the crop at age 0
        ageable.setAge(0);
        block.setBlockData(ageable);

        // Spawn the drops into the world
        for (ItemStack drop : drops) {
            world.dropItemNaturally(loc, drop);
        }
    }
}
