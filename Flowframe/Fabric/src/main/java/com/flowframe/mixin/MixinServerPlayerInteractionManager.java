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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.block.BlockState;
import com.flowframe.mixin.ServerPlayerInteractionManagerAccessor;

@Mixin(ServerPlayerInteractionManager.class)
public class MixinServerPlayerInteractionManager {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void flowframe$logBlockPlace(BlockPos pos, BlockState state, CallbackInfo ci) {
        ServerPlayerInteractionManager self = (ServerPlayerInteractionManager)(Object)this;
        ServerPlayerEntity player = ((ServerPlayerInteractionManagerAccessor)self).getPlayer();
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        BlockEntity blockEntity = player.getWorld().getBlockEntity(pos);
        String nbt = blockEntity != null ? blockEntity.createNbt().toString() : null;
        LogStorage.logBlockAction("place", player, pos, blockId.toString(), nbt);
        if (blockEntity instanceof SignBlockEntity sign) {
            LogStorage.logSignAction("place", player, pos, sign.getFrontText().getMessage(0, false).getString(), sign.createNbt().toString());
        }
    }
}
