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
    /** 基础伤害。一击必杀 Mixin 会把它放大到致死。 */
    public static final float BASE_DAMAGE = 10.0f;

    /** 砍到盾牌时玩家被弹开的力度 */
    public static final double SHIELD_KNOCKBACK = 0.55;
    /** 弹开的向上分量 */
    public static final double SHIELD_KNOCKBACK_UP = 0.30;

    /** 攻击范围（相对玩家视线的 forward 坐标系） */
    public static final double RANGE_FORWARD_MIN = 0.5;
    public static final double RANGE_FORWARD_MAX = 2.5;
    public static final double RANGE_SIDE = 1.5;
    public static final double RANGE_VERTICAL = 1.5;

    // ================================================================

    public static void performSwing(ServerPlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isIn(ItemTags.SWORDS)) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d playerPos = player.getPos();

        // 玩家视线水平前方向 + 右手方向
        float yawRad = (float) Math.toRadians(player.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);

        // 搜索范围（矩形展开一点，逐实体精判）
        Box searchBox = player.getBoundingBox().expand(3.5, 2.0, 3.5);
        List<Entity> candidates = world.getOtherEntities(player, searchBox);

        boolean anyHit = false;

        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDead()) continue;
            if (!GhostrunnerState.isGhostrunner(player)) continue;

            Vec3d toEntity = entity.getPos().subtract(playerPos);
            double fwd = toEntity.x * forward.x + toEntity.z * forward.z;
            double side = toEntity.x * right.x + toEntity.z * right.z;
            double dy = toEntity.y;

            // 范围判定：向前 0.5~2.5，左右 ±1.5，垂直 ±1.5
            if (fwd < RANGE_FORWARD_MIN || fwd > RANGE_FORWARD_MAX) continue;
            if (Math.abs(side) > RANGE_SIDE) continue;
            if (Math.abs(dy) > RANGE_VERTICAL) continue;

            // 盾牌检查
            if (isShieldBlocking(living, player) && !canBypassShield(player, living)) {
                // 挡下 → 玩家被弹开
                applyShieldKnockback(player, forward);
                continue;
            }

            // 造成伤害
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
    //                          盾牌判定
    // ================================================================

    /** 目标是否举起盾牌并面向攻击者。 */
    private static boolean isShieldBlocking(LivingEntity target, ServerPlayerEntity attacker) {
        if (!target.isBlocking()) return false;

        Vec3d toAttacker = attacker.getPos().subtract(target.getPos()).normalize();
        Vec3d targetLook = target.getRotationVec(1.0F);

        // 原版逻辑：面向攻击者才算挡下（点积 > 0）
        return targetLook.x * toAttacker.x + targetLook.z * toAttacker.z > 0.0;
    }

    /**
     * 拓展接口：返回 true 时无视盾牌直接造成伤害。
     * <p>当前永远返回 false。未来的"锋利刀刃"升级会覆盖此方法。
     */
    private static boolean canBypassShield(ServerPlayerEntity player, LivingEntity target) {
        return false;
    }

    // ================================================================
    //                          弹开
    // ================================================================

    private static void applyShieldKnockback(ServerPlayerEntity player, Vec3d forward) {
        player.setVelocity(
                -forward.x * SHIELD_KNOCKBACK,
                SHIELD_KNOCKBACK_UP,
                -forward.z * SHIELD_KNOCKBACK);
        player.velocityModified = true;
        player.fallDistance = 0;

        player.getWorld().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
}