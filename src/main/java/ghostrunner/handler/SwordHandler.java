package ghostrunner.handler;

import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class SwordHandler {

    private SwordHandler() {}

    // ============ 参数 ============
    public static final float BASE_DAMAGE = 10.0f;

    public static final double SHIELD_KNOCKBACK = 0.55;
    public static final double SHIELD_KNOCKBACK_UP = 0.30;

    // ============ 攻击范围 ============
    public static final double RANGE_FORWARD_MIN = -1.0;
    public static final double RANGE_FORWARD_MAX = 2.5;
    public static final double RANGE_SIDE = 1.5;
    public static final double RANGE_VERT_MIN = -0.6;
    public static final double RANGE_VERT_MAX = 0.6;

    // ================================================================

    public static void performSwing(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isIn(ItemTags.SWORDS)) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d eyePos = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0F).normalize();

        // 视线相对坐标系
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(worldUp);
        if (right.lengthSquared() < 1e-4) {
            right = new Vec3d(-1, 0, 0);
        }
        right = right.normalize();
        Vec3d up = right.crossProduct(look).normalize();

        Box searchBox = player.getBoundingBox().expand(4.0, 3.0, 4.0);
        List<Entity> candidates = world.getOtherEntities(player, searchBox);

        boolean anyHit = false;

        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDead()) continue;
            if (!GhostrunnerState.isGhostrunner(player)) continue;

            Vec3d closest = closestPointOnBox(living.getBoundingBox(), eyePos);
            Vec3d toEntity = closest.subtract(eyePos);

            double fwd  = toEntity.dotProduct(look);
            double side = toEntity.dotProduct(right);
            double vert = toEntity.dotProduct(up);

            if (fwd  < RANGE_FORWARD_MIN || fwd  > RANGE_FORWARD_MAX) continue;
            if (Math.abs(side) > RANGE_SIDE) continue;
            if (vert < RANGE_VERT_MIN || vert > RANGE_VERT_MAX) continue;

            // ★ 格挡判定（含 GR 格挡 + 原版盾牌）
            if (!canBypassShield(player, living)
                    && tryBlockedByTarget(player, living, look)) {
                continue;   // 已被挡下，跳过伤害
            }

            DamageSource source = player.getDamageSources().playerAttack(player);
            if (living.damage(source, BASE_DAMAGE)) {
                anyHit = true;
            }
        }

        if (anyHit) {
            world.playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
    }

    // ================================================================
    //                          工具方法
    // ================================================================

    /** 点在 AABB 上的最近点。 */
    private static Vec3d closestPointOnBox(Box box, Vec3d point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3d(x, y, z);
    }

    // ================================================================
    //                          格挡判定
    // ================================================================

    /**
     * 判断目标是否挡下攻击。同时处理原版盾牌和 GR 格挡。
     * <p>GR 格挡命中时**消耗目标耐力**并弹开攻击者。
     */
    private static boolean tryBlockedByTarget(ServerPlayerEntity attacker,
                                              LivingEntity target,
                                              Vec3d look) {
        // ============ GR 格挡 ============
        if (target instanceof PlayerEntity targetPlayer
                && GhostrunnerState.isGhostrunner(targetPlayer)) {

            GhostrunnerStamina stamina = (GhostrunnerStamina) targetPlayer;
            if (stamina.ghostrunner$isBlocking() && isFacingAttacker(target, attacker)) {
                // ★ 传 source（近战攻击源，不是投射物）
                DamageSource source = attacker.getDamageSources().playerAttack(attacker);
                if (BlockHandler.tryBlock(targetPlayer, source)) {
                    applyShieldKnockback(attacker, look);
                    return true;
                }
                return false;
            }
        }

        // ============ 原版盾牌 ============
        if (target.isBlocking() && isFacingAttacker(target, attacker)) {
            applyShieldKnockback(attacker, look);
            return true;
        }

        return false;
    }

    /** 目标是否面向攻击者。 */
    private static boolean isFacingAttacker(LivingEntity target, ServerPlayerEntity attacker) {
        Vec3d toAttacker = attacker.getPos().subtract(target.getPos()).normalize();
        Vec3d targetLook = target.getRotationVec(1.0F);
        return targetLook.x * toAttacker.x + targetLook.z * toAttacker.z > 0.0;
    }

    /**
     * 拓展接口：返回 true 时无视盾牌/格挡直接造成伤害。
     * <p>当前永远返回 false。未来"锋利刀刃"升级可覆盖此方法。
     */
    private static boolean canBypassShield(ServerPlayerEntity player, LivingEntity target) {
        return false;
    }

    // ================================================================
    //                          弹开
    // ================================================================

    private static void applyShieldKnockback(ServerPlayerEntity player, Vec3d look) {
        Vec3d horiz = new Vec3d(look.x, 0, look.z);
        if (horiz.lengthSquared() < 1e-4) {
            horiz = new Vec3d(0, 0, 1);
        }
        horiz = horiz.normalize();

        player.setVelocity(
                -horiz.x * SHIELD_KNOCKBACK,
                SHIELD_KNOCKBACK_UP,
                -horiz.z * SHIELD_KNOCKBACK);
        player.velocityModified = true;
        player.fallDistance = 0;

        player.getWorld().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
}