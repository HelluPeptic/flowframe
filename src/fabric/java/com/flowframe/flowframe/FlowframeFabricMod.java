package com.flowframe.flowframe;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

public class FlowframeFabricMod implements ModInitializer {
    public static final String MOD_ID = "flowframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Whether the LuckPerms mod is present on this server. Checked once at
    // startup so we never touch the LuckPerms API classes if it isn't loaded.
    private static final boolean LUCKPERMS_LOADED = FabricLoader.getInstance().isModLoaded("luckperms");

    private final DataManager dataManager;
    private final HorseTraining horseTraining = new HorseTraining();
    // ServerPlayer's own "server" field is private, with no public getter on the
    // entity itself — stashing the instance here once at startup (well before any player
    // can trigger a callback that needs it) avoids relying on entity-side accessors at all.
    private volatile MinecraftServer serverInstance;
    private final Set<UUID> headRiders = new java.util.HashSet<>();
    // Rider UUID -> target UUID. Needed so that whenever a ride ends, for ANY reason
    // (our own sneak handling, vanilla's own built-in dismount-on-sneak beating us to
    // it, the rider disconnecting, etc.), we still know who to notify.
    private final Map<UUID, UUID> headRiderTargets = new java.util.HashMap<>();
    private int tickCounter;

    // Phantoms are relocated to The End: SPAWN_PHANTOMS is forced off (below) so vanilla's
    // own phantom spawner never fires anywhere, and this replicates just its "haven't
    // slept in 3 days" condition against players standing in The End instead.
    private static final int PHANTOM_CHECK_PERIOD_TICKS = 200; // every 10s
    private static final int PHANTOM_INSOMNIA_TICKS = 72000; // 3 in-game days, matches vanilla
    private static final int PHANTOM_SPAWN_CHANCE_ONE_IN = 6; // ~once/minute per eligible player
    private static final int PHANTOM_HORIZONTAL_SPREAD = 20;
    private static final int PHANTOM_MIN_HEIGHT_ABOVE = 16;
    private static final int PHANTOM_HEIGHT_ABOVE_RANGE = 14;
    private final Random phantomRandom = new Random();
    private int phantomTickCounter;

    // Volume for the sound that plays when a dimension is opened.
    private static final float DIMENSION_OPEN_SOUND_VOLUME = 0.15F;

    // Exposed so the portal mixins (which have no other way to reach mod state) can
    // check isEndPortalEnabled()/isNetherPortalEnabled() without needing a full
    // dependency-injection setup.
    private static FlowframeFabricMod instance;

    public static FlowframeFabricMod getInstance() {
        return instance;
    }

