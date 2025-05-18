package com.flowframe.features.chatformat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;

public class TablistSyncFeature {
    private static int tickCounter = 0;

    public static void register() {
        // Periodic tick event: every 100 ticks (~5 seconds)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter >= 100) {
                TablistUtil.updateTablistTeamsForAll(server);
                TablistUtil.updateTablistDisplayNamesSorted(server);
                tickCounter = 0;
            }
        });

        // Player respawn event
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            MinecraftServer server = newPlayer.getServer();
            if (server != null) {
                TablistUtil.updateTablistTeamsForAll(server);
                TablistUtil.updateTablistDisplayNamesSorted(server);
            }
        });
    }
}
