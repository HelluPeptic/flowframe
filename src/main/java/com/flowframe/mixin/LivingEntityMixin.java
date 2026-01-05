package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
import com.flowframe.util.PathBlockSpeedTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique
    private static final Identifier PATH_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("flowframe", "path_speed");

    @Unique
    private boolean flowframe$wasOnPathBlock = false;

    @Unique
    private boolean flowframe$hasModifier = false;

    /**
     * Applies speed boost when walking on path blocks with a 1-second grace
     * period
     */
    @Inject(method = "tick", at = @At("HEAD"))
    public void onTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        // Check if standing on a path block
        BlockPos posBelow = player.blockPosition().below();
        BlockState blockBelow = player.level().getBlockState(posBelow);
        boolean isOnPathBlock = blockBelow.is(Blocks.DIRT_PATH);

        if (isOnPathBlock) {
            PathBlockSpeedTracker.updatePathBlockTime(player.getUUID());
            flowframe$wasOnPathBlock = true;
        }

        // Check if we should apply speed boost (on path block or within grace period)
        boolean shouldApplyBoost = isOnPathBlock || PathBlockSpeedTracker.shouldApplySpeedBoost(player.getUUID());

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            if (shouldApplyBoost) {
                if (!flowframe$hasModifier) {
                    // Add the speed boost
                    double multiplier = FlowframeConfig.getPathBlockSpeedMultiplier();
                    AttributeModifier modifier = new AttributeModifier(
                            PATH_SPEED_MODIFIER_ID,
                            multiplier - 1.0, // Subtract 1 because it's additive
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    );
                    movementSpeed.addTransientModifier(modifier);
                    flowframe$hasModifier = true;
                }
            } else {
                // Remove the speed boost if it exists
                if (flowframe$hasModifier) {
                    movementSpeed.removeModifier(PATH_SPEED_MODIFIER_ID);
                    flowframe$hasModifier = false;
                }

                if (flowframe$wasOnPathBlock) {
                    flowframe$wasOnPathBlock = false;
                }
            }
        }
    }
}
