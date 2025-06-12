package immersive_aircraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import immersive_aircraft.entity.VehicleEntity;

public class SetAircraftPilotLimitCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aircraftpilotlimit")
            .requires(source -> source.hasPermission(2) && hasLuckPermsPermission(source, "flowframe.command.aircraftpilotlimit"))
            .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                .executes(SetAircraftPilotLimitCommand::setLimit)));
    }

    private static int setLimit(CommandContext<CommandSourceStack> ctx) {
        int limit = IntegerArgumentType.getInteger(ctx, "limit");
        VehicleEntity.setAircraftPilotLimit(limit);
        ctx.getSource().sendSuccess(() -> Component.literal("Set aircraft pilot limit to " + limit), true);
        return 1;
    }

    // LuckPerms permission check stub (replace with actual LuckPerms API if available)
    private static boolean hasLuckPermsPermission(CommandSourceStack source, String permission) {
        // TODO: Integrate with LuckPerms API for real permission check
        return true; // Always allow for now
    }
}
