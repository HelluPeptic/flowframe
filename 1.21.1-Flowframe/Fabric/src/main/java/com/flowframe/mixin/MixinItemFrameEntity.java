package com.flowframe.mixin;

import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrameEntity.class)
public abstract class MixinItemFrameEntity {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void onInteract(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemFrameEntity self = (ItemFrameEntity)(Object)this;
        boolean isShift = player.isSneaking();
        boolean isInvisible = self.isInvisible();
        if (isShift) {
            if (!isInvisible) {
                // Make frame invisible, leave item visible
                self.setInvisible(true);
                if (!player.getWorld().isClient) {
                    player.getWorld().playSound(null, self.getBlockPos(), SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 0.7F, 1.2F);
                }
                cir.setReturnValue(ActionResult.SUCCESS);
            } else {
                // If already invisible, shift+right click reveals it
                self.setInvisible(false);
                if (!player.getWorld().isClient) {
                    player.getWorld().playSound(null, self.getBlockPos(), SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 0.7F, 0.8F);
                }
                cir.setReturnValue(ActionResult.SUCCESS);
            }
        } else if (isInvisible) {
            // Prevent rotation and item removal if invisible and not sneaking
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemFrameEntity self = (ItemFrameEntity)(Object)this;
        if (self.isInvisible()) {
            // Prevent breaking/removal if invisible
            cir.setReturnValue(false);
        }
    }
}
