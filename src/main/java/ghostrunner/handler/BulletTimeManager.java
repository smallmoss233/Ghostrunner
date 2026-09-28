package ghostrunner.handler;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class BulletTimeManager {

    private BulletTimeManager() {}

    /** 正在子弹时间的玩家 UUID */
    private static final Set<UUID> activePlayers = new HashSet<>();

    /** 冻结半径（格） */
    public static final double RADIUS = 30.0;
    /** 时间缩放：10 表示附近实体慢 10 倍 */
    public static final int TIME_SCALE = 10;

    public static void enter(ServerPlayerEntity player) {
        activePlayers.add(player.getUuid());
    }

    public static void exit(ServerPlayerEntity player) {
        activePlayers.remove(player.getUuid());
    }

    public static boolean hasAnyActive() {
        return !activePlayers.isEmpty();
    }

    /**
     * 判断该实体本 tick 是否应跳过 tick（用于模拟时间变慢）。
     */
    public static boolean shouldSkip(ServerWorld world, Entity entity) {
        if (activePlayers.isEmpty()) return false;
        if (entity instanceof PlayerEntity) return false;   // 玩家自己不受影响

        boolean inRange = false;
        for (UUID uuid : activePlayers) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(uuid);
            if (p == null) continue;
            if (p.getWorld() != world) continue;
            if (p.squaredDistanceTo(entity) > RADIUS * RADIUS) continue;
            inRange = true;
            break;
        }
        if (!inRange) return false;

        long t = world.getTime();
        int id = entity.getId();
        // 每 TIME_SCALE tick 真正 tick 一次
        return Math.floorMod(t + id, TIME_SCALE) != 0;
    }
}