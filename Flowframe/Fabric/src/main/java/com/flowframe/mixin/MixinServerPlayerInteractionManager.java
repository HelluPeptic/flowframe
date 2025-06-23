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
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import com.flowframe.mixin.ServerPlayerInteractionManagerAccessor;
import com.google.gson.Gson;

@Mixin(ServerPlayerInteractionManager.class)
public class MixinServerPlayerInteractionManager {
    private static final Gson GSON = new Gson();

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void flowframe$cacheBlockPlaceState(ServerPlayerEntity player, net.minecraft.world.World world, net.minecraft.item.ItemStack stack, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        // Store the block state before interaction
        BlockPos placedPos = hitResult.getBlockPos().offset(hitResult.getSide());
        this.flowframe$prevPlacedState = world.getBlockState(placedPos);
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void flowframe$logBlockPlace(ServerPlayerEntity player, net.minecraft.world.World world, net.minecraft.item.ItemStack stack, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult, CallbackInfoReturnable<net.minecraft.util.ActionResult> cir) {
        BlockPos placedPos = hitResult.getBlockPos().offset(hitResult.getSide());
        BlockState placedState = world.getBlockState(placedPos);
        BlockState prevState = this.flowframe$prevPlacedState;
        this.flowframe$prevPlacedState = null;
        // Only log if the block actually changed (was air or different block before, now not air and different)
        if (placedState.isAir() || (prevState != null && placedState.getBlock() == prevState.getBlock())) return;
        Identifier blockId = Registries.BLOCK.getId(placedState.getBlock());
        net.minecraft.block.entity.BlockEntity blockEntity = world.getBlockEntity(placedPos);
        String nbt = blockEntity != null ? blockEntity.createNbt().toString() : null;
        LogStorage.logBlockAction("place", player, placedPos, blockId.toString(), nbt);
        if (blockEntity instanceof net.minecraft.block.entity.SignBlockEntity) {
            net.minecraft.block.entity.SignBlockEntity sign = (net.minecraft.block.entity.SignBlockEntity) blockEntity;
            // Extract all visible lines as plain text
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) {
                try {
                    lines[i] = sign.getFrontText().getMessage(i, false).getString();
                } catch (Exception e) {
                    lines[i] = "";
                }
            }
            String beforeText = GSON.toJson(lines);
            LogStorage.logSignAction("place", player, placedPos, beforeText, sign.createNbt().toString());
        }
    }
    @org.spongepowered.asm.mixin.Unique
    private BlockState flowframe$prevPlacedState = null;

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
        if (blockEntity instanceof SignBlockEntity) {
            SignBlockEntity sign = (SignBlockEntity) blockEntity;
            // Extract all visible lines as plain text
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) {
                try {
                    lines[i] = sign.getFrontText().getMessage(i, false).getString();
                } catch (Exception e) {
                    lines[i] = "";
                }
            }
            String beforeText = GSON.toJson(lines);
            LogStorage.logSignAction("broke", player, pos, beforeText, sign.createNbt().toString());
        }
    }
}
