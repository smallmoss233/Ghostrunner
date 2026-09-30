package ghostrunner;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.gui.GhostrunnerHud;
import ghostrunner.network.GhostrunnerNetworking;
import ghostrunner.network.GhostrunnerNetworking.ActionPayload;
import ghostrunner.network.GhostrunnerNetworking.MovePayload;
import ghostrunner.network.GhostrunnerNetworking.NoticePayload;
import mosslib.api.ClientHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;

public class GhostrunnerClient implements ClientModInitializer {

    // ============ HUD 透明度 ============
    public static float bulletTimeFilterAlpha = 0.0f;
    public static float staminaBarAlpha = 0.0f;

    // ============ 耐力 ============
    public static float currentStamina = 100.0f;
    public static final float STAMINA_MAX = 100.0f;

    // ============ 跑墙状态 ============
    public static boolean wallRunning = false;
    public static Direction wallSide = null;

    // ============ 相机倾斜 ============
    public static final float MAX_ROLL = (float) Math.toRadians(15.0);
    public static final float ROLL_LERP_PER_TICK = 0.15f;
    public static float currentRoll = 0.0f;

    // ============ 冲刺特效 ============
    public static float dashEffectTimer = 0f;
    public static final float DASH_EFFECT_DURATION = 0.4f;
    public static long dashEffectSeed = 0L;

    // ============ 子弹时间 ============
    public static boolean inBulletTime = false;
    /** 进入子弹时间的本地时间戳。0 表示未进入。 */
    private static long btStartedMs = 0L;
    /** 进入时冻结的耐力值。 */
    private static float btStaminaAtStart = 0f;

    // ============ 按键状态 ============
    private static boolean prevJumpPressed = false;
    private static boolean prevSprintPressed = false;
    private static boolean prevAttackPressed = false;
    private static boolean prevRightPressed = false;
    private static boolean prevF = false, prevB = false, prevL = false, prevR = false;

    // ============ 蓄力（按秒计） ============
    private static float chargeHoldSeconds = 0f;
    private static boolean chargeStarted = false;
    private static final float CHARGE_THRESHOLD_SECONDS = 0.2f;

    // ============ 弹反 ============
    public static float parryPoseTimer = 0f;
    public static final float PARRY_POSE_DURATION = 0.3f;
    public static float parryFlashAlpha = 0.0f;

    // ============ 帧时间追踪 ============
    private static long lastFrameNanos = 0L;
    private static final float MAX_FRAME_DT = 0.5f;
    private static boolean bulletTimeExitRequested = false;

    @Override
    public void onInitializeClient() {
        GhostrunnerKeys.register();
        GhostrunnerHud.register();
        registerReceivers();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    // ================================================================
    //                       网络包接收
    // ================================================================

    private void registerReceivers() {

        // ---------- 跑墙状态 ----------
        ClientHandlers.handle(
                GhostrunnerNetworking.WallRunStatePayload.TYPE,
                payload -> {
                    wallRunning = payload.running();
                    wallSide = payload.running()
                            ? Direction.values()[payload.wallSideOrdinal()]
                            : null;
                });

        // ---------- 改造标记 ----------
        ClientHandlers.handle(
                GhostrunnerNetworking.AscendedStatePayload.TYPE,
                payload -> {
                    Minecraft client = Minecraft.getInstance();
                    if (client.player == null) return;
                    GhostrunnerPlayer.of(client.player)
                            .ghostrunner$setAscended(payload.ascended());
                });

        // ---------- 耐力同步 ----------
        ClientHandlers.handle(
                GhostrunnerNetworking.StaminaPayload.TYPE,
                payload -> {
                    // 子弹时间期间忽略服务端耐力（本地用公式预测）
                    if (!inBulletTime) {
                        currentStamina = payload.stamina();
                    }
                });

        // ---------- 子弹时间状态 ----------
        ClientHandlers.handle(
                GhostrunnerNetworking.BulletTimeStatePayload.TYPE,
                payload -> {
                    inBulletTime = payload.active();

                    Minecraft client = Minecraft.getInstance();
                    if (client.player != null) {
                        GhostrunnerPlayer.of(client.player)
                                .ghostrunner$data().inBulletTime = payload.active();
                    }

                    if (inBulletTime) {
                        btStartedMs = System.currentTimeMillis();
                        btStaminaAtStart = currentStamina;
                    } else {
                        currentStamina = payload.stamina();
                        btStartedMs = 0L;
                        btStaminaAtStart = 0f;
                        chargeStarted = false;
                        chargeHoldSeconds = 0f;
                        bulletTimeExitRequested = false;
                    }
                });

        // ---------- 通知（冲刺成功 / 弹反成功） ----------
        ClientHandlers.handle(
                NoticePayload.TYPE,
                payload -> {
                    switch (payload.notice()) {
                        case DASH_SUCCESS -> {
                            dashEffectTimer = DASH_EFFECT_DURATION;
                            dashEffectSeed = System.nanoTime();
                        }
                        case PARRY_SUCCESS -> {
                            parryPoseTimer = PARRY_POSE_DURATION;
                            parryFlashAlpha = 1.0f;
                        }
                    }
                });
    }

    // ================================================================
    //                     tick：只做兜底清理
    // ================================================================

    private void onClientTick(Minecraft client) {
        if (client.player == null) {
            resetAllKeys();
        }
    }

    // ================================================================
    //                   每帧：输入 + 视觉 + 计时
    // ================================================================

    /**
     * 由 {@link GhostrunnerHud} 每帧调用。
     * <p>客户端 tick 慢到 2Hz 时，tick 里做输入检测会有 500ms 延迟，
     * 所以全部挪到帧级（60fps 时 16ms）。
     */
    public static void onFrame() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            lastFrameNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }
        float dtSec = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;

