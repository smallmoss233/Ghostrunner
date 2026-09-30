package ghostrunner.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

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

    public static boolean isOnCooldown(Player player) {
        Integer t = cooldowns.get(player.getUUID());
        return t != null && t > 0;
    }

    // ================================================================
    //                          目标检测
    // ================================================================

    public static BlockPos findClimbTarget(Player player) {
        Direction facing = player.getDirection();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        int[][] dirs = {
                {facing.getStepX(), facing.getStepZ()},
                {facing.getStepX() + left.getStepX(), facing.getStepZ() + left.getStepZ()},
                {facing.getStepX() + right.getStepX(), facing.getStepZ() + right.getStepZ()},
        };

        for (int[] d : dirs) {
            BlockPos target = findClimbTargetInDirection(player, d[0], d[1]);
            if (target != null) return target;
        }
        return null;
    }

    public static BlockPos findClimbTargetInDirection(Player player, int dx, int dz) {
        if (dx == 0 && dz == 0) return null;

        Level level = player.level();
        var box = player.getBoundingBox();
        int feetY = (int) Math.floor(box.minY);

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        int x = (int) Math.floor(centerX) + dx;
        int z = (int) Math.floor(centerZ) + dz;

        int wallTop = Integer.MIN_VALUE;
        for (int y = feetY; y <= feetY + MAX_CLIMB_HEIGHT; y++) {
            BlockPos p = new BlockPos(x, y, z);
            var state = level.getBlockState(p);
            boolean hasCollision = !state.getCollisionShape(level, p).isEmpty();

            if (hasCollision) {
                wallTop = y;
            } else if (wallTop != Integer.MIN_VALUE) {
                break;
            }
        }

        if (wallTop == Integer.MIN_VALUE) return null;

        int targetY = wallTop + 1;
        int heightDiff = targetY - player.blockPosition().getY();
        if (heightDiff < 1 || heightDiff > MAX_CLIMB_HEIGHT) return null;

        BlockPos target = new BlockPos(x, targetY, z);

        if (!level.getBlockState(target).getCollisionShape(level, target).isEmpty()) return null;

        BlockPos head = target.above();
        var headShape = level.getBlockState(head).getCollisionShape(level, head);
        if (!headShape.isEmpty()) {
            double maxY = headShape.max(Direction.Axis.Y);
            if (maxY >= 1.5) return null;
        }

        return target;
    }

    // ================================================================
    //                          执行
    // ================================================================

    public static void tryClimb(ServerPlayer player) {
        if (isOnCooldown(player)) return;

        BlockPos target = findClimbTarget(player);
        if (target == null) return;

        int heightDiff = target.getY() - player.blockPosition().getY();
        applyClimbVelocity(player, heightDiff, player.getYRot());

        cooldowns.put(player.getUUID(), CLIMB_COOLDOWN);
    }

    public static void applyClimbVelocity(Player player, int heightDiff, float yRot) {
        double vy = switch (heightDiff) {
            case 1 -> VY_1;
            case 2 -> VY_2;
            case 3 -> VY_3;
            default -> 0;
        };
        if (vy == 0) return;

        Vec3 forward = Vec3.directionFromRotation(0, yRot).normalize();
        MotionSync.setAndSync(player, forward.x * FORWARD_V, vy, forward.z * FORWARD_V);
        player.fallDistance = 0;
    }
}