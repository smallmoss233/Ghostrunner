package ghostrunner.handler;

import ghostrunner.api.GRTags;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class WallRunHandler {

    private WallRunHandler() {}

    // ================================================================
    //                          手感参数
    // ================================================================

    /** 跑墙恒定水平速度（方块/tick） */
    public static final double WALL_RUN_SPEED = 0.30;

    /** 探测墙的距离 */
    public static final double WALL_PROBE = 0.15;

    /** 跑墙时抵消重力的量 */
    public static final double GRAVITY_COMPENSATION = 0.08;

    /** 跳出时远离墙的横向推力 */
    public static final double JUMP_OUT_H = 0.35;

    /** 跳出时的向上推力 */
    public static final double JUMP_OUT_V = 0.40;

    /** 退出后多少 tick 内不能再进入 */
    public static final int REENTRY_COOLDOWN = 15;

    /** 进入跑墙后至少经过这么多 tick 才允许跳出 */
    public static final int MIN_WALL_RUN_TICKS = 4;

    // ================================================================
    //                          进入条件
    // ================================================================

    /** 进入跑墙所需的最低水平速度 */
    public static final double MIN_ENTRY_H_SPEED = 0.04;

    /** 常规跑墙：速度朝墙的最小归一化分量 */
    public static final double MIN_ENTRY_TOWARD_WALL = 0.3;

    /** 离地后至少经过这么多 tick 才能进入跑墙 */
    public static final int MIN_AIRBORNE_TICKS = 2;

    // ================================================================
    //                      冲刺窗口判定区间
    // ================================================================

    /** 低于此值 = 平行擦过，不触发 */
    public static final double DASH_WINDOW_TOWARD_MIN = 0.15;

    /** 高于此值 = 正面撞墙，不触发 */
    public static final double DASH_WINDOW_TOWARD_MAX = 0.75;

    // ================================================================
    //                          墙面探测
    // ================================================================

    /** 返回玩家紧贴的墙面方向；没有返回 null。 */
    public static Direction findWall(PlayerEntity player) {
        World world = player.getWorld();
        Box box = player.getBoundingBox().contract(0.001);

        for (Direction dir : Direction.Type.HORIZONTAL) {
            Box probe = box.offset(
                    dir.getOffsetX() * WALL_PROBE, 0, dir.getOffsetZ() * WALL_PROBE);
            BlockPos min = BlockPos.ofFloored(probe.minX, probe.minY, probe.minZ);
            BlockPos max = BlockPos.ofFloored(probe.maxX, probe.maxY, probe.maxZ);

            for (BlockPos p : BlockPos.iterate(min, max)) {
                BlockState state = world.getBlockState(p);
                if (state.isIn(GRTags.WALL_RUN_BLACKLIST)) continue;
                if (state.getCollisionShape(world, p).isEmpty()) continue;
                return dir;
            }
        }
        return null;
    }

    // ================================================================
    //                          条件判定
    // ================================================================

    public static boolean canEnter(PlayerEntity player) {
        if (player.isOnGround()) return false;
        if (hasGroundBelow(player)) return false;
        if (player.isTouchingWater() || player.isInLava()) return false;
        if (player.isFallFlying()) return false;
        if (player.isSneaking()) return false;
        if (player.isClimbing()) return false;

        Vec3d vel = player.getVelocity();
        double hSpeedSq = vel.x * vel.x + vel.z * vel.z;
        return hSpeedSq >= MIN_ENTRY_H_SPEED * MIN_ENTRY_H_SPEED;
    }

    /** 玩家脚下是否踩着实心方块。 */
    public static boolean hasGroundBelow(PlayerEntity player) {
        World world = player.getWorld();
        Box box = player.getBoundingBox();
        Box probe = new Box(
                box.minX + 0.001, box.minY - 0.06, box.minZ + 0.001,
                box.maxX - 0.001, box.minY,        box.maxZ - 0.001);

        BlockPos min = BlockPos.ofFloored(probe.minX, probe.minY, probe.minZ);
        BlockPos max = BlockPos.ofFloored(probe.maxX, probe.maxY, probe.maxZ);

        for (BlockPos p : BlockPos.iterate(min, max)) {
            if (world.getBlockState(p).isSolidBlock(world, p)) return true;
        }
        return false;
    }

    /** 计算给定速度在"朝墙"方向上的归一化分量（-1 ~ 1）。 */
    public static double getTowardWallComponent(Vec3d velocity, Direction wall) {
        double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (hSpeed < 1e-6) return 0;
        double nx = wall.getOffsetX();
        double nz = wall.getOffsetZ();
        return (velocity.x * nx + velocity.z * nz) / hSpeed;
    }

    /** 常规判定：当前速度是否朝墙（默认阈值）。 */
    public static boolean isMovingTowardWall(PlayerEntity player, Direction wall) {
        return isMovingTowardWall(player, wall, MIN_ENTRY_TOWARD_WALL);
    }

    /** 常规判定：当前速度是否朝墙（自定义阈值）。 */
    public static boolean isMovingTowardWall(PlayerEntity player, Direction wall, double threshold) {
        return getTowardWallComponent(player.getVelocity(), wall) >= threshold;
    }

    /**
     * 冲刺窗口判定：用记录的冲刺方向，检查是否在"斜撞"区间内。
     * <p>低于 MIN = 平行擦过；高于 MAX = 正面撞墙。两者都不触发。
     */
    public static boolean isApproachingWallAtAngle(Vec3d dashDirection, Direction wall) {
        double toward = getTowardWallComponent(dashDirection, wall);
        return toward >= DASH_WINDOW_TOWARD_MIN && toward <= DASH_WINDOW_TOWARD_MAX;
    }

    // ================================================================
    //                          方向锁定
    // ================================================================

    public static Vec3d computeLockedDirection(PlayerEntity player, Direction wall) {
        double nx = wall.getOffsetX();
        double nz = wall.getOffsetZ();

        // 1) 视线投影
        Vec3d look = player.getRotationVec(1.0F);
        double dotL = look.x * nx + look.z * nz;
        double px = look.x - dotL * nx;
        double pz = look.z - dotL * nz;
        double lenSq = px * px + pz * pz;

        // 2) 视线退化时，用速度投影
        if (lenSq < 0.0001) {
            Vec3d vel = player.getVelocity();
            double dotV = vel.x * nx + vel.z * nz;
            px = vel.x - dotV * nx;
            pz = vel.z - dotV * nz;
            lenSq = px * px + pz * pz;
        }

        if (lenSq < 0.0001) return null;

        double len = Math.sqrt(lenSq);
        return new Vec3d(px / len, 0, pz / len);
    }

    public static Vec3d velocityFromLockedDirection(Vec3d lockedDirection) {
        return new Vec3d(
                lockedDirection.x * WALL_RUN_SPEED,
                GRAVITY_COMPENSATION,
                lockedDirection.z * WALL_RUN_SPEED);
    }

    // ================================================================
    //                          跳出
    // ================================================================

    public static void jumpOffWall(PlayerEntity player, Direction wall) {
        Vec3d vel = player.getVelocity();
        double outX = -wall.getOffsetX() * JUMP_OUT_H;
        double outZ = -wall.getOffsetZ() * JUMP_OUT_H;

        int dx = -wall.getOffsetX();
        int dz = -wall.getOffsetZ();
        var climbTarget = ClimbHandler.findClimbTargetInDirection(player, dx, dz);

        double extraUp = 0.0;
        if (climbTarget != null) {
            int heightDiff = climbTarget.getY() - player.getBlockPos().getY();
            if (heightDiff >= 2) {
                extraUp = 0.10 * (heightDiff - 1);
            }
        }

        player.setVelocity(vel.x + outX, JUMP_OUT_V + extraUp, vel.z + outZ);
        player.velocityModified = true;
        player.fallDistance = 0;
    }
}