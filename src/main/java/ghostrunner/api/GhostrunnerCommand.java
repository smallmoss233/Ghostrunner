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

                // /ghostrunner set <true|false>
                .then(Commands.literal("set")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean value = BoolArgumentType.getBool(ctx, "value");
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    GhostrunnerPlayer.of(player).ghostrunner$setAscended(value);
                                    sendSuccess(ctx, "ascended = " + value, ChatFormatting.AQUA);
                                    return 1;
                                })))

                // /ghostrunner clear
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            GhostrunnerPlayer.of(player).ghostrunner$setAscended(false);
                            sendSuccess(ctx, "标记已清除", ChatFormatting.YELLOW);
                            return 1;
                        }))

                // /ghostrunner check
                .then(Commands.literal("check")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean ascended = GhostrunnerPlayer.of(player).ghostrunner$isAscended();
                            sendSuccess(ctx, "ascended = " + ascended,
                                    ascended ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                            return 1;
                        })));
    }

    // ================================================================
    //                          工具
    // ================================================================

    private static void sendSuccess(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                    String message,
                                    ChatFormatting color) {
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Ghostrunner] " + message).withStyle(color),
                false);
    }
}