package ghostrunner;

import ghostrunner.api.GhostrunnerState;
import ghostrunner.gui.GhostrunnerHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Direction;

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
    public static final float ROLL_LERP = 0.15f;
    public static float currentRoll = 0.0f;

    // ============ 冲刺特效 ============
    public static int dashEffectTicks = 0;
    public static final int DASH_EFFECT_DURATION = 8;
    public static long dashEffectSeed = 0L;

    // ============ 子弹时间 ============
    public static boolean inBulletTime = false;

    // ============ 按键状态缓存 ============
    private static boolean prevJumpPressed = false;
    private static boolean prevSprintPressed = false;
    private static boolean prevAttackPressed = false;
    private static boolean prevF = false, prevB = false, prevL = false, prevR = false;

    // ============ 蓄力 ============
    private static int chargeHoldTicks = 0;
    private static boolean chargeStarted = false;
    private static final int CHARGE_THRESHOLD = 4;   // 0.2 秒

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

        // 跑墙状态
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.WALL_RUN_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean running = buf.readBoolean();
                    Direction side = running ? buf.readEnumConstant(Direction.class) : null;
                    client.execute(() -> {
                        wallRunning = running;
                        wallSide = side;
                    });
                });

        // 幽灵行者标记
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.ASCENDED_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean ascended = buf.readBoolean();
                    client.execute(() -> {
                        if (client.player == null) return;
                        GhostrunnerState.GhostrunnerStateAccessor a =
                                (GhostrunnerState.GhostrunnerStateAccessor) client.player;
                        a.ghostrunner$setAscended(ascended);
                    });
                });

        // 耐力
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.STAMINA_PACKET,
                (client, handler, buf, sender) -> {
                    float value = buf.readFloat();
                    client.execute(() -> currentStamina = value);
                });

        // 冲刺特效
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.DASH_SUCCESS_PACKET,
                (client, handler, buf, sender) -> client.execute(() -> {
                    dashEffectTicks = DASH_EFFECT_DURATION;
                    dashEffectSeed = System.nanoTime();
                }));

        // 子弹时间状态
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.BULLET_TIME_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean in = buf.readBoolean();
                    client.execute(() -> {
                        inBulletTime = in;
                        // 服务端结束子弹时间 → 清掉客户端蓄力标记
                        if (!in) {
                            chargeStarted = false;
                            chargeHoldTicks = 0;
                        }
                    });
                });
    }

    // ================================================================
    //                          每 tick
    // ================================================================

    private void onClientTick(net.minecraft.client.MinecraftClient client) {

        if (client.player == null) {
            resetAllKeys();
            return;
        }

        // ---------- 跳跃键 ----------
        boolean jumpPressed = client.options.jumpKey.isPressed();
        if (jumpPressed && !prevJumpPressed) {
            ClientPlayNetworking.send(Ghostrunner.JUMP_OFF_WALL_PACKET,
                    PacketByteBufs.empty());
        }
        prevJumpPressed = jumpPressed;

        // ---------- 攻击键 ----------
        boolean attackPressed = client.options.attackKey.isPressed();
        if (attackPressed && !prevAttackPressed) {
            // 只处理剑类武器；其他物品走原版逻辑
            if (client.player.getMainHandStack().isIn(ItemTags.SWORDS)) {
                ClientPlayNetworking.send(Ghostrunner.ATTACK_PACKET,
                        PacketByteBufs.empty());
            }
        }
        prevAttackPressed = attackPressed;

        // ---------- 冲刺键（长按蓄力） ----------
        handleSprint(client);

        // ---------- 相机倾斜 + 冲刺特效 ----------
        float targetRoll = computeTargetRoll(client);
        currentRoll += (targetRoll - currentRoll) * ROLL_LERP;
        if (dashEffectTicks > 0) dashEffectTicks--;

        // ---------- HUD 透明度插值 ----------
        updateHudAlphas();
    }

    // ================================================================
    //                       冲刺键逻辑
    // ================================================================

    private void handleSprint(net.minecraft.client.MinecraftClient client) {

        boolean sprintPressed = client.options.sprintKey.isPressed();
        boolean f = client.options.forwardKey.isPressed();
        boolean b = client.options.backKey.isPressed();
        boolean l = client.options.leftKey.isPressed();
        boolean r = client.options.rightKey.isPressed();

        if (sprintPressed) {
            if (prevSprintPressed) {
                // 持续按住
                chargeHoldTicks++;

                if (!chargeStarted && chargeHoldTicks >= CHARGE_THRESHOLD) {
                    // 达到阈值 → 发 START
                    sendDashBuf(Ghostrunner.DASH_CHARGE_START_PACKET, f, b, l, r);
                    chargeStarted = true;
                } else if (chargeStarted && inBulletTime) {
                    // 子弹时间中方向变化 → 更新瞄准
                    if (f != prevF || b != prevB || l != prevL || r != prevR) {
                        sendDashBuf(Ghostrunner.DASH_CHARGE_AIM_PACKET, f, b, l, r);
                    }
                }
            } else {
                // 刚按下
                chargeHoldTicks = 0;
                chargeStarted = false;
            }
        } else {
            if (prevSprintPressed) {
                // 刚松开
                if (chargeStarted && inBulletTime) {
                    // 子弹时间激活 → 释放冲刺
                    ClientPlayNetworking.send(Ghostrunner.DASH_CHARGE_RELEASE_PACKET,
                            PacketByteBufs.empty());
                } else {
                    // 未激活 → 普通冲刺（含 START 失败 fallback）
                    sendDashBuf(Ghostrunner.DASH_PACKET, f, b, l, r);
                }
                chargeHoldTicks = 0;
                chargeStarted = false;
            }
        }

        prevSprintPressed = sprintPressed;
        prevF = f; prevB = b; prevL = l; prevR = r;
    }

    private static void sendDashBuf(net.minecraft.util.Identifier packetId,
                                    boolean f, boolean b, boolean l, boolean r) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(f);
        buf.writeBoolean(b);
        buf.writeBoolean(l);
        buf.writeBoolean(r);
        ClientPlayNetworking.send(packetId, buf);
    }

    // ================================================================
    //                       HUD 透明度
    // ================================================================

    private static void updateHudAlphas() {
        // 子弹时间滤镜
        float bulletTarget = inBulletTime ? 1.0f : 0.0f;
        bulletTimeFilterAlpha += (bulletTarget - bulletTimeFilterAlpha) * 0.20f;
        if (Math.abs(bulletTimeFilterAlpha - bulletTarget) < 0.005f) {
            bulletTimeFilterAlpha = bulletTarget;
        }

        // 耐力条（满耐力时淡出）
        boolean showStamina = currentStamina < STAMINA_MAX - 0.5f;
        float staminaTarget = showStamina ? 1.0f : 0.0f;
        staminaBarAlpha += (staminaTarget - staminaBarAlpha) * 0.25f;
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
        prevF = prevB = prevL = prevR = false;
        chargeHoldTicks = 0;
        chargeStarted = false;
    }

    private static float computeTargetRoll(net.minecraft.client.MinecraftClient client) {
        if (!wallRunning || wallSide == null || client.player == null) return 0.0f;

        float yawRad = (float) Math.toRadians(client.player.getYaw());
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        double wallX = wallSide.getOffsetX();
        double wallZ = wallSide.getOffsetZ();

        double cross = lookX * wallZ - lookZ * wallX;
        float side = cross > 0 ? -1.0f : 1.0f;

        return side * MAX_ROLL;
    }
}