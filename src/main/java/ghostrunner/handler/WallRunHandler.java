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

    /** 离地后至少经过这么多 tick 才能进入跑墙 */
    public static final int MIN_AIRBORNE_TICKS = 2;

    // ================================================================
    //                          墙面探测
    // ================================================================

    /** 返回玩家紧贴的墙面方向；没有返回 null。只认 tag 内的方块。 */
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
                // 黑名单里的方块不能跑
                if (state.isIn(GRTags.WALL_RUN_BLACKLIST)) continue;
                // 有碰撞体的方块才能跑（过滤掉草、花、藤蔓等）
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

        // 必须有足够的水平速度（垂直掉落 hSpeed ≈ 0）
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
     * 检查玩家当前速度是否"朝向墙面"。
     * <p>返回 true 表示水平速度方向与"指向墙"的方向夹角小于 90°-arccos(MIN_ENTRY_TOWARD_WALL)。
     */
    public static boolean isMovingTowardWall(PlayerEntity player, Direction wall) {
        Vec3d vel = player.getVelocity();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        if (hSpeed < 1e-6) return false;

        // 墙相对玩家的方向（如玩家贴西墙 → wall = WEST，法线 = (-1, 0)）
        // 玩家要"朝墙"移动，即速度方向朝 WEST 方向，也就是速度在 (-nx, -nz) 上的投影为正
        // wall.getOffsetX() 是"从玩家指向墙"的方向
        double nx = wall.getOffsetX();
        double nz = wall.getOffsetZ();

        // 归一化后的朝墙分量
        double towardWall = (vel.x * nx + vel.z * nz) / hSpeed;
        return towardWall >= MIN_ENTRY_TOWARD_WALL;
    }

    // ================================================================
    //                          方向锁定
    // ================================================================

    /**
     * 进入跑墙时**只调用一次**，计算并锁定"沿墙前进方向"。
     * <p>优先用玩家视线方向投影到墙面；如果视线恰好垂直墙面（退化），
     * 退而使用玩家当前水平速度投影；两者都退化则返回 null。
     */
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

    /** 用锁定的方向计算每 tick 应施加的速度。 */
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

        // 检测"跳出方向"前方是否有可攀爬的平台
        // 有 → 额外加向上推力，让玩家直接翻上去
        // 无 → 普通跳出
        int dx = -wall.getOffsetX();
        int dz = -wall.getOffsetZ();
        var climbTarget = ClimbHandler.findClimbTargetInDirection(player, dx, dz);

        double extraUp = 0.0;
        if (climbTarget != null) {
            int heightDiff = climbTarget.getY() - player.getBlockPos().getY();
            if (heightDiff >= 2) {
                // 2 格以上才有必要"翻上去"，1 格普通跳跃就够
                extraUp = 0.10 * (heightDiff - 1);
            }
        }

        player.setVelocity(vel.x + outX, JUMP_OUT_V + extraUp, vel.z + outZ);
        player.velocityModified = true;
        player.fallDistance = 0;
    }
}