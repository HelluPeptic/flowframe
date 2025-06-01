package com.flowframe.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.explosion.Explosion;
import java.util.List;

@Mixin(CreeperEntity.class)
public abstract class MixinCreeperEntity {
}
