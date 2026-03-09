package com.flowframe.util;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public class SelfKillHeadDrop {

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) return;

            Entity attacker = damageSource.getEntity();
            Entity directEntity = damageSource.getDirectEntity();

            // Must be killed by themselves via a projectile they fired (arrow, trident, etc.)
            if (attacker != player) return;
            if (!(directEntity instanceof Projectile)) return;

            // Build a player head carrying the dead player's skin
            ItemStack headStack = new ItemStack(Items.PLAYER_HEAD);
            headStack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));

            // Spawn the head at the player's death location
            ItemEntity itemEntity = new ItemEntity(
                player.level(),
                player.getX(), player.getY(), player.getZ(),
                headStack
            );
            player.level().addFreshEntity(itemEntity);
        });
    }
}
