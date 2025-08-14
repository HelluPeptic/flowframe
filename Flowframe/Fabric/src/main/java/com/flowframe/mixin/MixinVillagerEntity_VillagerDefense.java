package com.flowframe.mixin;

import com.flowframe.features.gungame.Battle;
import com.flowframe.features.gungame.BattleMode;
import com.flowframe.features.gungame.VillagerDefenseManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public abstract class MixinVillagerEntity_VillagerDefense {
    
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onVillagerDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        try {
            VillagerEntity villager = (VillagerEntity) (Object) this;
            Battle battle = Battle.getInstance();
            
            // Defensive checks to prevent crashes
            if (battle == null || battle.getBattleMode() == null) {
                return;
            }
            
            // Only handle this if we're in a Villager Defense battle
            if (battle.getBattleMode() != BattleMode.VILLAGER_DEFENSE) {
                return;
            }
            
            VillagerDefenseManager manager = battle.getVillagerDefenseManager();
            if (manager == null) {
                return;
            }
            
            // Check if damage should be prevented (during grace period)
            if (manager.shouldPreventVillagerDamage(villager)) {
                cir.setReturnValue(false); // Cancel the damage
                return;
            }
        } catch (Exception e) {
            // If anything goes wrong, don't crash the server - just allow the damage
            // This ensures the mixin doesn't cause crashes
            return;
        }
    }
}
