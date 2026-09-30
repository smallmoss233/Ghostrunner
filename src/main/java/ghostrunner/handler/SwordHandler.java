package ghostrunner.handler;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.config.GhostrunnerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 范围挥砍。
 * <p>3D 视线坐标系（前 / 侧 / 上）做 AABB 最近点判定，支持"掠过斩"。
 * <p>判定顺序：
 * <ol>
 *   <li>世界坐标过滤：距离 + 视线坐标范围</li>
 *   <li>格挡：目标举盾面向攻击者时格挡成功 → 攻击者被弹开</li>
 *   <li>伤害：走 {@code hurtServer}，被 {@code LivingEntityDamageMixin} 放大为一击必杀</li>
 * </ol>
 */
public final class SwordHandler {

    private SwordHandler() {}

    // ================================================================
    //                          主入口
    // ================================================================

    public static void performSwing(ServerPlayer player) {
        // 持剑判定
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ItemTags.SWORDS)) return;

        // 幽灵行者判定（防御性，调用方通常已检查）
        if (!GhostrunnerPlayer.isGhostrunner(player)) return;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        // 视线坐标系
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < GhostrunnerConfig.VEC_EPSILON_SQ) {
            // 视线朝正上/正下时 cross 退化，用固定右向量
            right = new Vec3(-1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();

        boolean anyHit = false;

        // 搜索范围候选
        AABB searchBox = player.getBoundingBox().inflate(
                GhostrunnerConfig.SWORD_SEARCH_RADIUS,
                GhostrunnerConfig.SWORD_SEARCH_RADIUS,
                GhostrunnerConfig.SWORD_SEARCH_RADIUS);
        List<Entity> candidates = level.getEntities(player, searchBox);

        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDeadOrDying()) continue;

            // ---- 范围判定 ----
            Vec3 closest = closestPointOnBox(living.getBoundingBox(), eyePos);
            Vec3 toEntity = closest.subtract(eyePos);

            if (!isInSwingRange(toEntity.dot(look),
                    toEntity.dot(right),
                    toEntity.dot(up))) {
                continue;
            }

            // ---- 格挡判定 ----
            if (tryBlockedByTarget(player, living, look)) {
                continue;
            }

            // ---- 造成伤害 ----
            DamageSource source = player.damageSources().playerAttack(player);
            if (living.hurtServer(level, source, GhostrunnerConfig.SWORD_BASE_DAMAGE)) {
                anyHit = true;
            }
        }

        if (anyHit) {
            level.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    // ================================================================
    //                          范围判定
    // ================================================================

    /**
     * 视线坐标系下的范围判定。三轴独立阈值。
     */
    private static boolean isInSwingRange(double fwd, double side, double vert) {
        if (fwd < GhostrunnerConfig.SWORD_RANGE_FORWARD_MIN) return false;
        if (fwd > GhostrunnerConfig.SWORD_RANGE_FORWARD_MAX) return false;
        if (Math.abs(side) > GhostrunnerConfig.SWORD_RANGE_SIDE) return false;
        if (vert < GhostrunnerConfig.SWORD_RANGE_VERT_MIN) return false;
        if (vert > GhostrunnerConfig.SWORD_RANGE_VERT_MAX) return false;
        return true;
    }

    /**
     * AABB 上离 {@code point} 最近的点。
     */
    private static Vec3 closestPointOnBox(AABB box, Vec3 point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3(x, y, z);
    }

    // ================================================================
    //                          格挡判定
    // ================================================================

    /**
     * 目标是否格挡成功。成功则弹开攻击者，返回 true。
     * <p>两类格挡：
     * <ul>
     *   <li>幽灵行者格挡：走 {@link BlockHandler#tryBlock}</li>
     *   <li>原版盾牌格挡：无条件成功</li>
     * </ul>
     * <p>两者都要求目标面向攻击者。
     */
    private static boolean tryBlockedByTarget(ServerPlayer attacker,
                                              LivingEntity target,
                                              Vec3 attackLook) {
        if (!isFacingAttacker(target, attacker)) return false;

        // 幽灵行者格挡
        if (target instanceof Player targetPlayer
                && GhostrunnerPlayer.isGhostrunner(targetPlayer)) {
            DamageSource source = attacker.damageSources().playerAttack(attacker);
            if (BlockHandler.tryBlock(targetPlayer, source)) {
                applyShieldKnockback(attacker, attackLook);
                return true;
            }
            return false;
        }

        // 原版盾牌格挡
        if (target.isBlocking()) {
            applyShieldKnockback(attacker, attackLook);
            return true;
        }

        return false;
    }

    /**
     * 目标是否面朝攻击者（水平面判定）。
     */
    private static boolean isFacingAttacker(LivingEntity target, ServerPlayer attacker) {
        Vec3 toAttacker = attacker.position().subtract(target.position());
        double horizLenSq = toAttacker.x * toAttacker.x + toAttacker.z * toAttacker.z;
        if (horizLenSq < GhostrunnerConfig.VEC_EPSILON_SQ) return true;  // 重叠视为面对

        Vec3 targetLook = target.getViewVector(1.0F);
        // 只看水平分量，避免垂直视角差影响判定
        double dot = targetLook.x * toAttacker.x + targetLook.z * toAttacker.z;
        return dot > 0.0;
    }

    // ================================================================
    //                          弹开
    // ================================================================

    /**
     * 攻击者被盾牌弹开。
     * <p>水平方向沿视线反方向，加一点上抬让弹开更明显。
     */
    private static void applyShieldKnockback(ServerPlayer player, Vec3 look) {
        Vec3 horiz = new Vec3(look.x, 0, look.z);
        if (horiz.lengthSqr() < GhostrunnerConfig.VEC_EPSILON_SQ) {
            horiz = new Vec3(0, 0, 1);
        }
        horiz = horiz.normalize();

        MotionSync.setAndSync(player,
                -horiz.x * GhostrunnerConfig.SHIELD_KNOCKBACK,
                GhostrunnerConfig.SHIELD_KNOCKBACK_UP,
                -horiz.z * GhostrunnerConfig.SHIELD_KNOCKBACK);
        player.fallDistance = 0;

        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}