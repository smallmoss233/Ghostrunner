package ghostrunner.handler;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public final class ClimbHandler {

    private ClimbHandler() {}

    public static final int MAX_CLIMB_HEIGHT = 3;

    public static final double VY_1 = 0.42;
    public static final double VY_2 = 0.60;
    public static final double VY_3 = 0.78;

    public static final double FORWARD_V = 0.12;

    // ================================================================
    //                          目标检测
    // ================================================================

    /**
     * 多方向检测：正前 + 左前 45° + 右前 45°，任一方向满足就返回。
     */
    public static BlockPos findClimbTarget(PlayerEntity player) {
        Direction facing = player.getHorizontalFacing();
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        int[][] dirs = {
                {facing.getOffsetX(), facing.getOffsetZ()},
                {facing.getOffsetX() + left.getOffsetX(), facing.getOffsetZ() + left.getOffsetZ()},
                {facing.getOffsetX() + right.getOffsetX(), facing.getOffsetZ() + right.getOffsetZ()},
        };

        for (int[] d : dirs) {
            BlockPos target = findClimbTargetInDirection(player, d[0], d[1]);
            if (target != null) return target;
        }
        return null;
    }

    /**
     * 检查指定水平方向的前方是否有可攀爬的墙。
     * <p>返回墙顶站立位置，无墙或不符合条件返回 null。
     */
    public static BlockPos findClimbTargetInDirection(PlayerEntity player, int dx, int dz) {
        if (dx == 0 && dz == 0) return null;

        World world = player.getWorld();
        Box box = player.getBoundingBox();
        int feetY = (int) Math.floor(box.minY);

        // 用玩家包围盒中心作为水平基准
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        int x = (int) Math.floor(centerX) + dx;
        int z = (int) Math.floor(centerZ) + dz;

        int scanStart = feetY;
        int scanEnd = feetY + MAX_CLIMB_HEIGHT;

        int wallTop = Integer.MIN_VALUE;
        for (int y = scanStart; y <= scanEnd; y++) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(p);
            boolean hasCollision = !state.getCollisionShape(world, p).isEmpty();

            if (hasCollision) {
                wallTop = y;
            } else if (wallTop != Integer.MIN_VALUE) {
                break;
            }
        }

        if (wallTop == Integer.MIN_VALUE) return null;

        int targetY = wallTop + 1;
        int heightDiff = targetY - player.getBlockPos().getY();
        if (heightDiff < 1 || heightDiff > MAX_CLIMB_HEIGHT) return null;

        BlockPos target = new BlockPos(x, targetY, z);

        // 脚部目标位置必须有空间
        if (!world.getBlockState(target).getCollisionShape(world, target).isEmpty()) return null;

        // 头部位置：允许低碰撞方块（半砖、地毯、台阶）
        BlockPos head = target.up();
        VoxelShape headShape = world.getBlockState(head).getCollisionShape(world, head);
        if (!headShape.isEmpty()) {
            double maxY = headShape.getMax(Direction.Axis.Y);
            if (maxY >= 1.5) return null;
        }

        return target;
    }

    // ================================================================
    //                          执行
    // ================================================================

    public static void tryClimb(ServerPlayerEntity player) {
        BlockPos target = findClimbTarget(player);
        if (target == null) return;

        int heightDiff = target.getY() - player.getBlockPos().getY();
        applyClimbVelocity(player, heightDiff, player.getYaw());
    }

    public static void applyClimbVelocity(PlayerEntity player, int heightDiff, float yaw) {
        double vy = switch (heightDiff) {
            case 1 -> VY_1;
            case 2 -> VY_2;
            case 3 -> VY_3;
            default -> 0;
        };
        if (vy == 0) return;

        Vec3d forward = Vec3d.fromPolar(0, yaw).normalize();
        double vx = forward.x * FORWARD_V;
        double vz = forward.z * FORWARD_V;

        player.setVelocity(vx, vy, vz);
        player.velocityModified = true;
        player.fallDistance = 0;
    }
}