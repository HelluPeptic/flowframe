package com.flowframe.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.flowframe.flowframe.HeadRiding;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Picks where a rider lands when they get off a HeadRiding seat. Without this they'd
 * be dropped on top of the seat, above the head of the player who was carrying them.
 */
@Mixin(Interaction.class)
public abstract class InteractionMixin extends Entity {

    protected InteractionMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (HeadRiding.isSeat(this)) {
            return HeadRiding.findDismountLocation((Interaction) (Object) this, passenger);
        }
        return super.getDismountLocationForPassenger(passenger);
    }
}
