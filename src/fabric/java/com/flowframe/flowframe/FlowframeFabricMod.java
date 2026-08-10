package com.flowframe.flowframe;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

public class FlowframeFabricMod implements ModInitializer {
    public static final String MOD_ID = "flowframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Whether the LuckPerms mod is present on this server. Checked once at
    // startup so we never touch the LuckPerms API classes if it isn't loaded.
    private static final boolean LUCKPERMS_LOADED = FabricLoader.getInstance().isModLoaded("luckperms");

    private final DataManager dataManager;
    private final Set<UUID> headRiders = new java.util.HashSet<>();
    private final Map<UUID, Double> headRiderSmoothedY = new java.util.HashMap<>();
    // Rider UUID -> target UUID. Needed so that whenever a ride ends, for ANY reason
    // (our own sneak handling, vanilla's own built-in dismount-on-sneak beating us to
    // it, the rider disconnecting, etc.), we still know who to notify.
    private final Map<UUID, UUID> headRiderTargets = new java.util.HashMap<>();
    private int tickCounter;

    // Exposed so EndPortalBlockMixin (which has no other way to reach mod state) can
    // check isEndPortalEnabled() without needing a full dependency-injection setup.
    private static FlowframeFabricMod instance;

    public static FlowframeFabricMod getInstance() {
        return instance;
    }

    public FlowframeFabricMod() {
        instance = this;
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        this.dataManager = new DataManager(configDir.resolve("flowframe_data.json"));
    }

