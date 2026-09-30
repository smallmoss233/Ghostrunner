package ghostrunner.handler;

import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 弹反。
 * <p>面对飞行中的弹射物按攻击键 → 把它弹回视线方向。
 * <p><b>设计目标</b>：
 * <ul>
 *   <li>只弹"迎面而来"的弹射物，不弹已经飞远的、插在地上的</li>
 *   <li>弹反速度基于原速放大，不是固定值——快箭反弹更快</li>
 *   <li>用 {@code lerpMotion} 而非 {@code setDeltaMovement}，取消 inGround 状态</li>
 *   <li>显式广播速度给追踪玩家，客户端立即看到转向</li>
 * </ul>
 */
public final class ParryHandler {

    private ParryHandler() {}

    private static final CooldownTracker COOLDOWNS = new CooldownTracker();

    // ================================================================
    //                          冷却
    // ================================================================

    public static void tickCooldowns() {
        COOLDOWNS.tickAll();
    }

    public static boolean isOnCooldown(ServerPlayer player) {
        return COOLDOWNS.isOnCooldown(player.getUUID());
    }

    // ================================================================
    //                          入口
    // ================================================================

    /**
     * 尝试弹反。成功则返回 true（外部应跳过普通挥砍）。
     */
    public static boolean tryParry(ServerPlayer player) {
        if (COOLDOWNS.isOnCooldown(player.getUUID())) return false;

        AbstractArrow target = findParryTarget(player);
        if (target == null) return false;

        reflect(player, target);
        playSuccessFeedback(player);

        COOLDOWNS.set(player.getUUID(), GhostrunnerConfig.PARRY_COOLDOWN);
        return true;
    }

    // ================================================================
    //                          目标搜索
    // ================================================================

    /**
     * 在玩家前方查找可弹反的弹射物。多个目标时取最近的。
     */
    private static AbstractArrow findParryTarget(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        double range = GhostrunnerConfig.PARRY_RANGE;
        AABB searchBox = new AABB(
                eyePos.x - range, eyePos.y - range, eyePos.z - range,
                eyePos.x + range, eyePos.y + range, eyePos.z + range);

        List<AbstractArrow> candidates = level.getEntitiesOfClass(
                AbstractArrow.class, searchBox);

        AbstractArrow best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (AbstractArrow arrow : candidates) {
            if (arrow.isRemoved()) continue;
            if (!isValidTarget(player, eyePos, look, arrow)) continue;

            double distSq = arrow.position().distanceToSqr(eyePos);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = arrow;
            }
        }
        return best;
    }

    /**
     * 四层过滤：距离 → 前方锥 → 飞行状态 → 迎面方向。
     */
    private static boolean isValidTarget(ServerPlayer player, Vec3 eyePos, Vec3 look,
                                         AbstractArrow arrow) {
        // 1. 距离
        Vec3 toArrow = arrow.position().subtract(eyePos);
        double distSq = toArrow.lengthSqr();
        double rangeSq = GhostrunnerConfig.PARRY_RANGE * GhostrunnerConfig.PARRY_RANGE;
        if (distSq > rangeSq) return false;

        // 距离极近时跳过后面的方向判定（避免除零）
        if (distSq < GhostrunnerConfig.VEC_EPSILON_SQ) return true;

        // 2. 前方锥
        Vec3 toArrowDir = toArrow.normalize();
        if (toArrowDir.dot(look) < GhostrunnerConfig.PARRY_CONE_DOT) return false;

        // 3. 飞行状态（插在地上的箭 velocity 接近 0）
        Vec3 arrowVel = arrow.getDeltaMovement();
        double arrowSpeedSq = arrowVel.lengthSqr();
        double minSpeedSq = GhostrunnerConfig.PARRY_MIN_TARGET_SPEED
                * GhostrunnerConfig.PARRY_MIN_TARGET_SPEED;
        if (arrowSpeedSq < minSpeedSq) return false;

        // 4. 迎面：弹射物速度方向朝向玩家
        Vec3 arrowVelDir = arrowVel.normalize();
        Vec3 arrowToPlayer = player.position().subtract(arrow.position());
        if (arrowToPlayer.lengthSqr() < GhostrunnerConfig.VEC_EPSILON_SQ) return true;
        arrowToPlayer = arrowToPlayer.normalize();

        return arrowVelDir.dot(arrowToPlayer) >= GhostrunnerConfig.PARRY_INCOMING_DOT;
    }

    // ================================================================
    //                          反弹
    // ================================================================

    /**
     * 把弹射物弹向玩家视线方向。
     * <p>速度 = max(原速 × 倍率, 最低速度)，让快箭反弹更快。
     */
    private static void reflect(ServerPlayer player, AbstractArrow arrow) {
        Vec3 look = player.getViewVector(1.0F).normalize();

        double originalSpeed = arrow.getDeltaMovement().length();
        double reflectedSpeed = Math.max(
                originalSpeed * GhostrunnerConfig.PARRY_REFLECT_MULTIPLIER,
                GhostrunnerConfig.PARRY_MIN_REFLECT_SPEED);

        Vec3 reflected = look.scale(reflectedSpeed);

        // lerpMotion 会清 inGround，让插地的箭也能重新飞起来
        arrow.lerpMotion(reflected);
        arrow.fallDistance = 0;

        // 广播给所有追踪者，客户端立即看到转向
        MotionSync.syncMotionToTracking(arrow);
    }

    // ================================================================
    //                          反馈
    // ================================================================

    private static void playSuccessFeedback(ServerPlayer player) {
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 1.0f, 1.5f);

        ServerPlayNetworking.send(player,
                new GhostrunnerNetworking.NoticePayload(
                        GhostrunnerNetworking.NoticePayload.Notice.PARRY_SUCCESS));
    }
}