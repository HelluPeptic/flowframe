package com.flowframe.features.keepinventory;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.ArrayList;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity {
    private static final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

    // Prevent inventory drop if per-player keep inventory is enabled
    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void onDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (KeepInventoryFeature.shouldKeepInventory(player)) {
            // Save inventory
            List<ItemStack> copy = new ArrayList<>();
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                copy.add(stack.copy());
            }
            savedInventories.put(player.getUuid(), copy);
            ci.cancel();
        }
    }

    @Inject(method = "copyFrom", at = @At("TAIL"))
    private void onCopyFrom(ServerPlayerEntity oldPlayer, boolean alive, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (KeepInventoryFeature.shouldKeepInventory(player)) {
            List<ItemStack> saved = savedInventories.remove(player.getUuid());
            if (saved != null) {
                for (int i = 0; i < saved.size(); i++) {
                    player.getInventory().setStack(i, saved.get(i));
                }
            }
        }
    }
}
