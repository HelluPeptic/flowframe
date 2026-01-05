package com.flowframe.mixin;

import com.flowframe.features.chatformat.TablistUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity_TablistName {
    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void injectTablistName(CallbackInfoReturnable<Text> cir) {
        Text custom = TablistUtil.getTablistName((ServerPlayerEntity)(Object)this);
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
