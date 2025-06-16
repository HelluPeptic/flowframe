package com.flowframe.mixin;

import com.flowframe.playerflags.PlayerEntityStewFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({PlayerEntity.class, ServerPlayerEntity.class})
public abstract class MixinPlayerEntity_StewFlag implements PlayerEntityStewFlag {
    @Unique
    private boolean flowframe$justAteSuspiciousStew = false;

    @Override
    public boolean flowframe$justAteSuspiciousStew() {
        return flowframe$justAteSuspiciousStew;
    }

    @Override
    public void flowframe$setJustAteSuspiciousStew(boolean value) {
        flowframe$justAteSuspiciousStew = value;
    }
}