        if (dtSec > MAX_FRAME_DT) dtSec = MAX_FRAME_DT;

        // ---- 输入检测 ----
        handleFrameInput(client, dtSec);

        // ---- 视觉状态 ----
        float tickEq = dtSec * 20f;

        // 相机倾斜
        float targetRoll = computeTargetRoll(client);
        float rollLerp = Math.min(1.0f, ROLL_LERP_PER_TICK * tickEq);
        currentRoll += (targetRoll - currentRoll) * rollLerp;

        // 子弹时间耐力预测（时间戳公式，与服务端一致）
        if (inBulletTime && btStartedMs != 0L) {
            double elapsedSec = (System.currentTimeMillis() - btStartedMs) / 1000.0;
            float consumed = (float) (elapsedSec * GhostrunnerConfig.BT_STAMINA_PER_SECOND);
            currentStamina = Math.max(0f, btStaminaAtStart - consumed);
        }

        // 耐力耗尽 → 通知服务端退出
        if (inBulletTime && currentStamina <= 0f && !bulletTimeExitRequested) {
            sendAction(ActionPayload.Action.BULLET_TIME_EXIT_REQUEST);
            bulletTimeExitRequested = true;
        }

        updateHudAlphas(tickEq);

        if (parryPoseTimer > 0f) {
            parryPoseTimer -= dtSec;
            if (parryPoseTimer < 0f) parryPoseTimer = 0f;
        }

        if (parryFlashAlpha > 0f) {
            parryFlashAlpha -= 0.15f * tickEq;
            if (parryFlashAlpha < 0f) parryFlashAlpha = 0f;
        }

