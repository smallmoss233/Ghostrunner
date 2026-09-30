package ghostrunner.handler;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.data.GhostrunnerData;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 冲刺。
 * <p>三件事：
 * <ul>
 *   <li>方向计算（WASD 相对视角）</li>
 *   <li>速度注入 + 衰减窗口</li>
 *   <li>冷却管理</li>
 * </ul>
 * <p>耐力消耗、空中标记、跑墙窗口都直接操作 {@link GhostrunnerData}。
 */
public final class DashHandler {

    private DashHandler() {}

    /** 冷却追踪。tick 由 {@code GhostrunnerServerEvents} 统一调用。 */
    private static final CooldownTracker COOLDOWNS = new CooldownTracker();

    // ================================================================
    //                          冷却 API
    // ================================================================

    public static void tickCooldowns() {
        COOLDOWNS.tickAll();
    }

    public static boolean isOnCooldown(Player player) {
        return COOLDOWNS.isOnCooldown(player.getUUID());
    }

    // ================================================================
    //                          冲刺
    // ================================================================

    /**
     * 冲刺。方向由客户端传来的 WASD 按键状态决定。
     */
    public static void tryDash(ServerPlayer player,
                               boolean forward, boolean back,
                               boolean left, boolean right) {
        // ---- 前置检查 ----
        if (isOnCooldown(player)) return;
        if (player.isInWater() || player.isInLava()) return;
        if (player.isFallFlying()) return;
        if (player.isShiftKeyDown()) return;

        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
        GhostrunnerData data = gr.ghostrunner$data();

        boolean inAir = !player.onGround() && !WallRunHandler.hasGroundBelow(player);
        if (inAir && data.airDashUsed) return;
        if (data.stamina < GhostrunnerConfig.STAMINA_PER_DASH) return;

        // ---- 方向 ----
        Vec3 direction = computeDashDirection(player, forward, back, left, right);
        if (direction == null) return;

        // ---- 注入冲刺状态 ----
        data.wallRunCooldown = 0;
        data.dashWindowTicks = GhostrunnerConfig.DASH_WALL_WINDOW_TICKS;
        data.dashDirection = new Vec3(direction.x, 0, direction.z).normalize();

        // 跑墙中 → 先跳出
        if (data.wallRunning) {
            gr.ghostrunner$jumpOffWall();
        }

        // ---- 速度 ----
        MotionSync.setAndSync(player,
                direction.x * GhostrunnerConfig.DASH_SPEED,
                GhostrunnerConfig.DASH_UPWARD,
                direction.z * GhostrunnerConfig.DASH_SPEED);
        player.fallDistance = 0;

        // ---- 音效 ----
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8f, 1.2f);

        // ---- 后置状态 ----
        data.dashDecayTicks = GhostrunnerConfig.DASH_DECAY_TICKS;
        data.stamina = Math.max(0f, data.stamina - GhostrunnerConfig.STAMINA_PER_DASH);
        if (inAir) data.airDashUsed = true;

        COOLDOWNS.set(player.getUUID(), GhostrunnerConfig.DASH_COOLDOWN);

        // ---- 通知客户端 ----
        ServerPlayNetworking.send(player,
                new GhostrunnerNetworking.NoticePayload(
                        GhostrunnerNetworking.NoticePayload.Notice.DASH_SUCCESS));
    }

    // ================================================================
    //                          方向计算
    // ================================================================

    /**
     * 水平冲刺方向（单位向量，y=0）。
     * <p>无输入时用视线方向。输入与视线相反时退化也用视线方向。
     *
     * @return null 表示视线太垂直无法投影
     */
    public static Vec3 computeDashDirection(Player player,
                                            boolean forward, boolean back,
                                            boolean left, boolean right) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 lookHoriz = new Vec3(look.x, 0, look.z);
        if (lookHoriz.lengthSqr() < GhostrunnerConfig.VEC_EPSILON_SQ) return null;
        lookHoriz = lookHoriz.normalize();

        if (!forward && !back && !left && !right) return lookHoriz;

        Vec3 result = accumulateHorizontal(lookHoriz, forward, back, left, right);
        if (result.lengthSqr() < GhostrunnerConfig.VEC_EPSILON_SQ) return lookHoriz;
        return result.normalize();
    }

    /**
     * 完整瞄准方向（含 Y 分量）。子弹时间释放时用。
     * <p>无输入时用完整视线方向。有输入时以水平输入 + 视线 Y 为分量。
     */
    public static Vec3 computeAimFromInput(Player player,
                                           boolean forward, boolean back,
                                           boolean left, boolean right) {
        Vec3 look = player.getViewVector(1.0F);
        if (!forward && !back && !left && !right) {
            return look.normalize();
        }

        Vec3 lookHoriz = new Vec3(look.x, 0, look.z).normalize();
        Vec3 result = accumulateHorizontal(lookHoriz, forward, back, left, right);

        // 输入水平方向与视线相反时退化用视线
        if (result.lengthSqr() < GhostrunnerConfig.VEC_EPSILON_SQ) {
            return look.normalize();
        }

        // 拼上视线 Y 分量
        return new Vec3(result.x, look.y, result.z).normalize();
    }

    /**
     * 把 WASD 输入累积成水平方向（未归一化）。
     * <p>视角右向量 = lookHoriz 顺时针 90°。
     */
    private static Vec3 accumulateHorizontal(Vec3 lookHoriz,
                                             boolean forward, boolean back,
                                             boolean left, boolean right) {
        Vec3 rightVec = new Vec3(-lookHoriz.z, 0, lookHoriz.x);

        double dx = 0, dz = 0;
        if (forward) { dx += lookHoriz.x; dz += lookHoriz.z; }
        if (back)    { dx -= lookHoriz.x; dz -= lookHoriz.z; }
        if (right)   { dx += rightVec.x;  dz += rightVec.z;  }
        if (left)    { dx -= rightVec.x;  dz -= rightVec.z;  }

        return new Vec3(dx, 0, dz);
    }
}