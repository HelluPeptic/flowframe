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
- Arms Dealers Workbench blocker: prevents players from using/breaking the workbench from Vic's Point Blank mod (requires permission).
- All major features are permission-based, allowing server admins to grant or revoke access to /tphere, /keepinv, /orelog, ore broadcasts, and arms workbench usage individually.
*/

import com.flowframe.features.chatformat.ChatFormatFeature;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import com.flowframe.features.oreannounce.OreAnnounceFeature;
import com.flowframe.features.keepinventory.KeepInventoryFeature;
import com.flowframe.features.creepernogrief.CreeperNoGriefFeature;
import com.flowframe.features.tphere.TpHereCommand;
import com.flowframe.features.croptrampling.NoCropTramplingFeature;
import com.flowframe.features.phantomdeny.PhantomDenyFeature;
import com.flowframe.features.endtoggle.EndToggleFeature;
import com.flowframe.features.version.VersionCommand;
import com.flowframe.features.gl.GlAliasCommands;
import com.flowframe.features.togglepvp.TogglePvpFeature;
import com.flowframe.features.obtaincard.ObtainCardFeature;
import com.flowframe.features.linked.LinkedCommandFeature;
import com.flowframe.features.levitate.LevitateCommandFeature;
import com.flowframe.features.inventoryrestore.InventoryRestoreFeature;
import com.flowframe.features.countentities.CountEntitiesCommand;
import com.flowframe.features.gamerules.GameRulesFeature;
import com.flowframe.features.gungame.BattleFeature;

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
        TogglePvpFeature.register();
        ObtainCardFeature.register();
        LinkedCommandFeature.register();
        LevitateCommandFeature.register();
        InventoryRestoreFeature.register();
        CountEntitiesCommand.register();
        GameRulesFeature.register();
        BattleFeature.register();
        System.out.println("[FLOWFRAME] Flowframe mod initialized");
    }
}