    public HorseTraining getHorseTraining() {
        return horseTraining;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            dispatcher.register(
                Commands.literal("keepinv")
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        ServerPlayer player = source.getPlayer();
                        if (player == null) {
                            source.sendSuccess(() -> Component.literal("This command can only be used by players."), false);
                            return 1;
                        }
                        boolean nowOptedOut = dataManager.toggle(player.getUUID());
                        player.sendSystemMessage(Component.literal("Keep Inventory: " + (nowOptedOut ? "OFF" : "ON")).withStyle(nowOptedOut ? ChatFormatting.RED : ChatFormatting.GREEN));
                        return 1;
                    })
            );

            dispatcher.register(
                Commands.literal("horsestats")
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        ServerPlayer player = source.getPlayer();
                        if (player == null) {
                            source.sendSuccess(() -> Component.literal("This command can only be used by players."), false);
                            return 1;
                        }
                        horseTraining.showStats(player);
                        return 1;
                    })
            );

            dispatcher.register(
                Commands.literal("endtoggle")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> {
                        boolean nowEnabled = !dataManager.isEndPortalEnabled();
                        dataManager.setEndPortalEnabled(nowEnabled);
                        MinecraftServer server = context.getSource().getServer();
                        Component state = nowEnabled ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN) : Component.literal("DISABLED").withStyle(ChatFormatting.RED);
                        server.getPlayerList().broadcastSystemMessage(Component.literal("End portals are now ").append(state).append(Component.literal(".")), false);
                        if (nowEnabled) {
                            announceDimensionOpened(server, "The End", ChatFormatting.LIGHT_PURPLE, SoundEvents.END_PORTAL_SPAWN);
                        }
                        return 1;
                    })
            );

            dispatcher.register(
                Commands.literal("nethertoggle")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(context -> {
                        boolean nowEnabled = !dataManager.isNetherPortalEnabled();
                        dataManager.setNetherPortalEnabled(nowEnabled);
                        MinecraftServer server = context.getSource().getServer();
                        Component state = nowEnabled ? Component.literal("ENABLED").withStyle(ChatFormatting.GREEN) : Component.literal("DISABLED").withStyle(ChatFormatting.RED);
                        server.getPlayerList().broadcastSystemMessage(Component.literal("Nether portals are now ").append(state).append(Component.literal(".")), false);
                        if (nowEnabled) {
                            announceDimensionOpened(server, "The Nether", ChatFormatting.DARK_RED, SoundEvents.PORTAL_TRAVEL);
                        }
                        return 1;
                    })
            );
        });
    }

    private void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
            ServerPlayer player = listener.player;
            if (!dataManager.hasSeenPlayer(player.getUUID())) {
                dataManager.markSeenPlayer(player.getUUID());
                server.getPlayerList().broadcastSystemMessage(Component.literal(player.getName().getString() + " joined the server for the first time! Welcome them!").withStyle(ChatFormatting.GOLD), false);
            }
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> {
            ChatFormatting nameColor = resolveGroupColor(sender);
            Component formatted = Component.literal(sender.getName().getString())
                .withStyle(nameColor)
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(message.signedContent()).withStyle(ChatFormatting.WHITE));
            serverInstance.getPlayerList().broadcastSystemMessage(formatted, false);

            // Returning false below skips vanilla's own "<Name> message" broadcast (so we
            // don't get a duplicate, differently-formatted line) — but per PlayerListMixin
            // in fabric-message-api, that also skips the CHAT_MESSAGE notify event entirely,
            // which is what bridge mods like Discord4Fabric listen on to relay chat elsewhere.
            // Fire it ourselves so those mods still see the message even though we've taken
            // over the actual in-game broadcast.
            ServerMessageEvents.CHAT_MESSAGE.invoker().onChatMessage(message, sender, boundChatType);
            return false;
        });

        // Safety net only now: EndPortalBlockMixin blocks portal entry outright, so
        // under normal circumstances a player should never actually arrive in The End
        // while it's disabled. This stays in place to catch edge cases (commands,
        // other mods/plugins, end gateways, etc.) that don't go through the block.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            if (!dataManager.isEndPortalEnabled() && destination.dimension() == Level.END) {
                teleportPlayerOutOfTheEnd(player);
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer rider)) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (!rider.getItemInHand(hand).isEmpty() || !rider.getOffhandItem().isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof ServerPlayer clicked) || clicked.getUUID().equals(rider.getUUID())) {
                return InteractionResult.PASS;
            }
            if (rider.isSpectator() || clicked.isSpectator()) {
                return InteractionResult.PASS;
            }
            if (rider.isPassenger()) {
                return InteractionResult.PASS;
            }

            // Right-clicking ANY player in an existing stack attaches the new rider to
            // the current top of that stack, not just to whoever was clicked directly —
            // otherwise you have to click the exact, often visually obscured, top player.
            UUID topId = clicked.getUUID();
            UUID nextRiderId = findRiderOf(topId);
            while (nextRiderId != null) {
                if (nextRiderId.equals(rider.getUUID())) {
                    // Rider is already somewhere in this stack.
                    return InteractionResult.PASS;
                }
                topId = nextRiderId;
                nextRiderId = findRiderOf(topId);
            }
            ServerPlayer top = topId.equals(clicked.getUUID()) ? clicked : serverInstance.getPlayerList().getPlayer(topId);
            if (top == null || top.isVehicle()) {
                return InteractionResult.PASS;
            }

            if (rider.startRiding(top, true, true)) {
                headRiders.add(rider.getUUID());
                headRiderTargets.put(rider.getUUID(), top.getUUID());
                // The vehicle's own client never receives the normal entity-tracking
                // broadcast about its own passengers (a player doesn't "track" itself),
                // so without this the target never learns they have a rider and keeps
                // rendering them at the old spot. Send it directly to their connection.
                top.connection.send(new ClientboundSetPassengersPacket(top));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                if (dataManager.isOptedOut(player.getUUID())) {
                    dropInventory(player);
                }
                if (isSelfProjectileKill(player, damageSource)) {
                    ItemStack head = new ItemStack(Items.PLAYER_HEAD);
                    // Without a profile the head renders as the default Steve skin.
                    head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
                    player.drop(head, false, Prediction.SERVER_ONLY);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            handleHeadRiding(server);
            horseTraining.tick(server);
            forceGameRulesOff(server);
            handlePhantomSpawning(server);
            if (dataManager.isEndPortalEnabled()) {
                return;
            }
            tickCounter++;
            if (tickCounter % 100 != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension() != Level.END) {
                    continue;
                }
                player.sendSystemMessage(Component.literal("The End is currently disabled.").withStyle(ChatFormatting.RED));
                ServerLevel overworld = server.overworld();
                BlockPos spawnPos = server.getRespawnData().pos();
                player.teleportTo(overworld, spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, Set.of(), 0.0F, 0.0F, false);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            forceGameRulesOff(server);
        });
    }

    public boolean isEndPortalEnabled() {
        return dataManager.isEndPortalEnabled();
    }

    public void setEndPortalEnabled(boolean enabled) {
        dataManager.setEndPortalEnabled(enabled);
    }

    public boolean isNetherPortalEnabled() {
        return dataManager.isNetherPortalEnabled();
    }

    /**
     * Shows every online player a big on-screen title announcing that a dimension has
     * opened, along with a sound that fits that dimension. The sound is sent to each
     * player at their own position, so everyone hears it at the same volume no matter
     * where they are in the world.
     */
    private void announceDimensionOpened(MinecraftServer server, String dimensionName, ChatFormatting color, SoundEvent sound) {
        PlayerList players = server.getPlayerList();
        // Fade in 0.5s, stay 3.5s, fade out 1s (vanilla's default title timings).
        players.broadcastAll(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        players.broadcastAll(new ClientboundSetSubtitleTextPacket(Component.literal("is now open!").withStyle(ChatFormatting.GOLD)));
        players.broadcastAll(new ClientboundSetTitleTextPacket(Component.literal(dimensionName).withStyle(color, ChatFormatting.BOLD)));

        Holder<SoundEvent> soundHolder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        for (ServerPlayer player : players.getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(soundHolder, SoundSource.MASTER, player.getX(), player.getY(), player.getZ(), DIMENSION_OPEN_SOUND_VOLUME, 1.0F, player.getRandom().nextLong()));
        }
    }

    /**
     * Looks up the LuckPerms "chat-color" meta value from the player's primary
     * group and converts it to a ChatFormatting. Falls back to white if LuckPerms
     * is absent, the player/group can't be resolved, or the meta key isn't set.
     *
     * Set the color on a group with:
     *   /lp group <group> meta set chat-color <minecraft-colour-name>
     * e.g. /lp group default meta set chat-color gray
     */
    private ChatFormatting resolveGroupColor(ServerPlayer player) {
        if (!LUCKPERMS_LOADED) {
            return ChatFormatting.WHITE;
        }
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUUID());
            if (user == null) {
                return ChatFormatting.WHITE;
            }

            Group group = lp.getGroupManager().getGroup(user.getPrimaryGroup());
            if (group == null) {
                return ChatFormatting.WHITE;
            }

            String colorName = group.getNodes(NodeType.META).stream()
                    .filter(node -> node.getMetaKey().equals("chat-color"))
                    .map(MetaNode::getMetaValue)
                    .findFirst()
                    .orElse(null);
            if (colorName == null) {
                return ChatFormatting.WHITE;
            }

            // ChatFormatting no longer has a by-name lookup; its enum constant names are
            // the upper-cased colour names ("dark_gray" -> DARK_GRAY).
            try {
                return ChatFormatting.valueOf(colorName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ChatFormatting.WHITE;
            }
        } catch (IllegalStateException e) {
            // LuckPerms mod is present but its provider isn't registered yet (e.g. very early startup).
            return ChatFormatting.WHITE;
        }
    }

    private void handleHeadRiding(MinecraftServer server) {
        java.util.Iterator<UUID> iterator = headRiders.iterator();
        while (iterator.hasNext()) {
            UUID riderId = iterator.next();
            ServerPlayer rider = server.getPlayerList().getPlayer(riderId);

            if (rider == null || !rider.isAlive() || !rider.isPassenger()) {
                // Rider disconnected, died, or is simply no longer riding — including
                // via vanilla's own built-in sneak-to-dismount, which can run before
                // this tick and beat us to it. Whatever the cause, make sure the
                // target still gets told the passenger is gone.
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
                continue;
            }

            Entity vehicle = rider.getVehicle();
            if (!(vehicle instanceof ServerPlayer target)) {
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
                continue;
            }

            if (target.isShiftKeyDown()) {
                target.ejectPassengers();
                notifyTargetOfDismount(server, riderId);
                iterator.remove();
            }
        }
    }

    /** Who, if anyone, is currently riding {@code targetId}? */
    private UUID findRiderOf(UUID targetId) {
        for (Map.Entry<UUID, UUID> entry : headRiderTargets.entrySet()) {
            if (entry.getValue().equals(targetId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void forceGameRulesOff(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        forceRuleOff(rules, GameRules.MOB_GRIEFING, server);
        forceRuleOff(rules, GameRules.SPAWN_PHANTOMS, server);
    }

    private void forceRuleOff(GameRules rules, GameRule<Boolean> rule, MinecraftServer server) {
        if (rules.get(rule)) {
            rules.set(rule, false, server);
        }
    }

    /**
     * Spawns Phantoms above players standing in The End, on a periodic check that
     * mirrors vanilla's own insomnia condition (SPAWN_PHANTOMS is forced off above, so
     * this is the only place Phantoms come from anymore).
     */
    private void handlePhantomSpawning(MinecraftServer server) {
        phantomTickCounter++;
        if (phantomTickCounter % PHANTOM_CHECK_PERIOD_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.level();
            if (level.dimension() != Level.END) {
                continue;
            }
            if (player.isSpectator()) {
                continue;
            }
            int timeSinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
            if (timeSinceRest < PHANTOM_INSOMNIA_TICKS) {
                continue;
            }
            if (phantomRandom.nextInt(PHANTOM_SPAWN_CHANCE_ONE_IN) != 0) {
                continue;
            }

            BlockPos origin = player.blockPosition();
            for (int attempt = 0; attempt < 3; attempt++) {
                int dx = phantomRandom.nextInt(PHANTOM_HORIZONTAL_SPREAD * 2 + 1) - PHANTOM_HORIZONTAL_SPREAD;
                int dz = phantomRandom.nextInt(PHANTOM_HORIZONTAL_SPREAD * 2 + 1) - PHANTOM_HORIZONTAL_SPREAD;
                int dy = PHANTOM_MIN_HEIGHT_ABOVE + phantomRandom.nextInt(PHANTOM_HEIGHT_ABOVE_RANGE);
                BlockPos spawnPos = origin.offset(dx, dy, dz);
                if (spawnPos.getY() >= level.getMaxY() - 4) {
                    continue;
                }
                if (!level.getBlockState(spawnPos).isAir()) {
                    continue;
                }

                int count = 1 + phantomRandom.nextInt(2);
                for (int i = 0; i < count; i++) {
                    EntityTypes.PHANTOM.spawn(level, spawnPos, EntitySpawnReason.NATURAL);
                }
                break;
            }
        }
    }

    /**
     * Cleans up tracking state for a rider and, if we know who they were riding,
     * tells that player's client directly that the passenger is gone. Needed for
     * every removal path, not just the ones we trigger ourselves, because a
     * player's own client never receives the normal entity-tracking broadcast
     * about its own passengers.
     */
    private void notifyTargetOfDismount(MinecraftServer server, UUID riderId) {
        UUID targetId = headRiderTargets.remove(riderId);
        if (targetId == null) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target != null) {
            target.connection.send(new ClientboundSetPassengersPacket(target));
        }
    }

    private void teleportPlayerOutOfTheEnd(ServerPlayer player) {
        BlockPos spawnPos = serverInstance.getRespawnData().pos();
        player.sendSystemMessage(Component.literal("The End is currently disabled.").withStyle(ChatFormatting.RED));
        player.teleportTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D);
    }

    private static final EquipmentSlot[] ARMOR_AND_OFFHAND_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
    };

    private void dropInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), false, Prediction.SERVER_ONLY);
            }
        }
        for (EquipmentSlot slot : ARMOR_AND_OFFHAND_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), false, Prediction.SERVER_ONLY);
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        inventory.clearContent();
    }

    private boolean isSelfProjectileKill(ServerPlayer player, DamageSource damageSource) {
        Entity source = damageSource.getDirectEntity();
        if (!(source instanceof AbstractArrow projectile)) {
            return false;
        }
        if (projectile.getOwner() instanceof Player owner) {
            return owner.getUUID().equals(player.getUUID());
        }
        return false;
    }
}
