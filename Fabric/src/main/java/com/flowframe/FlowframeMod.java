package com.flowframe;

import net.fabricmc.api.ModInitializer;
import com.flowframe.features.oreannounce.OreAnnounceFeature;


public class FlowframeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[Flowframe] Initializing Flowframe mod");
        // Register features here:
        OreAnnounceFeature.register();
    }
}
