package com.flowframe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(SpawnEggItem.class)
public class MixinMobSpawnerBlock_NoSurvivalChange {
    
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void preventSurvivalSpawnerChange(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        
        // Check if the block being clicked is a spawner
        if (world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
            // Check if player is in survival mode
            if (player != null && !player.isCreative() && !player.isSpectator()) {
                // Cancel the interaction completely
                cir.setReturnValue(ActionResult.FAIL);
                
                // Send message to player on server side
                if (!world.isClient) {
                    player.sendMessage(Text.literal("§c[FLOWFRAME] You cannot change spawners with spawn eggs in survival mode!"), true);
                }
                return;
            }
        }
    }
}
