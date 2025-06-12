package immersive_aircraft.fabric;

import com.mojang.brigadier.CommandDispatcher;
import immersive_aircraft.Main;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class FabricCommandRegistration {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(FabricCommandRegistration::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, Object registryAccess, Object server) {
        Main.registerCommands(dispatcher);
    }
}
