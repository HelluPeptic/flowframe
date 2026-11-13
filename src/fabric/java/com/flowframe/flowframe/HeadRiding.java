package com.flowframe.flowframe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Lets players ride on each other's heads, stacked as high as they like.
 *
 * A rider doesn't ride the player below directly. They sit on an invisible "seat"
 * (an Interaction entity, which has no model) that rides the player below:
 *
 *   rider -> seat -> player below
 *
 * Two reasons:
 * - Vanilla refuses to let anything ride a player (Entity.startRiding rejects vehicles
 *   whose type isn't saveable, and players aren't). EntityRidingMixin makes an exception
 *   for these seats only.
 * - Riding a player directly puts the rider's legs right in front of the lower
 *   player's eyes. The seat's height lifts the rider clear of their view.
 *
 * It's all ordinary vanilla riding, so every client positions the stack by itself
 * every frame — it stays smooth and needs no client mod.
 */
public class HeadRiding {

    /** Scoreboard tag on every seat this class spawns. */
    public static final String SEAT_TAG = "flowframe_head_seat";

    // How far the seat lifts the rider. Vanilla would sit the rider's hips at the top
    // of the lower player's hitbox (1.8 blocks); this puts them at 2.2, just above the
    // lower player's head and high enough that the rider's legs stay out of their view.
    private static final float SEAT_HEIGHT = 0.4F;

    private final Set<Interaction> seats = new HashSet<>();
    // Carriers whose current sneak started while flying, where sneaking means "fly down".
    // That sneak never throws riders off, even once they land with the key still held;
    // they have to let go and sneak again.
    private final Set<UUID> flyingSneakers = new HashSet<>();
    // Carriers to re-send their own passenger list to on the next tick (see syncCarrier).
    private final List<ServerPlayer> pendingCarrierSyncs = new ArrayList<>();

    public static boolean isSeat(Entity entity) {
        return entity instanceof Interaction && entity.entityTags().contains(SEAT_TAG);
    }

    /** Whether this entity is currently sitting on someone's head. */
    public static boolean isHeadRiding(Entity entity) {
        return isSeat(entity.getVehicle());
    }

    /**
     * Puts {@code rider} on top of the stack {@code clicked} is part of. Clicking anyone
     * in a stack joins at the top, not just whoever was clicked.
     */
    public InteractionResult tryMount(ServerPlayer rider, ServerPlayer clicked) {
        if (rider.isPassenger() || rider.isSpectator() || clicked.isSpectator()) {
            return InteractionResult.PASS;
        }
        // Clicking someone in the stack you're carrying would make the stack ride itself.
        if (rider.hasIndirectPassenger(clicked)) {
            return InteractionResult.PASS;
        }
        ServerPlayer top = topOfStack(clicked);
        if (top.isVehicle()) {
            return InteractionResult.PASS;
        }

        ServerLevel level = top.level();
        Interaction seat = new Interaction(EntityTypes.INTERACTION, level);
        // Zero width so it can't be clicked or hit; the height is the lift.
        seat.setWidth(0.0F);
        seat.setHeight(SEAT_HEIGHT);
        seat.addTag(SEAT_TAG);
        seat.setPos(top.getX(), top.getY() + top.getBbHeight(), top.getZ());
        // Add it to the world first, so every client (the carrier included) knows the
        // seat exists before being told it's riding someone.
        if (!level.addFreshEntity(seat)) {
            return InteractionResult.PASS;
        }
        seats.add(seat);
        if (!seat.startRiding(top, true, false) || !rider.startRiding(seat, true, true)) {
            endRide(seat);
            return InteractionResult.PASS;
        }
        syncCarrier(top);
        pendingCarrierSyncs.add(top);
        return InteractionResult.SUCCESS;
    }

    /**
     * Ends rides that should end: someone dismounted, left, died, or the carrier sneaked
     * (other than to fly down).
     */
    public void tick() {
        for (ServerPlayer carrier : pendingCarrierSyncs) {
            if (!carrier.isRemoved()) {
                syncCarrier(carrier);
            }
        }
        pendingCarrierSyncs.clear();

        List<Interaction> finished = new ArrayList<>();
        for (Interaction seat : seats) {
            if (seat.isRemoved() || !isRideStillValid(seat)) {
                finished.add(seat);
            }
        }
        for (Interaction seat : finished) {
            seats.remove(seat);
            if (!seat.isRemoved()) {
                endRide(seat);
            }
        }
    }

    /**
     * Called (from PlayerListMixin) right before a player is removed from the server.
     * Takes them out of any stack first: vanilla assumes players are never vehicles, and
     * if a rider left while still on someone's head, vanilla would unload the player
     * underneath them along with them.
     */
    public void onPlayerLeave(ServerPlayer player) {
        List<Interaction> toEnd = new ArrayList<>();
        if (player.getVehicle() instanceof Interaction seat && isSeat(seat)) {
            toEnd.add(seat);
        }
        for (Entity passenger : player.getPassengers()) {
            if (passenger instanceof Interaction seat && isSeat(seat)) {
                toEnd.add(seat);
            }
        }
        for (Interaction seat : toEnd) {
            seats.remove(seat);
            endRide(seat);
        }
    }

