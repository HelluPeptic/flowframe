package com.flowframe.mixin;

import com.flowframe.features.minetracer.LogStorage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.block.BlockState;
import com.flowframe.mixin.ServerPlayerInteractionManagerAccessor;

@Mixin(ServerPlayerInteractionManager.class)
public class MixinServerPlayerInteractionManager {
    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void flowframe$logBlockPlace(ServerPlayerEntity player, net.minecraft.world.World world, net.minecraft.item.ItemStack stack, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        // The position where the new block will be placed
        BlockPos placedPos = hitResult.getBlockPos().offset(hitResult.getSide());
        BlockState placedState = world.getBlockState(placedPos);
        if (placedState.isAir()) return; // Do not log if the placed block is air
        Identifier blockId = Registries.BLOCK.getId(placedState.getBlock());
        net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(placedPos);
        String nbt = blockEntity != null ? blockEntity.createNbt().toString() : null;
        LogStorage.logBlockAction("place", player, placedPos, blockId.toString(), nbt);
        if (blockEntity instanceof net.minecraft.block.entity.SignBlockEntity sign) {
            LogStorage.logSignAction("place", player, placedPos, sign.getFrontText().getMessage(0, false).getString(), sign.createNbt().toString());
        }
    }

    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void flowframe$logBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = ((ServerPlayerInteractionManagerAccessor)this).getPlayer();
        net.minecraft.world.World world = player.getWorld();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        BlockEntity blockEntity = world.getBlockEntity(pos);
        String nbt = blockEntity != null ? blockEntity.createNbt().toString() : null;
        LogStorage.logBlockAction("broke", player, pos, blockId.toString(), nbt);
        if (blockEntity instanceof SignBlockEntity sign) {
            LogStorage.logSignAction("broke", player, pos, sign.getFrontText().getMessage(0, false).getString(), sign.createNbt().toString());
        }
    }
}
