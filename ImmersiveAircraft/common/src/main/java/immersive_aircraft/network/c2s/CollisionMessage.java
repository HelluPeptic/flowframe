package immersive_aircraft.network.c2s;

import immersive_aircraft.cobalt.network.Message;
import immersive_aircraft.config.Config;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

public class CollisionMessage extends Message {
    private final float damage;

    public CollisionMessage(float damage) {
        this.damage = damage;
    }

    public CollisionMessage(FriendlyByteBuf b) {
        damage = b.readFloat();
    }

    @Override
    public void encode(FriendlyByteBuf b) {
        b.writeFloat(damage);
    }

    @Override
    public void receive(Player e) {
        if (e.getRootVehicle() instanceof VehicleEntity vehicle) {
            vehicle.hurt(e.level().damageSources().fall(), damage);
            if (vehicle.isRemoved()) {
                float crashDamage = damage * Config.getInstance().crashDamage;
                if (Config.getInstance().preventKillThroughCrash) {
                    crashDamage = Math.min(crashDamage, e.getHealth() - 1.0f);
                }
                e.hurt(e.level().damageSources().fall(), crashDamage);
            }
        }
    }
}
