package ghostrunner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.math.Direction;

public class GhostrunnerClient implements ClientModInitializer {

    // ============ 客户端跑墙状态（由服务端同步） ============
    public static boolean wallRunning = false;
    public static Direction wallSide = null;

    // ============ 相机倾斜 ============
    /** 目标最大倾斜角（弧度）。约 15 度 */
    public static final float MAX_ROLL = (float) Math.toRadians(15.0);
    /** 平滑插值系数（0~1，越大越快） */
    public static final float ROLL_LERP = 0.15f;
    /** 当前倾斜角，逐 tick 插值 */
    public static float currentRoll = 0.0f;

    private static boolean prevJumpPressed = false;

    @Override
    public void onInitializeClient() {

        // ---- 接收服务端状态 ----
        ClientPlayNetworking.registerGlobalReceiver(Ghostrunner.WALL_RUN_STATE_PACKET,
                (client, handler, buf, sender) -> {
                    boolean running = buf.readBoolean();
                    Direction side = running ? buf.readEnumConstant(Direction.class) : null;

                    client.execute(() -> {
                        wallRunning = running;
                        wallSide = side;
                    });
                });

        // ---- 每 tick：检测跳跃键 + 更新相机 roll ----
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            // 跳跃键上升沿 → 发跳出请求包
            if (client.player != null) {
                boolean pressed = client.options.jumpKey.isPressed();
                if (pressed && !prevJumpPressed) {
                    ClientPlayNetworking.send(Ghostrunner.JUMP_OFF_WALL_PACKET,
                            PacketByteBufs.empty());
                }
                prevJumpPressed = pressed;
            } else {
                prevJumpPressed = false;
            }

            // 计算目标 roll
            float targetRoll = ghostrunner$computeTargetRoll(client);

            // 平滑插值
            currentRoll += (targetRoll - currentRoll) * ROLL_LERP;
        });
    }

    /**
     * 根据玩家当前朝向与墙的方向，决定倾斜方向。
     * <p>墙在玩家左侧 → 左倾（正）；墙在右侧 → 右倾（负）。
     */
    private static float ghostrunner$computeTargetRoll(net.minecraft.client.MinecraftClient client) {
        if (!wallRunning || wallSide == null || client.player == null) {
            return 0.0f;
        }

        float yawRad = (float) Math.toRadians(client.player.getYaw());

        // 玩家视线水平分量
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);

        // 墙相对玩家的方向（从玩家指向墙）
        double wallX = wallSide.getOffsetX();
        double wallZ = wallSide.getOffsetZ();

        // 叉积 y 分量：判断墙在左还是右
        // cross > 0：墙在玩家右侧；cross < 0：墙在玩家左侧
        double cross = lookX * wallZ - lookZ * wallX;

        // 左右符号
        float side = cross > 0 ? -1.0f : 1.0f;

        return side * MAX_ROLL;
    }
}