    @Override
    public void onInitialize() {
        registerCommands();
        registerEvents();
        LOGGER.info("Flowframe Fabric mod initialized");
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("keepinv")
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        ServerPlayerEntity player = source.getPlayer();
                        if (player == null) {
                            source.sendFeedback(() -> Text.literal("This command can only be used by players."), false);
                            return 1;
                        }
                        boolean nowOptedOut = dataManager.toggle(player.getUuid());
                        player.sendMessage(Text.literal("Keep Inventory: " + (nowOptedOut ? "OFF" : "ON")).formatted(nowOptedOut ? Formatting.RED : Formatting.GREEN), false);
                        return 1;
                    })
            );

            dispatcher.register(
                CommandManager.literal("endtoggle")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(context -> {
                        boolean nowEnabled = !dataManager.isEndPortalEnabled();
                        dataManager.setEndPortalEnabled(nowEnabled);
                        Text state = nowEnabled ? Text.literal("ENABLED").formatted(Formatting.GREEN) : Text.literal("DISABLED").formatted(Formatting.RED);
                        context.getSource().getServer().getPlayerManager().broadcast(Text.literal("End portals are now ").append(state).append(Text.literal(".")), false);
                        return 1;
                    })
            );
        });
    }

    private void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!dataManager.hasSeenPlayer(player.getUuid())) {
                dataManager.markSeenPlayer(player.getUuid());
                server.getPlayerManager().broadcast(Text.literal(player.getName().getString() + " joined the server for the first time! Welcome them!").formatted(Formatting.GOLD), false);
            } else {
                server.getPlayerManager().broadcast(Text.literal(player.getName().getString() + " joined the server").formatted(Formatting.GRAY), false);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            server.getPlayerManager().broadcast(Text.literal(handler.player.getName().getString() + " left the server").formatted(Formatting.GRAY), false);
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            Formatting nameColor = resolveGroupColor(sender);
            Text formatted = Text.literal(sender.getName().getString())
                .formatted(nameColor)
                .append(Text.literal(" » ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(message.getSignedContent()).formatted(Formatting.WHITE));
            sender.server.getPlayerManager().broadcast(formatted, false);
            return false;
        });

        // Safety net only now: EndPortalBlockMixin blocks portal entry outright, so
        // under normal circumstances a player should never actually arrive in The End
        // while it's disabled. This stays in place to catch edge cases (commands,
        // other mods/plugins, end gateways, etc.) that don't go through the block.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            if (!dataManager.isEndPortalEnabled() && destination.getRegistryKey() == World.END) {
                teleportPlayerOutOfTheEnd(player);
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity rider)) {
                return ActionResult.PASS;
            }
            if (hand != Hand.MAIN_HAND) {
                return ActionResult.PASS;
            }
            if (!rider.getStackInHand(hand).isEmpty() || !rider.getOffHandStack().isEmpty()) {
                return ActionResult.PASS;
            }
            if (!(entity instanceof ServerPlayerEntity target) || target.getUuid().equals(rider.getUuid())) {
                return ActionResult.PASS;
            }
            if (rider.hasVehicle() || target.hasPassengers()) {
                return ActionResult.PASS;
            }
            if (rider.startRiding(target, true)) {
                headRiders.add(rider.getUuid());
                headRiderSmoothedY.remove(rider.getUuid());
                headRiderTargets.put(rider.getUuid(), target.getUuid());
                // The vehicle's own client never receives the normal entity-tracking
                // broadcast about its own passengers (a player doesn't "track" itself),
                // so without this the target never learns they have a rider and keeps
                // rendering them at the old spot. Send it directly to their connection.
                target.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(target));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (dataManager.isOptedOut(player.getUuid())) {
                    dropInventory(player);
                }
                if (isSelfProjectileKill(player, damageSource)) {
                    ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                    player.dropItem(head, false, false);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            handleHeadRiding(server);
            if (server.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
            }
            if (dataManager.isEndPortalEnabled()) {
                return;
            }
            tickCounter++;
            if (tickCounter % 100 != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getWorld().getRegistryKey() != World.END) {
                    continue;
                }
                player.sendMessage(Text.literal("The End is currently disabled.").formatted(Formatting.RED), false);
                ServerWorld overworld = server.getOverworld();
                player.teleport(overworld, overworld.getSpawnPos().getX() + 0.5D, overworld.getSpawnPos().getY() + 1.0D, overworld.getSpawnPos().getZ() + 0.5D, 0.0F, 0.0F);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            server.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(false, server);
        });
    }

    public boolean isEndPortalEnabled() {
        return dataManager.isEndPortalEnabled();
    }

    public void setEndPortalEnabled(boolean enabled) {
        dataManager.setEndPortalEnabled(enabled);
    }

    /**
     * Looks up the LuckPerms "chat-color" meta value from the player's primary
     * group and converts it to a Formatting. Falls back to white if LuckPerms
     * is absent, the player/group can't be resolved, or the meta key isn't set.
     *
     * Set the color on a group with:
     *   /lp group <group> meta set chat-color <minecraft-colour-name>
     * e.g. /lp group default meta set chat-color gray
     */
    private Formatting resolveGroupColor(ServerPlayerEntity player) {
        if (!LUCKPERMS_LOADED) {
            return Formatting.WHITE;
        }
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUuid());
            if (user == null) {
                return Formatting.WHITE;
            }

            Group group = lp.getGroupManager().getGroup(user.getPrimaryGroup());
            if (group == null) {
                return Formatting.WHITE;
            }

            String colorName = group.getNodes(NodeType.META).stream()
                    .filter(node -> node.getMetaKey().equals("chat-color"))
                    .map(MetaNode::getMetaValue)
                    .findFirst()
                    .orElse(null);
            if (colorName == null) {
                return Formatting.WHITE;
            }

            Formatting formatting = Formatting.byName(colorName.toLowerCase());
            return formatting != null ? formatting : Formatting.WHITE;
        } catch (IllegalStateException e) {
            // LuckPerms mod is present but its provider isn't registered yet (e.g. very early startup).
            return Formatting.WHITE;
        }
    }

    private void handleHeadRiding(net.minecraft.server.MinecraftServer server) {
        Iterator<UUID> iterator = headRiders.iterator();
        while (iterator.hasNext()) {
            UUID riderId = iterator.next();
            ServerPlayerEntity rider = server.getPlayerManager().getPlayer(riderId);

            if (rider == null || !rider.isAlive() || !rider.hasVehicle()) {
                // Rider disconnected, died, or is simply no longer riding — including
                // via vanilla's own built-in sneak-to-dismount, which can run before
                // this tick and beat us to it. Whatever the cause, make sure the
                // target still gets told the passenger is gone.
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
                continue;
            }

            Entity vehicle = rider.getVehicle();
            if (!(vehicle instanceof ServerPlayerEntity target)) {
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
                continue;
            }

            if (rider.isSneaking()) {
                rider.stopRiding();
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
                continue;
            }

            if (target.isSneaking()) {
                target.removeAllPassengers();
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
                continue;
            }

            // Use the target's current eye Y (accounts for sneaking/pose changes) plus
            // a generous margin, so the rider's model AND hitbox sit well clear of the
            // target's own head — otherwise the rider's overlapping hitbox blocks the
            // target's view and interferes with their own interact/attack raycasts.
            double rawMountY = target.getEyeY() + 1.2D;

            // Vanilla's own passenger-follow system already ran earlier this tick and
            // snapped the rider to a raw, unsmoothed value; since this loop runs in
            // END_SERVER_TICK (after that), whatever we set here is the final,
            // authoritative position that actually gets broadcast — so we override it
            // with a smoothed value to cancel out per-tick walking-physics noise.
            // Lower this (e.g. 0.3) for smoother-but-laggier following, raise it
            // (e.g. 0.7) for snappier-but-jitterier following.
            double smoothing = 0.45D;
            double previousY = headRiderSmoothedY.getOrDefault(riderId, rawMountY);
            double smoothedY = previousY + (rawMountY - previousY) * smoothing;
            headRiderSmoothedY.put(riderId, smoothedY);

            rider.setPosition(target.getX(), smoothedY, target.getZ());
        }
    }

    /**
     * Cleans up tracking state for a rider and, if we know who they were riding,
     * tells that player's client directly that the passenger is gone. Needed for
     * every removal path, not just the ones we trigger ourselves, because a
     * player's own client never receives the normal entity-tracking broadcast
     * about its own passengers.
     */
    private void notifyTargetOfDismount(net.minecraft.server.MinecraftServer server, UUID riderId) {
        headRiderSmoothedY.remove(riderId);
        UUID targetId = headRiderTargets.remove(riderId);
        if (targetId == null) {
            return;
        }
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
        if (target != null) {
            target.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(target));
        }
    }

    private void teleportPlayerOutOfTheEnd(ServerPlayerEntity player) {
        ServerWorld overworld = player.server.getOverworld();
        if (overworld == null) {
            return;
        }
        player.sendMessage(Text.literal("The End is currently disabled.").formatted(Formatting.RED), false);
        player.requestTeleport(overworld.getSpawnPos().getX() + 0.5D, overworld.getSpawnPos().getY() + 1.0D, overworld.getSpawnPos().getZ() + 0.5D);
    }

    private void dropInventory(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        for (ItemStack stack : inventory.main) {
            if (!stack.isEmpty()) {
                player.dropItem(stack.copy(), false, false);
            }
        }
        for (ItemStack stack : inventory.armor) {
            if (!stack.isEmpty()) {
                player.dropItem(stack.copy(), false, false);
            }
        }
        ItemStack offhand = inventory.offHand.get(0);
        if (!offhand.isEmpty()) {
            player.dropItem(offhand.copy(), false, false);
        }
        inventory.clear();
    }

    private boolean isSelfProjectileKill(ServerPlayerEntity player, DamageSource damageSource) {
        Entity source = damageSource.getSource();
        if (!(source instanceof net.minecraft.entity.projectile.PersistentProjectileEntity projectile)) {
            return false;
        }
        if (projectile.getOwner() instanceof PlayerEntity owner) {
            return owner.getUuid().equals(player.getUuid());
        }
        return false;
    }
}