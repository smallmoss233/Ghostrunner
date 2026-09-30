package ghostrunner.handler;

import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.GhostrunnerState;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
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

    public static void performSwing(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ItemTags.SWORDS)) return;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).normalize();

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < 1e-4) {
            right = new Vec3(-1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();

        AABB searchBox = player.getBoundingBox().inflate(4.0, 3.0, 4.0);
        List<Entity> candidates = level.getEntities(player, searchBox);

        boolean anyHit = false;

        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living.isDeadOrDying()) continue;
            if (!GhostrunnerState.isGhostrunner(player)) continue;

            Vec3 closest = closestPointOnBox(living.getBoundingBox(), eyePos);
            Vec3 toEntity = closest.subtract(eyePos);

            double fwd  = toEntity.dot(look);
            double side = toEntity.dot(right);
            double vert = toEntity.dot(up);

            if (fwd  < RANGE_FORWARD_MIN || fwd  > RANGE_FORWARD_MAX) continue;
            if (Math.abs(side) > RANGE_SIDE) continue;
            if (vert < RANGE_VERT_MIN || vert > RANGE_VERT_MAX) continue;

            if (!canBypassShield(player, living)
                    && tryBlockedByTarget(player, living, look)) {
                continue;
            }

            DamageSource source = player.damageSources().playerAttack(player);
            // ★ 修正 1: 使用 hurtServer(ServerLevel, DamageSource, float)
            if (living.hurtServer(level, source, BASE_DAMAGE)) {
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
    //                          工具方法
    // ================================================================

    private static Vec3 closestPointOnBox(AABB box, Vec3 point) {
        double x = Math.max(box.minX, Math.min(point.x, box.maxX));
        double y = Math.max(box.minY, Math.min(point.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(point.z, box.maxZ));
        return new Vec3(x, y, z);
    }

    // ================================================================
    //                          格挡判定
    // ================================================================

    private static boolean tryBlockedByTarget(ServerPlayer attacker,
                                              LivingEntity target,
                                              Vec3 look) {
        if (target instanceof Player targetPlayer
                && GhostrunnerState.isGhostrunner(targetPlayer)) {

            GhostrunnerStamina stamina = (GhostrunnerStamina) targetPlayer;
            if (stamina.ghostrunner$isBlocking() && isFacingAttacker(target, attacker)) {
                DamageSource source = attacker.damageSources().playerAttack(attacker);
                if (BlockHandler.tryBlock(targetPlayer, source)) {
                    applyShieldKnockback(attacker, look);
                    return true;
                }
                return false;
            }
        }

        if (target.isBlocking() && isFacingAttacker(target, attacker)) {
            applyShieldKnockback(attacker, look);
            return true;
        }

        return false;
    }

    private static boolean isFacingAttacker(LivingEntity target, ServerPlayer attacker) {
        Vec3 toAttacker = attacker.position().subtract(target.position()).normalize();
        Vec3 targetLook = target.getViewVector(1.0F);
        return targetLook.x * toAttacker.x + targetLook.z * toAttacker.z > 0.0;
    }

    private static boolean canBypassShield(ServerPlayer player, LivingEntity target) {
        return false;
    }

    // ================================================================
    //                          弹开
    // ================================================================

    private static void applyShieldKnockback(ServerPlayer player, Vec3 look) {
        Vec3 horiz = new Vec3(look.x, 0, look.z);
        if (horiz.lengthSqr() < 1e-4) {
            horiz = new Vec3(0, 0, 1);
        }
        horiz = horiz.normalize();

        player.setDeltaMovement(
                -horiz.x * SHIELD_KNOCKBACK,
                SHIELD_KNOCKBACK_UP,
                -horiz.z * SHIELD_KNOCKBACK);

        // ★ 修正 2: Fabric 环境下广播原版运动数据包
        ServerLevel level = (ServerLevel) player.level();
        ClientboundSetEntityMotionPacket motionPacket = new ClientboundSetEntityMotionPacket(player);

        for (ServerPlayer trackingPlayer : PlayerLookup.tracking(player)) {
            if (trackingPlayer != player) {
                trackingPlayer.connection.send(motionPacket);
            }
        }

        player.fallDistance = 0;

        level.playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }
}