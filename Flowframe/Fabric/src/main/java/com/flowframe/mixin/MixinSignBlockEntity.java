package com.flowframe.mixin;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.flowframe.features.minetracer.LogStorage;

@Mixin(SignBlockEntity.class)
public class MixinSignBlockEntity {
    @Inject(method = "setText", at = @At("HEAD"))
    private void flowframe$logSignEdit(int row, Text text, CallbackInfo ci) {
        SignBlockEntity sign = (SignBlockEntity)(Object)this;
        BlockPos pos = sign.getPos();
        if (sign.getWorld() instanceof ServerWorld) {
            // We don't have direct access to the player, so log as 'unknown' (can be improved with more context)
            LogStorage.logSignAction("edit", null, pos, text.getString(), sign.createNbt().toString());
        }
    }
}
