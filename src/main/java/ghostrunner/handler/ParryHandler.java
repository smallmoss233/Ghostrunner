package ghostrunner.handler;

import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow; // ★ 修正：新的包路径
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ParryHandler {

    private ParryHandler() {}

    // ============ 参数 ============

    /** 弹射物进入此距离内可被弹反 */
    public static final double PARRY_RANGE = 1.5;

    /** 弹反成功后，弹射物被弹开的速度倍率 */
    public static final double REFLECT_SPEED = 1.5;

    /** 弹反冷却（tick）。10 = 0.5 秒 */
    public static final int PARRY_COOLDOWN = 10;

    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    // ================================================================

    public static void tickCooldowns() {
        cooldowns.replaceAll((uuid, t) -> Math.max(0, t - 1));
    }

    public static boolean isOnCooldown(ServerPlayer player) {
        Integer t = cooldowns.get(player.getUUID());
        return t != null && t > 0;
    }

    /**
     * 尝试弹反。成功则返回 true（外部应跳过普通挥砍）。
     */
    public static boolean tryParry(ServerPlayer player) {
        if (isOnCooldown(player)) return false;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        // 搜索前方 1.5 格内的弹射物
        AABB searchBox = new AABB(
                eyePos.x - PARRY_RANGE, eyePos.y - PARRY_RANGE, eyePos.z - PARRY_RANGE,
                eyePos.x + PARRY_RANGE, eyePos.y + PARRY_RANGE, eyePos.z + PARRY_RANGE);

        List<Entity> entities = level.getEntities(player, searchBox);

        for (Entity entity : entities) {
            if (!(entity instanceof AbstractArrow projectile)) continue;
            if (projectile.isRemoved()) continue;

            Vec3 toProjectile = projectile.position().subtract(eyePos);
            double dist = toProjectile.length();
            if (dist > PARRY_RANGE) continue;

            // 必须在前方（视线点积 > 0.3）
            double forward = toProjectile.normalize().dot(look);
            if (forward < 0.3) continue;

            // 弹反成功：弹射物被弹向玩家视线方向
            Vec3 reflectedDir = look.scale(REFLECT_SPEED);
            projectile.setDeltaMovement(
                    reflectedDir.x,
                    reflectedDir.y + 0.1,
                    reflectedDir.z);
            projectile.syncVelocity = true; // ★ 修正：26.3 使用 syncVelocity 而非 hurtMarked

            // 音效
            level.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 1.0f, 1.5f);

            // 通知客户端播放举剑姿势 + 闪光
            ServerPlayNetworking.send(player,
                    new GhostrunnerNetworking.ParrySuccessPayload());

            cooldowns.put(player.getUUID(), PARRY_COOLDOWN);
            return true;
        }

        return false;
    }
}