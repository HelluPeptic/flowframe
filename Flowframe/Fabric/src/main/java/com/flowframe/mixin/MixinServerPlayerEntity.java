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
        
        // Note: Battle deaths are now handled in MixinLivingEntity_BattlePvp to prevent death screen
        // This method only handles keep inventory and player head drops
        
        // Continue with keep inventory logic
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
            head.setNbt(nbt);
            ServerWorld worldObj = (ServerWorld) player.getWorld();
            BlockPos posObj = player.getBlockPos();
            worldObj.spawnEntity(new net.minecraft.entity.ItemEntity(worldObj, posObj.getX() + 0.5, posObj.getY() + 0.5,
                    posObj.getZ() + 0.5, head));
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
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onPlayerTick(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        
        // CTF movement handling
        try {
            com.flowframe.features.gungame.Battle battle = com.flowframe.features.gungame.Battle.getInstance();
            
            // Only process if in CTF mode and battle is active
            if (battle.getBattleMode() == com.flowframe.features.gungame.BattleMode.CAPTURE_THE_FLAG && 
                battle.getState() == com.flowframe.features.gungame.Battle.BattleState.ACTIVE) {
                
                com.flowframe.features.gungame.CaptureTheFlagManager ctf = battle.getCTFManager();
                if (ctf != null && battle.isPlayerInGame(player.getUuid())) {
                    // Check for automatic flag interactions every 10ms (every 2 ticks at 20 TPS)
                    if (player.age % 2 == 0) { // Check 10 times per second for responsiveness
                        ctf.handlePlayerMovement(player);
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore errors to prevent issues with other features
        }
    }
}
