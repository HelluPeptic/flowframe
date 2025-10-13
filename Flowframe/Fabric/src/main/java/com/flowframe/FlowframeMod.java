package com.flowframe;

import com.flowframe.features.chatformat.ChatFormatFeature;
import com.flowframe.features.blockduplication.BlockDuplicationFix;
import com.flowframe.features.spawner.SpawnerFeature;

import net.fabricmc.api.ModInitializer;

import com.flowframe.features.oreannounce.OreAnnounceFeature;
import com.flowframe.features.keepinventory.KeepInventoryFeature;
import com.flowframe.features.creepernogrief.CreeperNoGriefFeature;
import com.flowframe.features.tphere.TpHereCommand;
import com.flowframe.features.croptrampling.NoCropTramplingFeature;
import com.flowframe.features.phantomdeny.PhantomDenyFeature;
import com.flowframe.features.endtoggle.EndToggleFeature;
import com.flowframe.features.version.VersionCommand;
import com.flowframe.features.gl.GlAliasCommands;
import com.flowframe.features.linked.LinkedCommandFeature;
import com.flowframe.features.levitate.LevitateCommandFeature;
import com.flowframe.features.inventoryrestore.InventoryRestoreFeature;
import com.flowframe.features.countentities.CountEntitiesCommand;
import com.flowframe.features.gamerules.GameRulesFeature;
import com.flowframe.features.mapremoval.MapRemovalFeature;

public class FlowframeMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[FLOWFRAME] Initializing Flowframe mod");
        OreAnnounceFeature.register();
        KeepInventoryFeature.register(); 
        CreeperNoGriefFeature.register();
        TpHereCommand.register();
        NoCropTramplingFeature.register();
        ChatFormatFeature.register();
        PhantomDenyFeature.register();
        EndToggleFeature.register();
        VersionCommand.register();
        GlAliasCommands.register();
        LinkedCommandFeature.register();
        LevitateCommandFeature.register();
        InventoryRestoreFeature.register();
        CountEntitiesCommand.register();
        GameRulesFeature.register();
        MapRemovalFeature.register();
        BlockDuplicationFix.register();
        SpawnerFeature.register();
        System.out.println("[FLOWFRAME] Flowframe mod initialized");
    }
}
