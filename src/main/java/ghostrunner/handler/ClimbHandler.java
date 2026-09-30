package ghostrunner.handler;

import ghostrunner.config.GhostrunnerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 爬墙。
 * <p>面向 1~3 格高的墙按跳跃键 → 给竖直冲量让玩家贴墙登顶。
 * <p>冷却由 {@link CooldownTracker} 管理，防止贴墙狂按无限登高。
 * <p>和跑墙的交互：跑墙中按跳跃键是"从墙上跳出"（不触发爬墙），
 * 跳出时如果跳跃方向有攀爬目标，{@link WallRunHandler#jumpOffWall}
 * 会额外给一点上抬。
 */
public final class ClimbHandler {

    private ClimbHandler() {}

    private static final CooldownTracker COOLDOWNS = new CooldownTracker();

    // ================================================================
    //                          冷却
    // ================================================================

    public static void tickCooldowns() {
        COOLDOWNS.tickAll();
    }

    // ================================================================
    //                          入口
    // ================================================================

    /**
     * 尝试爬墙。由跳跃键触发。
     * <p>内部检查冷却、目标、高度差，全部通过才施加速度。
     */
    public static void tryClimb(ServerPlayer player) {
        if (COOLDOWNS.isOnCooldown(player.getUUID())) return;

        BlockPos target = findClimbTarget(player);
        if (target == null) return;

        int heightDiff = target.getY() - player.blockPosition().getY();
        applyClimbVelocity(player, heightDiff);

        COOLDOWNS.set(player.getUUID(), GhostrunnerConfig.CLIMB_COOLDOWN);
    }

    // ================================================================
    //                          目标检测
    // ================================================================

    /**
     * 查找玩家面前（含左右斜前）的攀爬目标。
     * <p>优先正前，其次左前，最后右前。
     */
    public static BlockPos findClimbTarget(Player player) {
        Direction facing = player.getDirection();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockPos t = findClimbTargetInDirection(player, facing.getStepX(), facing.getStepZ());
        if (t != null) return t;

        t = findClimbTargetInDirection(player,
                facing.getStepX() + left.getStepX(),
                facing.getStepZ() + left.getStepZ());
        if (t != null) return t;

        return findClimbTargetInDirection(player,
                facing.getStepX() + right.getStepX(),
                facing.getStepZ() + right.getStepZ());
    }

    /**
     * 沿 (dx, dz) 方向查找攀爬目标。
     * <p>算法：
     * <ol>
     *   <li>从玩家脚底向上扫描，找连续实心块的最顶端</li>
     *   <li>顶端 +1 为目标 Y；高度差须在 1~{@code CLIMB_MAX_HEIGHT}</li>
     *   <li>目标站位必须无碰撞体</li>
     *   <li>目标站位上方留够头部空间</li>
     * </ol>
     *
     * @return 目标站位的 {@link BlockPos}，或 null
     */
    public static BlockPos findClimbTargetInDirection(Player player, int dx, int dz) {
        if (dx == 0 && dz == 0) return null;

        Level level = player.level();
        var box = player.getBoundingBox();
        int feetY = (int) Math.floor(box.minY);

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        int x = (int) Math.floor(centerX) + dx;
        int z = (int) Math.floor(centerZ) + dz;

        int maxHeight = GhostrunnerConfig.CLIMB_MAX_HEIGHT;

        // 向上扫描，找连续实心块的顶端
        int wallTop = Integer.MIN_VALUE;
        for (int y = feetY; y <= feetY + maxHeight; y++) {
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
        if (heightDiff < 1 || heightDiff > maxHeight) return null;

        BlockPos target = new BlockPos(x, targetY, z);

        // 站位格必须空
        if (!level.getBlockState(target).getCollisionShape(level, target).isEmpty()) return null;

        // 头部空间：上方一格不能"几乎实心"
        BlockPos head = target.above();
        var headShape = level.getBlockState(head).getCollisionShape(level, head);
        if (!headShape.isEmpty()) {
            double maxY = headShape.max(Direction.Axis.Y);
            if (maxY >= 1.5) return null;
        }

        return target;
    }

    // ================================================================
    //                          速度注入
    // ================================================================

    /**
     * 根据高度差给竖直冲量。
     * <p>竖直速度按阶梯值给（1 格 / 2 格 / 3 格），水平方向朝面向给一点前冲，
     * 让玩家贴墙往上滑。
     */
    public static void applyClimbVelocity(Player player, int heightDiff) {
        double vy = switch (heightDiff) {
            case 1 -> GhostrunnerConfig.CLIMB_VY_1;
            case 2 -> GhostrunnerConfig.CLIMB_VY_2;
            case 3 -> GhostrunnerConfig.CLIMB_VY_3;
            default -> 0.0;
        };
        if (vy == 0.0) return;

        float yRot = player.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0, yRot).normalize();

        MotionSync.setAndSync(player,
                forward.x * GhostrunnerConfig.CLIMB_FORWARD_V,
                vy,
                forward.z * GhostrunnerConfig.CLIMB_FORWARD_V);
        player.fallDistance = 0;
    }
}