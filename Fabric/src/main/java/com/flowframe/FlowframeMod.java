package com.flowframe;

/*
Flowframe Mod Features:

- Player head drops on self-inflicted bow deaths (custom head drop logic).
- No crop trampling: farmland cannot be trampled by players or mobs.
- Invisible item frames: shift + right-click to make item frames invisible, right-click again to reveal.
- /tphere command: teleport another player to you (requires permission).
- /keepinv command: toggle keep inventory per player (requires permission).
- /orelog command: view ore mining logs (requires permission).
- Ore announce feature: broadcasts ore discoveries to ops or players with permission.
- All major features are permission-based, allowing server admins to grant or revoke access to /tphere, /keepinv, /orelog, and ore broadcasts individually.
*/

import net.fabricmc.api.ModInitializer;

import com.flowframe.features.oreannounce.OreAnnounceFeature;
import com.flowframe.features.keepinventory.KeepInventoryFeature;
import com.flowframe.features.creepernogrief.CreeperNoGriefFeature;
import com.flowframe.features.tphere.TpHereCommand;
import com.flowframe.features.croptrampling.NoCropTramplingFeature;

public class FlowframeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[FLOWFRAME] Initializing Flowframe mod");
        // Register features here:
        OreAnnounceFeature.register();
        KeepInventoryFeature.register(); 
        CreeperNoGriefFeature.register();
        TpHereCommand.register();
        NoCropTramplingFeature.register();
    }
}
