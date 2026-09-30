package ghostrunner.api;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GhostrunnerCommand {

    private GhostrunnerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ghostrunner")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))

                // /ghostrunner set true|false
                .then(Commands.literal("set")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ((GhostrunnerState.GhostrunnerStateAccessor) player)
                                            .ghostrunner$setAscended(value);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Ghostrunner] ascended = " + value)
                                                    .withStyle(ChatFormatting.AQUA),
                                            false);
                                    return 1;
                                })))

                // /ghostrunner clear
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ((GhostrunnerState.GhostrunnerStateAccessor) player)
                                    .ghostrunner$setAscended(false);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Ghostrunner] 标记已清除")
                                            .withStyle(ChatFormatting.YELLOW),
                                    false);
                            return 1;
                        })));
    }
}