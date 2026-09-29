package ghostrunner.handler;

import ghostrunner.api.GhostrunnerState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
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

    // ============ 攻击范围（相对"眼睛 + 视线"坐标系） ============
    /** 前方判定：-1 允许身后 1 格，2.5 正前方 */
    public static final double RANGE_FORWARD_MIN = -1.0;
    public static final double RANGE_FORWARD_MAX = 2.5;

    /** 左右判定：±1.5 */
    public static final double RANGE_SIDE = 1.5;

    /**
     * 上下判定：相对视线收窄。
     * <p>平视时打不到脚下的小僵尸；低头后才进范围。
     * <p>滑铲时身位降低 → 相对视线自动命中低处目标。
     */
    public static final double RANGE_VERT_MIN = -0.6;
    public static final double RANGE_VERT_MAX = 0.6;

    // ================================================================

    public static void performSwing(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isIn(ItemTags.SWORDS)) return;

        ServerWorld world = (ServerWorld) player.getWorld();

        // ★ 以"眼睛"为原点
        Vec3d eyePos = player.getEyePos();

        // ★ 3D 视线（含俯仰）
        Vec3d look = player.getRotationVec(1.0F).normalize();

        // 构造视线相对坐标系：right = look × worldUp，up = right × look
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right = look.crossProduct(worldUp);
        if (right.lengthSquared() < 1e-4) {
            // 视线几乎垂直，退化处理
            right = new Vec3d(-1, 0, 0);
        }
        right = right.normalize();

        Vec3d up = right.crossProduct(look).normalize();

        // 搜索范围（以玩家为中心，覆盖攻击距离 + 余量）
        Box searchBox = player.getBoundingBox().expand(4.0, 3.0, 4.0);
        List<Entity> candidates = world.getOtherEntities(player, searchBox);

        boolean anyHit = false;

        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDead()) continue;
            if (!GhostrunnerState.isGhostrunner(player)) continue;

            // ★ AABB 最近点判定
            Vec3d closest = closestPointOnBox(living.getBoundingBox(), eyePos);
            Vec3d toEntity = closest.subtract(eyePos);

            double fwd  = toEntity.dotProduct(look);
            double side = toEntity.dotProduct(right);
            double vert = toEntity.dotProduct(up);

            if (fwd  < RANGE_FORWARD_MIN || fwd  > RANGE_FORWARD_MAX) continue;
            if (Math.abs(side) > RANGE_SIDE) continue;
            if (vert < RANGE_VERT_MIN || vert > RANGE_VERT_MAX) continue;

            // 盾牌
            if (isShieldBlocking(living, player) && !canBypassShield(player, living)) {
                applyShieldKnockback(player, look);
                continue;
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

    /** 点在 AABB 上的最近点（若点在盒内返回自身）。 */
    private static Vec3d closestPointOnBox(Box box, Vec3d point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3d(x, y, z);
    }

    // ================================================================
    //                          盾牌
    // ================================================================

    private static boolean isShieldBlocking(LivingEntity target, ServerPlayerEntity attacker) {
        if (!target.isBlocking()) return false;

        Vec3d toAttacker = attacker.getPos().subtract(target.getPos()).normalize();
        Vec3d targetLook = target.getRotationVec(1.0F);
        return targetLook.x * toAttacker.x + targetLook.z * toAttacker.z > 0.0;
    }

    /** 拓展接口：未来"锋利刀刃"升级覆盖此方法即可无视盾牌。 */
    private static boolean canBypassShield(ServerPlayerEntity player, LivingEntity target) {
        return false;
    }

    // ================================================================
    //                          弹开
    // ================================================================

    private static void applyShieldKnockback(ServerPlayerEntity player, Vec3d look) {
        // 用视线的水平分量作为"退开方向"
        Vec3d horiz = new Vec3d(look.x, 0, look.z);
        if (horiz.lengthSquared() < 1e-4) {
            // 视线垂直，退化为"回到反方向"
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