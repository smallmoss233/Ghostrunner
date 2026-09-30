package ghostrunner;

import ghostrunner.api.GhostrunnerState;
import ghostrunner.gui.GhostrunnerHud;
import ghostrunner.network.GhostrunnerNetworking;
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
    /** 每 tick 的插值率（相当于 20Hz 下 0.15） */
    public static final float ROLL_LERP_PER_TICK = 0.15f;
    public static float currentRoll = 0.0f;

    // ============ 冲刺特效 ============
    /** 剩余秒数 */
    public static float dashEffectTimer = 0f;
    /** 持续秒数（0.4 秒 = 原 8 tick） */
    public static final float DASH_EFFECT_DURATION = 0.4f;
    public static long dashEffectSeed = 0L;

    // ============ 子弹时间 ============
    public static boolean inBulletTime = false;

    // ============ 按键状态 ============
    private static boolean prevJumpPressed = false;
    private static boolean prevSprintPressed = false;
    private static boolean prevAttackPressed = false;
    private static boolean prevRightPressed = false;
    private static boolean prevF = false, prevB = false, prevL = false, prevR = false;

    // ============ 蓄力（按秒计） ============
    private static float chargeHoldSeconds = 0f;
    private static boolean chargeStarted = false;
    /** 0.2 秒 = 原 4 tick */
    private static final float CHARGE_THRESHOLD_SECONDS = 0.2f;

    /** 弹反举剑姿势剩余秒数 */
    public static float parryPoseTimer = 0f;
    /** 弹反姿势总时长（秒）。0.3 秒 = 原 6 tick */
    public static final float PARRY_POSE_DURATION = 0.3f;

    /** 弹反闪光透明度（0~1） */
    public static float parryFlashAlpha = 0.0f;

    // ============ 帧时间追踪 ============
    private static long lastFrameNanos = 0L;
    /** 单帧最大处理时间，防止暂停恢复后一次跳太多 */
    private static final float MAX_FRAME_DT = 0.5f;
    private static boolean bulletTimeExitRequested = false;

    @Override
    public void onInitializeClient() {
        GhostrunnerKeys.register();
        GhostrunnerHud.register();
        registerReceivers();

        // tick 只做兜底，主逻辑在 onFrame
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    // ================================================================
    //                       网络包接收
    // ================================================================

    private void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.WallRunStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    wallRunning = payload.running();
                    wallSide = payload.running()
                            ? Direction.values()[payload.wallSideOrdinal()]
                            : null;
                }));

        ClientPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.AscendedStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().player == null) return;
                    GhostrunnerState.GhostrunnerStateAccessor a =
                            (GhostrunnerState.GhostrunnerStateAccessor) context.client().player;
                    a.ghostrunner$setAscended(payload.ascended());
                }));

        ClientPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.StaminaPayload.TYPE,
                (payload, context) -> context.client().execute(() ->
                        currentStamina = payload.stamina()));

        ClientPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.DashSuccessPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    dashEffectTimer = DASH_EFFECT_DURATION;
                    dashEffectSeed = System.nanoTime();
                }));

        ClientPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.BulletTimeStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    inBulletTime = payload.inBulletTime();
                    if (!inBulletTime) {
                        chargeStarted = false;
                        chargeHoldSeconds = 0f;
                        bulletTimeExitRequested = false;   // ★ 重置标记
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.ParrySuccessPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    parryPoseTimer = PARRY_POSE_DURATION;
                    parryFlashAlpha = 1.0f;
                }));
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

        // 子弹时间期间客户端预测耐力消耗（视觉平滑）
        if (inBulletTime) {
            currentStamina = Math.max(0f, currentStamina - 20.0f * dtSec);
        }

        // ★ 耐力耗尽 → 通知服务端退出子弹时间
        if (inBulletTime && currentStamina <= 0f && !bulletTimeExitRequested) {
            ClientPlayNetworking.send(
                    new GhostrunnerNetworking.BulletTimeExitRequestPayload());
            bulletTimeExitRequested = true;
        }

        // HUD alpha
        updateHudAlphas(tickEq);

        // 弹反姿势
        if (parryPoseTimer > 0f) {
            parryPoseTimer -= dtSec;
            if (parryPoseTimer < 0f) parryPoseTimer = 0f;
        }

        // 弹反闪光
        if (parryFlashAlpha > 0f) {
            parryFlashAlpha -= 0.15f * tickEq;
            if (parryFlashAlpha < 0f) parryFlashAlpha = 0f;
        }

        // 冲刺特效
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

        // ---------- 跳跃 ----------
        boolean jumpPressed = client.options.keyJump.isDown();
        if (jumpPressed && !prevJumpPressed) {
            ClientPlayNetworking.send(new GhostrunnerNetworking.JumpOffWallPayload());
        }
        prevJumpPressed = jumpPressed;

        // ---------- 攻击 ----------
        boolean attackPressed = client.options.keyAttack.isDown();
        if (attackPressed && !prevAttackPressed && isSword) {
            ClientPlayNetworking.send(new GhostrunnerNetworking.AttackPayload());
        }
        prevAttackPressed = attackPressed;

        // ---------- 右键格挡 ----------
        boolean rightPressed = client.options.keyUse.isDown();
        if (rightPressed && !prevRightPressed && isSword) {
            ClientPlayNetworking.send(new GhostrunnerNetworking.BlockStartPayload());
        } else if (prevRightPressed && (!rightPressed || !isSword)) {
            ClientPlayNetworking.send(new GhostrunnerNetworking.BlockStopPayload());
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
                    ClientPlayNetworking.send(
                            new GhostrunnerNetworking.DashChargeStartPayload(f, b, l, r));
                    chargeStarted = true;
                } else if (chargeStarted && inBulletTime) {
                    if (f != prevF || b != prevB || l != prevL || r != prevR) {
                        ClientPlayNetworking.send(
                                new GhostrunnerNetworking.DashChargeAimPayload(f, b, l, r));
                    }
                }
            } else {
                chargeHoldSeconds = 0f;
                chargeStarted = false;
            }
        } else {
            if (prevSprintPressed) {
                if (chargeStarted && inBulletTime) {
                    ClientPlayNetworking.send(
                            new GhostrunnerNetworking.DashChargeReleasePayload());
                } else {
                    ClientPlayNetworking.send(
                            new GhostrunnerNetworking.DashPayload(f, b, l, r));
                }
                chargeHoldSeconds = 0f;
                chargeStarted = false;
            }
        }

        prevSprintPressed = sprintPressed;
        prevF = f; prevB = b; prevL = l; prevR = r;
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