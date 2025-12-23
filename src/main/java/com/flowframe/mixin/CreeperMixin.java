package com.flowframe.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Creeper.class)
public class CreeperMixin {
    
    /**
     * Prevents creeper explosions from destroying blocks by redirecting to NONE interaction mode.
     */
    @Redirect(
        method = "explodeCreeper()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"
        )
    )
    private void disableBlockDestruction(ServerLevel level, Entity entity, double x, double y, double z, float power, Level.ExplosionInteraction interaction) {
        // Use NONE interaction mode to prevent block destruction while keeping entity damage
        level.explode(entity, x, y, z, power, Level.ExplosionInteraction.NONE);
    }
}
