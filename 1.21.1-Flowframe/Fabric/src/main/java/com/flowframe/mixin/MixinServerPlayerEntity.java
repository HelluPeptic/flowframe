package com.flowframe.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.flowframe.features.keepinventory.KeepInventoryFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.LivingEntity;

@Mixin(ServerPlayerEntity.class)
public abstract class MixinServerPlayerEntity {
    private static final Map<UUID, List<ItemStack>> savedInventories = new HashMap<>();

    // Prevent inventory drop if per-player keep inventory is enabled
    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true)
    private void onDeath(DamageSource source, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        boolean keepInv = KeepInventoryFeature.shouldKeepInventory(player);
        boolean killedByOwnArrow = false;
        // Check if the source is a projectile from the player (self-inflicted bow
        // death)
        if (source.getSource() instanceof PersistentProjectileEntity projectile) {
            if (projectile.getOwner() == player) {
                killedByOwnArrow = true;
            }
        }
        if (killedByOwnArrow) {
            // Drop player head at death location
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            NbtCompound nbt = new NbtCompound();
            NbtCompound skullOwner = new NbtCompound();
            // Set both UUID and Name for correct skin
            UUID uuid = player.getGameProfile().getId();
            skullOwner.putUuid("Id", uuid);
            skullOwner.putString("Name", player.getGameProfile().getName());
            nbt.put("SkullOwner", skullOwner);
            // Use saveNbt instead of setNbt
            head.saveNbt(nbt);
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos pos = player.getBlockPos();
            world.spawnEntity(new net.minecraft.entity.ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, head));
            if (keepInv) {
                // Save inventory and prevent normal drops
                List<ItemStack> copy = new ArrayList<>();
                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    copy.add(stack.copy());
                }
                savedInventories.put(player.getUuid(), copy);
                ci.cancel();
                return;
            }
            // else: allow normal drops (head is already dropped)
        }
        if (keepInv && !killedByOwnArrow) {
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
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
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
