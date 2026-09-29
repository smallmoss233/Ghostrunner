package ghostrunner.handler;

import ghostrunner.Ghostrunner;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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

    public static boolean isOnCooldown(ServerPlayerEntity player) {
        Integer t = cooldowns.get(player.getUuid());
        return t != null && t > 0;
    }

    /**
     * 尝试弹反。成功则返回 true（外部应跳过普通挥砍）。
     */
    public static boolean tryParry(ServerPlayerEntity player) {
        if (isOnCooldown(player)) return false;

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();

        // 搜索前方 1.5 格内的弹射物
        Box searchBox = new Box(
                eyePos.x - PARRY_RANGE, eyePos.y - PARRY_RANGE, eyePos.z - PARRY_RANGE,
                eyePos.x + PARRY_RANGE, eyePos.y + PARRY_RANGE, eyePos.z + PARRY_RANGE);

        List<Entity> entities = world.getOtherEntities(player, searchBox);

        for (Entity entity : entities) {
            if (!(entity instanceof PersistentProjectileEntity projectile)) continue;
            if (projectile.isRemoved()) continue;

            Vec3d toProjectile = projectile.getPos().subtract(eyePos);
            double dist = toProjectile.length();
            if (dist > PARRY_RANGE) continue;

            // 必须在前方（视线点积 > 0.3）
            double forward = toProjectile.normalize().dotProduct(look);
            if (forward < 0.3) continue;

            // 弹反成功：弹射物被弹向玩家视线方向
            Vec3d reflectedDir = look.multiply(REFLECT_SPEED);
            projectile.setVelocity(
                    reflectedDir.x,
                    reflectedDir.y + 0.1,
                    reflectedDir.z);
            projectile.velocityModified = true;

            // 音效（吸收经验球的声音，清脆有仪式感）
            world.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 1.0f, 1.5f);

            // 通知客户端播放举剑姿势 + 闪光
            ServerPlayNetworking.send(player, Ghostrunner.PARRY_SUCCESS_PACKET,
                    PacketByteBufs.empty());

            cooldowns.put(player.getUuid(), PARRY_COOLDOWN);
            return true;
        }

        return false;
    }
}