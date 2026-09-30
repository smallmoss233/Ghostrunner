package ghostrunner.network;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.handler.ClimbHandler;
import ghostrunner.handler.DashHandler;
import ghostrunner.handler.ParryHandler;
import ghostrunner.handler.SwordHandler;
import ghostrunner.handler.WallRunHandler;
import ghostrunner.network.GhostrunnerNetworking.ActionPayload;
import ghostrunner.network.GhostrunnerNetworking.MovePayload;
import mosslib.api.ServerHandlers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;

/**
 * 所有 C2S 网络包的处理。
 * <p>由 {@link ghostrunner.Ghostrunner#onInitialize} 通过 {@link #register()} 调用。
 * <p>逻辑与 {@link GhostrunnerNetworking} 的 payload 定义一一对应。
 */
public final class GhostrunnerServerHandlers {

    private GhostrunnerServerHandlers() {}

    // ================================================================
    //                      注册
    // ================================================================

    public static void register() {
        ServerHandlers.handle(ActionPayload.TYPE, GhostrunnerServerHandlers::onAction);

        ServerHandlers.of(MovePayload.TYPE)
                .filter(GhostrunnerPlayer::isGhostrunner)
                .handle(GhostrunnerServerHandlers::onMove);
    }

    // ================================================================
    //                      ActionPayload 分发
    // ================================================================

    private static void onAction(ActionPayload payload, ServerPlayer player) {
        switch (payload.action()) {
            case JUMP_OFF_WALL -> onJumpOffWall(player);
            case ATTACK -> onAttack(player);
            case BLOCK_START -> onBlockStart(player);
            case BLOCK_STOP -> onBlockStop(player);
            case DASH_CHARGE_RELEASE -> onChargeRelease(player);
            case BULLET_TIME_EXIT_REQUEST -> onBulletTimeExit(player);
        }
    }

    private static void onJumpOffWall(ServerPlayer player) {
        if (!GhostrunnerPlayer.isGhostrunner(player)) return;

        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
        if (gr.ghostrunner$isWallRunning()) {
            gr.ghostrunner$jumpOffWall();
            return;
        }
        ClimbHandler.tryClimb(player);
    }

    private static void onAttack(ServerPlayer player) {
        if (!GhostrunnerPlayer.isGhostrunner(player)) return;

        // 先尝试弹反，成功就跳过挥砍
        if (ParryHandler.tryParry(player)) return;
        SwordHandler.performSwing(player);
    }

    private static void onBlockStart(ServerPlayer player) {
        if (!GhostrunnerPlayer.isGhostrunner(player)) return;
        if (!player.getMainHandItem().is(ItemTags.SWORDS)) return;

        GhostrunnerPlayer.of(player).ghostrunner$setBlocking(true);
    }

    private static void onBlockStop(ServerPlayer player) {
        GhostrunnerPlayer.of(player).ghostrunner$setBlocking(false);
    }

    private static void onChargeRelease(ServerPlayer player) {
        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
        if (gr.ghostrunner$isInBulletTime()) {
            gr.ghostrunner$exitBulletTimeAndDash();
        }
    }

    private static void onBulletTimeExit(ServerPlayer player) {
        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
        if (gr.ghostrunner$isInBulletTime()) {
            gr.ghostrunner$exitBulletTimeAndDash();
        }
    }

    // ================================================================
    //                      MovePayload 分发
    // ================================================================

    private static void onMove(MovePayload payload, ServerPlayer player) {
        switch (payload.context()) {
            case DASH -> DashHandler.tryDash(player,
                    payload.forward(), payload.back(),
                    payload.left(), payload.right());
            case CHARGE_START -> onChargeStart(player,
                    payload.forward(), payload.back(),
                    payload.left(), payload.right());
            case CHARGE_AIM -> onChargeAim(player,
                    payload.forward(), payload.back(),
                    payload.left(), payload.right());
        }
    }

    private static void onChargeStart(ServerPlayer player,
                                      boolean f, boolean b, boolean l, boolean r) {
        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);

        boolean inAir = !player.onGround() && !WallRunHandler.hasGroundBelow(player);
        if (inAir && gr.ghostrunner$tryEnterBulletTime()) {
            gr.ghostrunner$updateBulletTimeAim(
                    DashHandler.computeAimFromInput(player, f, b, l, r));
        } else {
            DashHandler.tryDash(player, f, b, l, r);
        }
    }

    private static void onChargeAim(ServerPlayer player,
                                    boolean f, boolean b, boolean l, boolean r) {
        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
        if (!gr.ghostrunner$isInBulletTime()) return;
        gr.ghostrunner$updateBulletTimeAim(
                DashHandler.computeAimFromInput(player, f, b, l, r));
    }
}