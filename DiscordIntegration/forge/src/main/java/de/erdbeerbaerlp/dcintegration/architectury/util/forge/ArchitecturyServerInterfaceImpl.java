package de.erdbeerbaerlp.dcintegration.architectury.util.forge;

import de.erdbeerbaerlp.dcintegration.architectury.forge.DiscordIntegrationForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.UUID;

public class ArchitecturyServerInterfaceImpl {
    public static String getLoaderNameX() {
        return "Forge";
    }

    public static String getLoaderVersion() {
        return FMLLoader.getLauncherInfo() + " (MC: " + FMLLoader.versionInfo().mcVersion() + ")";
    }

    public static boolean playerHasPermissionsX(UUID player, String... permissions) {
        final ServerPlayer serverPlayer = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(player);
        for (String p : permissions) {
            if (serverPlayer != null) {
                if (PermissionAPI.getPermission(serverPlayer, DiscordIntegrationForge.nodes.get(p))) {
                    return true;
                }
            } else {
                if (PermissionAPI.getOfflinePermission(player, DiscordIntegrationForge.nodes.get(p))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean playerHasPermissionsX(Player serverPlayer, String... permissions) {
        for (String p : permissions) {
            if (PermissionAPI.getPermission((ServerPlayer) serverPlayer, DiscordIntegrationForge.nodes.get(p))) {
                return true;
            }
        }
        return false;
    }
}