        if (dashEffectTimer > 0f) {
            dashEffectTimer -= dtSec;
            if (dashEffectTimer < 0f) dashEffectTimer = 0f;
        }
    }

    // ================================================================
    //                       帧级输入处理
    // ================================================================

    private static void handleFrameInput(Minecraft client, float dtSec) {
        boolean isSword = client.player.getMainHandItem().is(ItemTags.SWORDS);

        // ---------- 跳跃 / 跳出跑墙 / 爬墙 ----------
        boolean jumpPressed = client.options.keyJump.isDown();
        if (jumpPressed && !prevJumpPressed) {
            sendAction(ActionPayload.Action.JUMP_OFF_WALL);
        }
        prevJumpPressed = jumpPressed;

        // ---------- 攻击 / 弹反 ----------
        boolean attackPressed = client.options.keyAttack.isDown();
        if (attackPressed && !prevAttackPressed && isSword) {
            sendAction(ActionPayload.Action.ATTACK);
        }
        prevAttackPressed = attackPressed;

        // ---------- 右键格挡 ----------
        boolean rightPressed = client.options.keyUse.isDown();
        if (rightPressed && !prevRightPressed && isSword) {
            sendAction(ActionPayload.Action.BLOCK_START);
        } else if (prevRightPressed && (!rightPressed || !isSword)) {
            sendAction(ActionPayload.Action.BLOCK_STOP);
        }
        prevRightPressed = rightPressed;

        // ---------- 冲刺（含蓄力） ----------
        handleSprintFrame(client, dtSec);
    }

    private static void handleSprintFrame(Minecraft client, float dtSec) {
        boolean sprintPressed = client.options.keySprint.isDown();
        boolean f = client.options.keyUp.isDown();
        boolean b = client.options.keyDown.isDown();
        boolean l = client.options.keyLeft.isDown();
        boolean r = client.options.keyRight.isDown();

        if (sprintPressed) {
            if (prevSprintPressed) {
                chargeHoldSeconds += dtSec;

                if (!chargeStarted && chargeHoldSeconds >= CHARGE_THRESHOLD_SECONDS) {
                    sendMove(MovePayload.Context.CHARGE_START, f, b, l, r);
                    chargeStarted = true;
                } else if (chargeStarted && inBulletTime) {
                    if (f != prevF || b != prevB || l != prevL || r != prevR) {
                        sendMove(MovePayload.Context.CHARGE_AIM, f, b, l, r);
                    }
                }
            } else {
                chargeHoldSeconds = 0f;
                chargeStarted = false;
            }
        } else {
            if (prevSprintPressed) {
                if (chargeStarted && inBulletTime) {
                    sendAction(ActionPayload.Action.DASH_CHARGE_RELEASE);
                } else {
                    sendMove(MovePayload.Context.DASH, f, b, l, r);
                }
                chargeHoldSeconds = 0f;
                chargeStarted = false;
            }
        }

        prevSprintPressed = sprintPressed;
        prevF = f; prevB = b; prevL = l; prevR = r;
    }

    // ================================================================
    //                       发送辅助
    // ================================================================

    private static void sendAction(ActionPayload.Action action) {
        ClientPlayNetworking.send(new ActionPayload(action));
    }

    private static void sendMove(MovePayload.Context ctx,
                                 boolean f, boolean b, boolean l, boolean r) {
        ClientPlayNetworking.send(new MovePayload(ctx, f, b, l, r));
    }

    // ================================================================
    //                       HUD 透明度
    // ================================================================

    private static void updateHudAlphas(float tickEq) {
        float bulletTarget = inBulletTime ? 1.0f : 0.0f;
        float bulletLerp = Math.min(1.0f, 0.20f * tickEq);
        bulletTimeFilterAlpha += (bulletTarget - bulletTimeFilterAlpha) * bulletLerp;
        if (Math.abs(bulletTimeFilterAlpha - bulletTarget) < 0.005f) {
            bulletTimeFilterAlpha = bulletTarget;
        }

        boolean showStamina = currentStamina < STAMINA_MAX - 0.5f;
        float staminaTarget = showStamina ? 1.0f : 0.0f;
        float staminaLerp = Math.min(1.0f, 0.25f * tickEq);
        staminaBarAlpha += (staminaTarget - staminaBarAlpha) * staminaLerp;
        if (Math.abs(staminaBarAlpha - staminaTarget) < 0.005f) {
            staminaBarAlpha = staminaTarget;
        }
    }

    // ================================================================
    //                       工具方法
    // ================================================================

    private static void resetAllKeys() {
        prevJumpPressed = false;
        prevSprintPressed = false;
        prevAttackPressed = false;
        prevRightPressed = false;
        prevF = prevB = prevL = prevR = false;
        chargeHoldSeconds = 0f;
        chargeStarted = false;
        lastFrameNanos = 0L;
    }

    private static float computeTargetRoll(Minecraft client) {
        if (!wallRunning || wallSide == null || client.player == null) return 0.0f;

        float yawRad = (float) Math.toRadians(client.player.getYRot());
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        double wallX = wallSide.getStepX();
        double wallZ = wallSide.getStepZ();

        double cross = lookX * wallZ - lookZ * wallX;
        float side = cross > 0 ? -1.0f : 1.0f;

        return side * MAX_ROLL;
    }
}