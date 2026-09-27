package ghostrunner.handler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClimbHandler {

    private ClimbHandler() {}

    public static final int MAX_CLIMB_HEIGHT = 3;

    public static final double VY_1 = 0.42;
    public static final double VY_2 = 0.60;
    public static final double VY_3 = 0.78;
    public static final double FORWARD_V = 0.12;

    /** 爬墙冷却（tick）。防止贴墙狂按空格无限登高。 */
    public static final int CLIMB_COOLDOWN = 12;

    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    // ================================================================
    //                          冷却
    // ================================================================

    public static void tickCooldowns() {
        cooldowns.replaceAll((uuid, t) -> Math.max(0, t - 1));
    }

    public static boolean isOnCooldown(PlayerEntity player) {
        Integer t = cooldowns.get(player.getUuid());
        return t != null && t > 0;
    }

    // ================================================================
    //                          目标检测（不变）
    // ================================================================

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

    public static BlockPos findClimbTargetInDirection(PlayerEntity player, int dx, int dz) {
        if (dx == 0 && dz == 0) return null;

        World world = player.getWorld();
        var box = player.getBoundingBox();
        int feetY = (int) Math.floor(box.minY);

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        int x = (int) Math.floor(centerX) + dx;
        int z = (int) Math.floor(centerZ) + dz;

        int wallTop = Integer.MIN_VALUE;
        for (int y = feetY; y <= feetY + MAX_CLIMB_HEIGHT; y++) {
            BlockPos p = new BlockPos(x, y, z);
            var state = world.getBlockState(p);
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

        if (!world.getBlockState(target).getCollisionShape(world, target).isEmpty()) return null;

        BlockPos head = target.up();
        var headShape = world.getBlockState(head).getCollisionShape(world, head);
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
        if (isOnCooldown(player)) return;

        BlockPos target = findClimbTarget(player);
        if (target == null) return;

        int heightDiff = target.getY() - player.getBlockPos().getY();
        applyClimbVelocity(player, heightDiff, player.getYaw());

        // 设置冷却
        cooldowns.put(player.getUuid(), CLIMB_COOLDOWN);
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
        player.setVelocity(forward.x * FORWARD_V, vy, forward.z * FORWARD_V);
        player.velocityModified = true;
        player.fallDistance = 0;
    }
}