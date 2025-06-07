package de.erdbeerbaerlp.dcintegration.architectury.forge;

import de.erdbeerbaerlp.dcintegration.common.storage.Configuration;
import de.erdbeerbaerlp.dcintegration.common.util.MinecraftPermission;

import de.erdbeerbaerlp.dcintegration.architectury.DiscordIntegrationMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.HashMap;

import static de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.LOGGER;

@Mod(DiscordIntegrationMod.MOD_ID)
public final class DiscordIntegrationForge {
    public DiscordIntegrationForge() {
        DiscordIntegrationMod.init();
        if (Configuration.instance().general.botToken.equals("INSERT BOT TOKEN HERE")) { //Prevent events when token not set or on client
            LOGGER.error("Please check the config file and set an bot token");
        } else {
            MinecraftForge.EVENT_BUS.addListener(this::serverSetup);
            MinecraftForge.EVENT_BUS.register(this);
        }
    }
    public void serverSetup(FMLDedicatedServerSetupEvent ev) {

    }
    @SubscribeEvent
    public void serverStarting(final ServerStartingEvent ev) {
        DiscordIntegrationMod.serverStarting(ev.getServer());
    }
    @SubscribeEvent
    public void serverStarted(final ServerStartedEvent ev) {
        DiscordIntegrationMod.serverStarted(ev.getServer());

    }
    @SubscribeEvent
    public void serverStopping(final ServerStoppingEvent ev) {
        DiscordIntegrationMod.serverStopping(ev.getServer());

    }
    @SubscribeEvent
    public void serverStopped(final ServerStoppedEvent ev) {
        DiscordIntegrationMod.serverStopped(ev.getServer());
    }
    public static final HashMap<String, PermissionNode<Boolean>> nodes = new HashMap();

    @SubscribeEvent
    public void addPermissions(final PermissionGatherEvent.Nodes ev) {
        for (MinecraftPermission p : MinecraftPermission.values()) {
            nodes.put(p.getAsString(), new PermissionNode<>("dcintegration", p.getAsString().replace("dcintegration.", ""), PermissionTypes.BOOLEAN, (player, playerUUID, context) -> p.getDefaultValue()));
        }
        ev.addNodes(nodes.values().toArray(new PermissionNode[0]));
    }
}
