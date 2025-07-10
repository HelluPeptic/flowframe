package com.flowframe.features.restartwarning;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RestartWarningFeature {
    private static ScheduledExecutorService scheduler;
    private static MinecraftServer server;
    
    // Central European Time zone
    private static final ZoneId CET_ZONE = ZoneId.of("Europe/Berlin");
    
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(RestartWarningFeature::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(RestartWarningFeature::onServerStop);
    }
    
    private static void onServerStart(MinecraftServer minecraftServer) {
        server = minecraftServer;
        scheduler = Executors.newScheduledThreadPool(1);
        
        // Schedule the daily restart warning
        scheduleNextRestartWarning();
        
        System.out.println("[FLOWFRAME] Restart warning scheduler started");
    }
    
    private static void onServerStop(MinecraftServer minecraftServer) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            System.out.println("[FLOWFRAME] Restart warning scheduler stopped");
        }
    }
    
    private static void scheduleNextRestartWarning() {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        
        ZonedDateTime now = ZonedDateTime.now(CET_ZONE);
        ZonedDateTime nextWarning = now.withHour(6).withMinute(55).withSecond(0).withNano(0);
        
        // If it's already past 6:55 AM today, schedule for tomorrow
        if (now.isAfter(nextWarning)) {
            nextWarning = nextWarning.plusDays(1);
        }
        
        long delayMinutes = ChronoUnit.MINUTES.between(now, nextWarning);
        
        System.out.println("[FLOWFRAME] Next restart warning scheduled in " + delayMinutes + " minutes at " + nextWarning);
        
        scheduler.schedule(() -> {
            sendRestartWarning();
            // Schedule the next one for tomorrow
            scheduleNextRestartWarning();
        }, delayMinutes, TimeUnit.MINUTES);
    }
    
    private static void sendRestartWarning() {
        if (server == null) {
            return;
        }
        
        Text warningMessage = Text.literal("[FLOWFRAME] ⚠ SERVER RESTART IN 5 MINUTES ⚠")
            .formatted(Formatting.RED, Formatting.BOLD);
        
        // Broadcast to all players
        server.getPlayerManager().broadcast(warningMessage, false);
        
        // Also log to console
        System.out.println("[FLOWFRAME] Sent daily restart warning: SERVER RESTART IN 5 MINUTES");
    }
}
