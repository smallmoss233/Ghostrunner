package ghostrunner.handler;

import ghostrunner.api.GRTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WallRunHandler {

    private WallRunHandler() {}

    // ================================================================
    //                          手感参数
    // ================================================================

    public static final double WALL_RUN_SPEED = 0.30;
    public static final double WALL_PROBE = 0.15;
    public static final double GRAVITY_COMPENSATION = 0.08;
    public static final double JUMP_OUT_H = 0.35;
    public static final double JUMP_OUT_V = 0.40;
    public static final int REENTRY_COOLDOWN = 15;
    public static final int MIN_WALL_RUN_TICKS = 4;

    public static final double MIN_ENTRY_H_SPEED = 0.04;
    public static final double MIN_ENTRY_TOWARD_WALL = 0.3;
    public static final int MIN_AIRBORNE_TICKS = 2;

    public static final double DASH_WINDOW_TOWARD_MIN = 0.15;
    public static final double DASH_WINDOW_TOWARD_MAX = 0.75;

    // ================================================================
    //                          墙面探测
    // ================================================================

    /** 返回玩家紧贴的墙面方向；没有返回 null。 */
    public static Direction findWall(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox().deflate(0.001);

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            AABB probe = box.move(
                    dir.getStepX() * WALL_PROBE, 0, dir.getStepZ() * WALL_PROBE);
            BlockPos min = BlockPos.containing(probe.minX, probe.minY, probe.minZ);
            BlockPos max = BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ);

            for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                BlockState state = level.getBlockState(p);
                if (state.is(GRTags.WALL_RUN_BLACKLIST)) continue;
                if (state.getCollisionShape(level, p).isEmpty()) continue;
                return dir;
            }
        }
        return null;
    }

    // ================================================================
    //                          条件判定
    // ================================================================

    public static boolean canEnter(Player player) {
        if (player.onGround()) return false;
        if (hasGroundBelow(player)) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.isFallFlying()) return false;
        if (player.isShiftKeyDown()) return false;
        if (player.onClimbable()) return false;

        Vec3 vel = player.getDeltaMovement();
        double hSpeedSq = vel.x * vel.x + vel.z * vel.z;
        return hSpeedSq >= MIN_ENTRY_H_SPEED * MIN_ENTRY_H_SPEED;
    }

    /** 玩家脚下是否踩着实心方块。 */
    public static boolean hasGroundBelow(Player player) {
        Level level = player.level();
        AABB box = player.getBoundingBox();
        AABB probe = new AABB(
                box.minX + 0.001, box.minY - 0.06, box.minZ + 0.001,
                box.maxX - 0.001, box.minY,        box.maxZ - 0.001);

        BlockPos min = BlockPos.containing(probe.minX, probe.minY, probe.minZ);
        BlockPos max = BlockPos.containing(probe.maxX, probe.maxY, probe.maxZ);

        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            // ★ 修正：26.3 中 isSolidRender 为无参版本
            if (level.getBlockState(p).isSolidRender()) return true;
        }
        return false;
    }

    /** 计算给定速度在"朝墙"方向上的归一化分量（-1 ~ 1）。 */
    public static double getTowardWallComponent(Vec3 velocity, Direction wall) {
        double hSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (hSpeed < 1e-6) return 0;
        double nx = wall.getStepX();
        double nz = wall.getStepZ();
        return (velocity.x * nx + velocity.z * nz) / hSpeed;
    }

    public static boolean isMovingTowardWall(Player player, Direction wall) {
        return isMovingTowardWall(player, wall, MIN_ENTRY_TOWARD_WALL);
    }

    public static boolean isMovingTowardWall(Player player, Direction wall, double threshold) {
        return getTowardWallComponent(player.getDeltaMovement(), wall) >= threshold;
    }

    public static boolean isApproachingWallAtAngle(Vec3 dashDirection, Direction wall) {
        double toward = getTowardWallComponent(dashDirection, wall);
        return toward >= DASH_WINDOW_TOWARD_MIN && toward <= DASH_WINDOW_TOWARD_MAX;
    }

    // ================================================================
    //                          方向锁定
    // ================================================================

    public static Vec3 computeLockedDirection(Player player, Direction wall) {
        double nx = wall.getStepX();
        double nz = wall.getStepZ();

        // 1) 视线投影
        Vec3 look = player.getViewVector(1.0F);
        double dotL = look.x * nx + look.z * nz;
        double px = look.x - dotL * nx;
        double pz = look.z - dotL * nz;
        double lenSq = px * px + pz * pz;

        // 2) 视线退化时，用速度投影
        if (lenSq < 0.0001) {
            Vec3 vel = player.getDeltaMovement();
            double dotV = vel.x * nx + vel.z * nz;
            px = vel.x - dotV * nx;
            pz = vel.z - dotV * nz;
            lenSq = px * px + pz * pz;
        }

        if (lenSq < 0.0001) return null;

        double len = Math.sqrt(lenSq);
        return new Vec3(px / len, 0, pz / len);
    }

    public static Vec3 velocityFromLockedDirection(Vec3 lockedDirection) {
        return new Vec3(
                lockedDirection.x * WALL_RUN_SPEED,
                0.0,
                lockedDirection.z * WALL_RUN_SPEED);
    }

    // ================================================================
    //                          跳出
    // ================================================================

    public static void jumpOffWall(Player player, Direction wall) {
        Vec3 vel = player.getDeltaMovement();
        double outX = -wall.getStepX() * JUMP_OUT_H;
        double outZ = -wall.getStepZ() * JUMP_OUT_H;

        int dx = -wall.getStepX();
        int dz = -wall.getStepZ();
        var climbTarget = ClimbHandler.findClimbTargetInDirection(player, dx, dz);

        double extraUp = 0.0;
        if (climbTarget != null) {
            int heightDiff = climbTarget.getY() - player.blockPosition().getY();
            if (heightDiff >= 2) {
                extraUp = 0.10 * (heightDiff - 1);
            }
        }

        player.setDeltaMovement(vel.x + outX, JUMP_OUT_V + extraUp, vel.z + outZ);

        // ★ 修正：26.3 无 hurtMarked，直接发包同步速度
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }

        player.fallDistance = 0;
    }
}