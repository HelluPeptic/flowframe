package com.flowframe.mixin;

import com.flowframe.config.FlowframeConfig;
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
    private int flowframe$tickCounter = 0;

    @Unique
    private boolean flowframe$lastPathState = false;

    @Unique
    private int flowframe$ticksSinceOffPath = 0;

    /**
     * Applies speed boost when walking on path blocks - checks every 5 ticks
     */
    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        // Check every 5 ticks to reduce attribute system overhead
        flowframe$tickCounter++;
        if (flowframe$tickCounter >= 5) {
            flowframe$tickCounter = 0;
            checkAndUpdatePathBlockSpeed(player);
        }
    }

    @Unique
    private void checkAndUpdatePathBlockSpeed(ServerPlayer player) {
        // Check if standing on a path block (check current position and 1 block below)
        BlockPos playerPos = player.blockPosition();
        boolean isOnPathBlock = player.level().getBlockState(playerPos).is(Blocks.DIRT_PATH) ||
                                player.level().getBlockState(playerPos.below()).is(Blocks.DIRT_PATH);

        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) {
            return;
        }

        if (isOnPathBlock) {
            // Reset the off-path timer when on a path block
            flowframe$ticksSinceOffPath = 0;
            
            // Add speed boost if not already present
            if (!flowframe$lastPathState) {
                movementSpeed.removeModifier(PATH_SPEED_MODIFIER_ID);
                double multiplier = FlowframeConfig.getPathBlockSpeedMultiplier();
                AttributeModifier modifier = new AttributeModifier(
                        PATH_SPEED_MODIFIER_ID,
                        multiplier - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );
                movementSpeed.addPermanentModifier(modifier);
                flowframe$lastPathState = true;
            }
        } else {
            // Not on path block - start/continue grace period
            if (flowframe$lastPathState) {
                flowframe$ticksSinceOffPath++;
                
                // Remove speed boost after 1 second (4 checks * 5 ticks = 20 ticks)
                if (flowframe$ticksSinceOffPath >= 4) {
                    movementSpeed.removeModifier(PATH_SPEED_MODIFIER_ID);
                    flowframe$lastPathState = false;
                    flowframe$ticksSinceOffPath = 0;
                }
            }
        }
    }
}
