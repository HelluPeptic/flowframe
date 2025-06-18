package com.flowframe.features.chatformat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.minecraft.server.network.ServerPlayerEntity;

public class LuckPermsUtil {
    public static String getLuckPermsPrefix(ServerPlayerEntity player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user != null) {
                CachedMetaData meta = user.getCachedData().getMetaData();
                String prefix = meta.getPrefix();
                if (prefix != null) {
                    // Convert & color codes to §
                    return prefix.replace('&', '§');
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }
}
