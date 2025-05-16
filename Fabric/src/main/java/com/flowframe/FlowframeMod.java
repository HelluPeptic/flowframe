package com.flowframe;

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
