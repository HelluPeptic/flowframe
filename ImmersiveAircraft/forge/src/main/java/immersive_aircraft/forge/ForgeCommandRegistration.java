package immersive_aircraft.forge;

import com.mojang.brigadier.CommandDispatcher;
import immersive_aircraft.Main;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.CommandSourceStack;

@Mod.EventBusSubscriber
public class ForgeCommandRegistration {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        Main.registerCommands(dispatcher);
    }
}
