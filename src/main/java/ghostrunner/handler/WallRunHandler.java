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

    /** 探测墙的距离（从玩家碰撞箱向外扩多少） */
    public static final double WALL_PROBE = 0.15;

    /** 跑墙时抵消重力的量（原版每 tick 重力 = -0.08） */
    public static final double GRAVITY_COMPENSATION = 0.08;

    /** 跳出时远离墙的横向推力 */
    public static final double JUMP_OUT_H = 0.35;

    /** 跳出时的向上推力 */
    public static final double JUMP_OUT_V = 0.40;

    /** 退出后多少 tick 内不能再进入（防止抖墙） */
    public static final int REENTRY_COOLDOWN = 15;

    /** 进入跑墙后至少经过这么多 tick 才允许跳出（防误触） */
    public static final int MIN_WALL_RUN_TICKS = 4;

    // ================================================================
    //                          进入条件参数
    // ================================================================

    /** 进入跑墙所需的最低水平速度（方块/tick） */
    public static final double MIN_ENTRY_H_SPEED = 0.04;

    /** 进入跑墙时速度朝墙的最小归一化分量（0~1） */
    public static final double MIN_ENTRY_TOWARD_WALL = 0.3;

    /** 冲刺窗口内进入跑墙的朝墙分量阈值（更宽松） */
    public static final double DASH_WINDOW_TOWARD_WALL = 0.1;

    /** 离地后至少经过这么多 tick 才能进入跑墙 */
    public static final int MIN_AIRBORNE_TICKS = 2;

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
    //                          进入条件
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
        if (hSpeedSq < MIN_ENTRY_H_SPEED * MIN_ENTRY_H_SPEED) return false;

        return true;
    }

    /** 玩家脚下是否踩着实心方块（向下探测一小段）。 */
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

    /**
     * 检查玩家当前速度是否"朝向墙面"，使用默认阈值 {@link #MIN_ENTRY_TOWARD_WALL}。
     */
    public static boolean isMovingTowardWall(PlayerEntity player, Direction wall) {
        return isMovingTowardWall(player, wall, MIN_ENTRY_TOWARD_WALL);
    }

    /**
     * 检查玩家当前速度是否"朝向墙面"，使用自定义阈值。
     *
     * @param threshold 归一化朝墙分量（0~1），越大要求越严格
     */
    public static boolean isMovingTowardWall(PlayerEntity player, Direction wall, double threshold) {
        Vec3d vel = player.getVelocity();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed < 1e-6) return false;

        double nx = wall.getOffsetX();
        double nz = wall.getOffsetZ();
        double towardWall = (vel.x * nx + vel.z * nz) / hSpeed;
        return towardWall >= threshold;
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