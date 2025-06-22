package com.flowframe.mixin;

import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.flowframe.features.minetracer.LogStorage;
import java.util.List;

@Mixin(SignBlockEntity.class)
public class MixinSignBlockEntity {
    @Inject(
        method = "tryChangeText",
        at = @At("TAIL")
    )
    private void flowframe$logSignEdit(PlayerEntity player, boolean front, List messages, CallbackInfo ci) {
        SignBlockEntity sign = (SignBlockEntity)(Object)this;
        BlockPos pos = sign.getPos();
        if (sign.getWorld() instanceof ServerWorld serverWorld) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                Object msg = messages.get(i);
                sb.append(msg instanceof Text ? ((Text)msg).getString() : msg.toString());
                if (i < messages.size() - 1) sb.append("\n");
            }
            LogStorage.logSignAction("edit", player, pos, sb.toString().trim(), sign.createNbt().toString());
        }
    }
}
