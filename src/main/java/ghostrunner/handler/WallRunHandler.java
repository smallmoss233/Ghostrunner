package ghostrunner.handler;

import ghostrunner.api.GRTags;
import ghostrunner.config.GhostrunnerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 跑墙。
 * <p>三组职责：
 * <ol>
 *   <li><b>墙面探测</b>——{@link #findWall} 全方向，{@link #findWallInDirection} 单方向</li>
 *   <li><b>进入条件</b>——{@link #canEnter} / {@link #isMovingTowardWall} / {@link #isApproachingWallAtAngle}</li>
 *   <li><b>方向与跳出</b>——{@link #computeLockedDirection} / {@link #velocityFromLockedDirection} / {@link #jumpOffWall}</li>
 * </ol>
 * <p><b>性能约定</b>：{@code findWall} 是全方向扫描（进入时用），
 * {@code findWallInDirection} 是单方向探测（跑墙维持时用）。
 * 跑墙 tick 里应优先用单方向版本，避免每 tick 全扫。
 */
public final class WallRunHandler {

    private WallRunHandler() {}

    // ================================================================
    //                          墙面探测
    // ================================================================

    /**
     * 全方向探测：返回玩家当前紧贴的墙面方向；没有返回 null。
     * <p>用于"尝试进入跑墙"。
     */
    public static Direction findWall(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox().deflate(0.001);

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (probeWall(level, box, dir)) return dir;
        }
        return null;
    }

    /**
     * 单方向探测：玩家是否仍然贴着指定方向的墙。
     * <p>用于"跑墙维持"——只探测当前墙面，避免每 tick 全方向扫描。
     */
    public static boolean findWallInDirection(Player player, Direction dir) {
        if (dir.getAxis() == Direction.Axis.Y) return false;
        Level level = player.level();
        AABB box = player.getBoundingBox().deflate(0.001);
        return probeWall(level, box, dir);
    }

    /**
     * 探测一个方向。检查沿该方向偏移 {@code WALL_PROBE} 后的 AABB
     * 是否与任何非黑名单方块的碰撞体相交。
     */
    private static boolean probeWall(Level level, AABB box, Direction dir) {
        AABB probe = box.move(
                dir.getStepX() * GhostrunnerConfig.WALL_PROBE,
                0,
                dir.getStepZ() * GhostrunnerConfig.WALL_PROBE);

        BlockPos min = BlockPos.containing(probe.minX, probe.minY, probe.minZ);
        BlockPos max = BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ);

        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(p);
            if (state.is(GRTags.WALL_RUN_BLACKLIST)) continue;
            if (state.getCollisionShape(level, p).isEmpty()) continue;
            return true;
        }
        return false;
    }

    /**
     * 玩家脚下是否踩着能站立的方块。
     * <p>用碰撞形状判断（和墙面探测一致），而不是 {@code isSolidRender}——
     * 后者对台阶、栅栏等判定太严格。
     */
    public static boolean hasGroundBelow(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        AABB probe = new AABB(
                box.minX + 0.001, box.minY - 0.06, box.minZ + 0.001,
                box.maxX - 0.001, box.minY,        box.maxZ - 0.001);

        BlockPos min = BlockPos.containing(probe.minX, probe.minY, probe.minZ);
        BlockPos max = BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ);

        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            if (!state.getCollisionShape(level, p).isEmpty()) return true;
        }
        return false;
    }

    // ================================================================
    //                          进入条件
    // ================================================================

    /**
     * 玩家状态是否允许进入跑墙。
     * <p>只检查"玩家自身"的条件——墙面和方向由调用方另行判定。
     */
    public static boolean canEnter(Player player) {
        if (player.onGround()) return false;
        if (hasGroundBelow(player)) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.isFallFlying()) return false;
        if (player.isShiftKeyDown()) return false;
        if (player.onClimbable()) return false;

        Vec3 vel = player.getDeltaMovement();
        double hSpeedSq = vel.x * vel.x + vel.z * vel.z;
        double min = GhostrunnerConfig.WALL_MIN_ENTRY_H_SPEED;
        return hSpeedSq >= min * min;
    }

    /**
     * 速度在"朝墙"方向上的归一化分量（-1 ~ 1）。
     * <p>水平速度接近 0 时返回 0。
     */
    public static double getTowardWallComponent(Vec3 velocity, Direction wall) {
        double vx = velocity.x, vz = velocity.z;
        double hSpeedSq = vx * vx + vz * vz;
        if (hSpeedSq < GhostrunnerConfig.VEC_EPSILON_SQ) return 0;

        double dot = vx * wall.getStepX() + vz * wall.getStepZ();
        return dot / Math.sqrt(hSpeedSq);
    }

    /** 玩家速度是否朝指定墙面。 */
    public static boolean isMovingTowardWall(Player player, Direction wall) {
        return getTowardWallComponent(player.getDeltaMovement(), wall)
                >= GhostrunnerConfig.WALL_MIN_ENTRY_TOWARD;
    }

    /**
     * 冲刺方向是否"斜撞"墙面。用于冲刺窗口的触发判定。
     * <p>正撞（too direct）和擦过（too shallow）都不算。
     */
    public static boolean isApproachingWallAtAngle(Vec3 dashDirection, Direction wall) {
        double toward = getTowardWallComponent(dashDirection, wall);
        return toward >= GhostrunnerConfig.WALL_DASH_WINDOW_TOWARD_MIN
                && toward <= GhostrunnerConfig.WALL_DASH_WINDOW_TOWARD_MAX;
    }

    // ================================================================
    //                          方向锁定
    // ================================================================

    /**
     * 计算跑墙的锁定方向（水平单位向量）。
     * <p>算法：把视线投影到墙面平面上。视线和墙几乎平行时退化为速度投影。
     *
     * @return null 表示无法确定方向（视线和速度都在墙的法线方向上）
     */
    public static Vec3 computeLockedDirection(Player player, Direction wall) {
        double nx = wall.getStepX();
        double nz = wall.getStepZ();

        // 1) 视线投影
        Vec3 look = player.getViewVector(1.0F);
        double dotL = look.x * nx + look.z * nz;
        double px = look.x - dotL * nx;
        double pz = look.z - dotL * nz;
        double lenSq = px * px + pz * pz;

        // 2) 视线退化时用速度投影
        if (lenSq < GhostrunnerConfig.AIM_EPSILON_SQ) {
            Vec3 vel = player.getDeltaMovement();
            double dotV = vel.x * nx + vel.z * nz;
            px = vel.x - dotV * nx;
            pz = vel.z - dotV * nz;
            lenSq = px * px + pz * pz;
        }

        if (lenSq < GhostrunnerConfig.AIM_EPSILON_SQ) return null;

        double len = Math.sqrt(lenSq);
        return new Vec3(px / len, 0, pz / len);
    }

    /**
     * 从锁定方向算速度。纯水平，无重力补偿。
     */
    public static Vec3 velocityFromLockedDirection(Vec3 lockedDirection) {
        return new Vec3(
                lockedDirection.x * GhostrunnerConfig.WALL_RUN_SPEED,
                0.0,
                lockedDirection.z * GhostrunnerConfig.WALL_RUN_SPEED);
    }

    // ================================================================
    //                          跳出
    // ================================================================

    /**
     * 从墙上跳出。
     * <p>水平方向沿墙面反方向给冲量，竖直给向上初速。
     * <p>如果跳出方向有 2~3 格高的可攀爬目标，额外加一点上抬帮助翻越。
     */
    public static void jumpOffWall(Player player, Direction wall) {
        Vec3 vel = player.getDeltaMovement();
        double outX = -wall.getStepX() * GhostrunnerConfig.WALL_JUMP_OUT_H;
        double outZ = -wall.getStepZ() * GhostrunnerConfig.WALL_JUMP_OUT_H;

        // 跳出方向是否有可攀爬目标
        int dx = -wall.getStepX();
        int dz = -wall.getStepZ();
        BlockPos climbTarget = ClimbHandler.findClimbTargetInDirection(player, dx, dz);

        double extraUp = 0.0;
        if (climbTarget != null) {
            int heightDiff = climbTarget.getY() - player.blockPosition().getY();
            if (heightDiff >= 2) {
                extraUp = 0.10 * (heightDiff - 1);
            }
        }

        MotionSync.setAndSync(player,
                vel.x + outX,
                GhostrunnerConfig.WALL_JUMP_OUT_V + extraUp,
                vel.z + outZ);
        player.fallDistance = 0;
    }
}