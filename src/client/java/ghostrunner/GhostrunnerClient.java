package ghostrunner;

import ghostrunner.api.GhostrunnerState;
import ghostrunner.gui.GhostrunnerHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Direction;

public class GhostrunnerClient implements ClientModInitializer {

    // 耐力
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

    // ============ 按键状态 ============
    private static boolean prevJumpPressed = false;
    private static boolean prevSprintPressed = false;
    private static boolean prevF = false, prevB = false, prevL = false, prevR = false;

    // ============ 子弹时间 ============
    public static boolean inBulletTime = false;
    private static int chargeHoldTicks = 0;
    private static boolean chargeStarted = false;
    private static final int CHARGE_THRESHOLD = 6;

    @Override
    public void onInitializeClient() {

        GhostrunnerKeys.register();
        GhostrunnerHud.register();

        // ---- 接收跑墙状态 ----
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.WALL_RUN_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean running = buf.readBoolean();
                    Direction side = running ? buf.readEnumConstant(Direction.class) : null;
                    client.execute(() -> {
                        wallRunning = running;
                        wallSide = side;
                    });
                });

        // ---- 接收标记 ----
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.ASCENDED_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean ascended = buf.readBoolean();
                    client.execute(() -> {
                        GhostrunnerState.GhostrunnerStateAccessor a =
                                (GhostrunnerState.GhostrunnerStateAccessor) client.player;
                        if (a != null) a.ghostrunner$setAscended(ascended);
                    });
                });

        // ---- 接收耐力 ----
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.STAMINA_PACKET,
                (client, handler, buf, sender) -> {
                    float value = buf.readFloat();
                    client.execute(() -> currentStamina = value);
                });

        // ---- 接收冲刺特效 ----
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.DASH_SUCCESS_PACKET,
                (client, handler, buf, sender) -> {
                    client.execute(() -> {
                        dashEffectTicks = DASH_EFFECT_DURATION;
                        dashEffectSeed = System.nanoTime();
                    });
                });

        // ---- 接收子弹时间状态 ----
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.BULLET_TIME_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean in = buf.readBoolean();
                    client.execute(() -> {
                        inBulletTime = in;
                        // ★ 服务端结束子弹时间 → 清掉客户端蓄力标记
                        if (!in) {
                            chargeStarted = false;
                            chargeHoldTicks = 0;
                        }
                    });
                });

        // ============================================================
        //                       每 tick
        // ============================================================
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null) {
                resetAllKeys();
                return;
            }

            // 跳跃
            boolean jumpPressed = client.options.jumpKey.isPressed();
            if (jumpPressed && !prevJumpPressed) {
                ClientPlayNetworking.send(Ghostrunner.JUMP_OFF_WALL_PACKET,
                        PacketByteBufs.empty());
            }
            prevJumpPressed = jumpPressed;

            // ============ 冲刺键长按逻辑 ============
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
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeBoolean(f); buf.writeBoolean(b);
                        buf.writeBoolean(l); buf.writeBoolean(r);
                        ClientPlayNetworking.send(Ghostrunner.DASH_CHARGE_START_PACKET, buf);
                        chargeStarted = true;
                    } else if (chargeStarted && inBulletTime) {
                        // 子弹时间中，方向变化 → 更新瞄准
                        if (f != prevF || b != prevB || l != prevL || r != prevR) {
                            PacketByteBuf buf = PacketByteBufs.create();
                            buf.writeBoolean(f); buf.writeBoolean(b);
                            buf.writeBoolean(l); buf.writeBoolean(r);
                            ClientPlayNetworking.send(Ghostrunner.DASH_CHARGE_AIM_PACKET, buf);
                        }
                    }
                } else {
                    // 刚按下
                    chargeHoldTicks = 0;
                    chargeStarted = false;
                }
            } else {
                if (prevSprintPressed) {
                    // ★ 刚松开
                    if (chargeStarted && inBulletTime) {
                        // 子弹时间激活 → 释放冲刺
                        ClientPlayNetworking.send(Ghostrunner.DASH_CHARGE_RELEASE_PACKET,
                                PacketByteBufs.empty());
                    } else {
                        // 未激活子弹时间 → 普通冲刺（含 START 失败 fallback）
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeBoolean(f); buf.writeBoolean(b);
                        buf.writeBoolean(l); buf.writeBoolean(r);
                        ClientPlayNetworking.send(Ghostrunner.DASH_PACKET, buf);
                    }
                    chargeHoldTicks = 0;
                    chargeStarted = false;
                }
            }
            prevSprintPressed = sprintPressed;
            prevF = f; prevB = b; prevL = l; prevR = r;

            // 相机倾斜 + 冲刺特效
            float targetRoll = ghostrunner$computeTargetRoll(client);
            currentRoll += (targetRoll - currentRoll) * ROLL_LERP;
            if (dashEffectTicks > 0) dashEffectTicks--;
        });
    }

    private static void resetAllKeys() {
        prevJumpPressed = false;
        prevSprintPressed = false;
        chargeHoldTicks = 0;
        chargeStarted = false;
        prevF = prevB = prevL = prevR = false;
    }

    private static float ghostrunner$computeTargetRoll(net.minecraft.client.MinecraftClient client) {
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