package com.flowframe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {
    
    /**
     * Prevents phantom spawning in the overworld.
     * Phantoms will now only spawn in the Nether with the same mechanics.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    public void onTick(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
        // If we're in the overworld, cancel phantom spawning
        if (level.dimension() == ServerLevel.OVERWORLD) {
            ci.cancel();
            return;
        }
        
        // If we're in the Nether, allow the phantom spawning logic to continue
        // The vanilla logic will handle spawning with the same mechanics
        if (level.dimension() == ServerLevel.NETHER) {
            // Let vanilla logic continue for Nether
            return;
        }
        
        // Cancel for other dimensions
        ci.cancel();
    }
}
