package ghostrunner.api;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GhostrunnerCommand {

    private GhostrunnerCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ghostrunner")
                .requires(src -> src.hasPermissionLevel(2))

                // /ghostrunner set true|false
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    ((GhostrunnerState.GhostrunnerStateAccessor) player)
                                            .ghostrunner$setAscended(value);
                                    ctx.getSource().sendFeedback(
                                            () -> Text.literal("[Ghostrunner] ascended = " + value)
                                                    .formatted(Formatting.AQUA),
                                            false);
                                    return 1;
                                })))

                // /ghostrunner clear
                .then(CommandManager.literal("clear")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            ((GhostrunnerState.GhostrunnerStateAccessor) player)
                                    .ghostrunner$setAscended(false);
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("[Ghostrunner] 标记已清除")
                                            .formatted(Formatting.YELLOW),
                                    false);
                            return 1;
                        })));
    }
}