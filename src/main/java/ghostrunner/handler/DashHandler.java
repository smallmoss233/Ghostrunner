package ghostrunner.handler;

import ghostrunner.api.WallRunState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DashHandler {

    private DashHandler() {}

    // ============ 手感参数 ============

    public static final double DASH_SPEED = 1.20;
    public static final double DASH_UPWARD = 0.08;
    public static final int DASH_COOLDOWN = 20;

    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    // ================================================================

    public static void tickCooldowns() {
        cooldowns.replaceAll((uuid, t) -> Math.max(0, t - 1));
    }

    public static boolean isOnCooldown(PlayerEntity player) {
        Integer t = cooldowns.get(player.getUuid());
        return t != null && t > 0;
    }

    /**
     * 冲刺。
     * <p>方向由客户端传来的 WASD 按键状态决定：
     * <ul>
     *   <li>无方向键 → 朝视线前方冲</li>
     *   <li>WASD 任意组合 → 朝该方向（相对玩家视角）冲</li>
     * </ul>
     */
    public static void tryDash(ServerPlayerEntity player,
                               boolean forward, boolean back,
                               boolean left, boolean right) {
        if (isOnCooldown(player)) return;
        if (player.isTouchingWater() || player.isInLava()) return;
        if (player.isFallFlying()) return;
        if (player.isSneaking()) return;

        Vec3d direction = computeDashDirection(player, forward, back, left, right);
        if (direction == null) return;

        player.setVelocity(
                direction.x * DASH_SPEED,
                DASH_UPWARD,
                direction.z * DASH_SPEED);
        player.velocityModified = true;
        player.fallDistance = 0;

        // 冲刺打断跑墙
        if (player instanceof WallRunState state && state.ghostrunner$isWallRunning()) {
            state.ghostrunner$jumpOffWall();
        }

        cooldowns.put(player.getUuid(), DASH_COOLDOWN);
    }

    /**
     * 根据 WASD 按键 + 玩家当前朝向，算出水平单位方向向量。
     * <p>无方向输入时退化为"视线前方"。
     */
    private static Vec3d computeDashDirection(PlayerEntity player,
                                              boolean forward, boolean back,
                                              boolean left, boolean right) {
        // 视线水平前方向
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d lookHoriz = new Vec3d(look.x, 0, look.z);
        if (lookHoriz.lengthSquared() < 0.0001) return null;
        lookHoriz = lookHoriz.normalize();

        // 右手方向 = 前方向绕 Y 轴旋转 -90°：(x, z) → (-z, x)
        Vec3d rightVec = new Vec3d(-lookHoriz.z, 0, lookHoriz.x);

        // 无任何方向键 → 默认前方
        if (!forward && !back && !left && !right) {
            return lookHoriz;
        }

        double dx = 0, dz = 0;
        if (forward) { dx += lookHoriz.x;  dz += lookHoriz.z;  }
        if (back)    { dx -= lookHoriz.x;  dz -= lookHoriz.z;  }
        if (right)   { dx += rightVec.x;   dz += rightVec.z;   }
        if (left)    { dx -= rightVec.x;   dz -= rightVec.z;   }

        Vec3d result = new Vec3d(dx, 0, dz);
        if (result.lengthSquared() < 0.0001) return lookHoriz;
        return result.normalize();
    }
}