    /**
     * Tracks seats as they're added to a world: ones spawned by tryMount, and any left
     * behind by a crash, which the next tick() then cleans up.
     */
    public void onEntityLoad(Entity entity) {
        if (entity instanceof Interaction seat && isSeat(seat)) {
            seats.add(seat);
        }
    }

    /**
     * Where a rider lands when they get off (from InteractionMixin): on the ground next
     * to the bottom of the stack, the same way vanilla finds a spot when you get off a
     * horse — rather than on top of or inside the player below.
     */
    public static Vec3 findDismountLocation(Interaction seat, LivingEntity passenger) {
        Entity bottom = seat.getRootVehicle();
        float yaw = passenger.getYRot();
        // Right, left, behind, then in front of the rider.
        for (float turn : new float[] {90.0F, -90.0F, 180.0F, 0.0F}) {
            Vec3 offset = besideOffset(bottom.getBbWidth(), passenger.getBbWidth(), yaw + turn);
            Vec3 spot = findGroundSpot(bottom, offset, passenger);
            if (spot != null) {
                return spot;
            }
        }
        // No room beside the stack: where the bottom player stands is known to fit a player.
        return bottom.position();
    }

    private boolean isRideStillValid(Interaction seat) {
        return seat.getVehicle() instanceof ServerPlayer carrier
                && carrier.isAlive()
                && !carrier.isRemoved()
                && !wantsToThrowRidersOff(carrier)
                && carrier.level() == seat.level()
                && seat.getPassengers().size() == 1
                && seat.getFirstPassenger() instanceof ServerPlayer rider
                && rider.isAlive();
    }

    /** Sneaking throws riders off, unless the sneak is a flying player going down. */
    private boolean wantsToThrowRidersOff(ServerPlayer carrier) {
        UUID id = carrier.getUUID();
        if (!carrier.isShiftKeyDown()) {
            flyingSneakers.remove(id);
            return false;
        }
        if (carrier.getAbilities().flying) {
            flyingSneakers.add(id);
        }
        return !flyingSneakers.contains(id);
    }

    private void endRide(Interaction seat) {
        Entity carrier = seat.getVehicle();
        if (carrier != null) {
            flyingSneakers.remove(carrier.getUUID());
        }
        // Dismount before removing the seat, so vanilla still asks the seat
        // (InteractionMixin) where to put the rider.
        for (Entity passenger : List.copyOf(seat.getPassengers())) {
            passenger.stopRiding();
        }
        seat.discard();
        if (carrier instanceof ServerPlayer player && !player.isRemoved()) {
            syncCarrier(player);
        }
    }

    private static ServerPlayer topOfStack(ServerPlayer player) {
        ServerPlayer current = player;
        while (true) {
            ServerPlayer above = null;
            for (Entity passenger : current.getPassengers()) {
                if (isSeat(passenger) && passenger.getFirstPassenger() instanceof ServerPlayer rider) {
                    above = rider;
                    break;
                }
            }
            if (above == null) {
                return current;
            }
            current = above;
        }
    }

    /**
     * Tells a carrier's own client what's riding them. Vanilla only sends passenger
     * updates to players tracking an entity, and a player doesn't track themselves, so
     * without this the carrier's client never learns about the seat on their head.
     */
    private static void syncCarrier(ServerPlayer carrier) {
        carrier.connection.send(new ClientboundSetPassengersPacket(carrier));
    }

    /** Offset that puts the passenger's hitbox right beside the bottom player's (same as vanilla's). */
    private static Vec3 besideOffset(double bottomWidth, double passengerWidth, float yaw) {
        double distance = (bottomWidth + passengerWidth + 1.0E-5) / 2.0;
        double x = -Math.sin(Math.toRadians(yaw));
        double z = Math.cos(Math.toRadians(yaw));
        double scale = Math.max(Math.abs(x), Math.abs(z));
        return new Vec3(x * distance / scale, 0.0, z * distance / scale);
    }

    /** Vanilla's horse dismount search: the first standable spot in that direction. */
    private static Vec3 findGroundSpot(Entity bottom, Vec3 offset, LivingEntity passenger) {
        Level level = bottom.level();
        double x = bottom.getX() + offset.x;
        double z = bottom.getZ() + offset.z;
        double minY = bottom.getBoundingBox().minY;
        double maxY = bottom.getBoundingBox().maxY + 0.75;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Pose pose : passenger.getDismountPoses()) {
            pos.set(x, minY, z);
            while (pos.getY() < maxY) {
                double floor = level.getBlockFloorHeight(pos);
                if (pos.getY() + floor > maxY) {
                    break;
                }
                if (DismountHelper.isBlockFloorValid(floor)) {
                    Vec3 spot = new Vec3(x, pos.getY() + floor, z);
                    if (DismountHelper.canDismountTo(level, passenger, passenger.getLocalBoundsForPose(pose).move(spot))) {
                        passenger.setPose(pose);
                        return spot;
                    }
                }
                pos.move(Direction.UP);
            }
        }
        return null;
    }
}
