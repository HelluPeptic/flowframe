package com.flowframe.mixin;

import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityRidingMixin {
    
    @Shadow
    private net.minecraft.world.level.Level level;
    
    // Allow players to ride other players by intercepting the canSerialize check
    @Redirect(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
    )
    private boolean allowPlayerRiding(EntityType instance) {
        if (instance == EntityType.PLAYER) {
            return true;
        } else {
            return instance.canSerialize();
        }
    }
    
    // Send sync packet when passenger is removed to fix visual desync
    @Inject(method = "removePassenger", at = @At("TAIL"))
    private void onRemovePassenger(Entity passenger, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (!this.level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetPassengersPacket(entity));
        }
    }
}