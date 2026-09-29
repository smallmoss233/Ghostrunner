package ghostrunner.handler;

import ghostrunner.Ghostrunner;
import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.WallRunState;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DashHandler {

    private DashHandler() {}

    // ============ 手感参数 ============
    public static final double DASH_SPEED = 1.20;
    public static final double DASH_UPWARD = 0.08;
    public static final int DASH_COOLDOWN = 20;

    /** 每次冲刺消耗的耐力 */
    public static final float STAMINA_PER_DASH = 25.0f;

    /** 冲刺后开启的贴墙窗口长度（tick） */
    public static final int DASH_WALL_WINDOW_TICKS = 8;

    /** 冲刺后开启的衰减窗口（tick） */
    public static final int DASH_DECAY_TICKS = 20;

    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    // ================================================================

    public static void tickCooldowns() {
        cooldowns.replaceAll((uuid, t) -> Math.max(0, t - 1));
    }

    public static boolean isOnCooldown(PlayerEntity player) {
        Integer t = cooldowns.get(player.getUuid());
        return t != null && t > 0;
    }

    /**
     * 冲刺。方向由客户端传来的 WASD 按键状态决定。
     */
    public static void tryDash(ServerPlayerEntity player,
                               boolean forward, boolean back,
                               boolean left, boolean right) {
        if (isOnCooldown(player)) return;
        if (player.isTouchingWater() || player.isInLava()) return;
        if (player.isFallFlying()) return;
        if (player.isSneaking()) return;

        // 直接强转（PlayerEntity 全部实现了这些接口）
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;
        WallRunState wallState = (WallRunState) player;

        boolean inAir = !player.isOnGround() && !WallRunHandler.hasGroundBelow(player);
        if (inAir && stamina.ghostrunner$isAirDashUsed()) return;
        if (stamina.ghostrunner$getStamina() < STAMINA_PER_DASH) return;

        Vec3d direction = computeDashDirection(player, forward, back, left, right);
        if (direction == null) return;

        // 清除冷却 + 开启贴墙窗口 + 记录冲刺方向
        wallState.ghostrunner$clearWallRunCooldown();
        wallState.ghostrunner$startDashWindow(DASH_WALL_WINDOW_TICKS);
        wallState.ghostrunner$setDashDirection(
                new Vec3d(direction.x, 0, direction.z).normalize());

        // 若在跑墙 → 先跳出
        if (wallState.ghostrunner$isWallRunning()) {
            wallState.ghostrunner$jumpOffWall();
        }

        // 施加冲刺速度
        player.setVelocity(
                direction.x * DASH_SPEED,
                DASH_UPWARD,
                direction.z * DASH_SPEED);
        player.velocityModified = true;
        player.fallDistance = 0;

        // 音效
        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS,
                0.8f, 1.2f);

        // 开启衰减
        wallState.ghostrunner$startDashDecay(DASH_DECAY_TICKS);

        // 消耗耐力 + 空中冲刺
        stamina.ghostrunner$consumeStamina(STAMINA_PER_DASH);
        if (inAir) stamina.ghostrunner$setAirDashUsed(true);

        cooldowns.put(player.getUuid(), DASH_COOLDOWN);

        // 通知客户端播放特效
        ServerPlayNetworking.send(player, Ghostrunner.DASH_SUCCESS_PACKET, PacketByteBufs.empty());
    }

    // ================================================================
    //                          方向计算
    // ================================================================

    /** 根据 WASD 按键 + 玩家朝向，算水平单位方向。 */
    private static Vec3d computeDashDirection(PlayerEntity player,
                                              boolean forward, boolean back,
                                              boolean left, boolean right) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d lookHoriz = new Vec3d(look.x, 0, look.z);
        if (lookHoriz.lengthSquared() < 0.0001) return null;
        lookHoriz = lookHoriz.normalize();

        if (!forward && !back && !left && !right) {
            return lookHoriz;
        }

        Vec3d rightVec = new Vec3d(-lookHoriz.z, 0, lookHoriz.x);

        double dx = 0, dz = 0;
        if (forward) { dx += lookHoriz.x; dz += lookHoriz.z; }
        if (back)    { dx -= lookHoriz.x; dz -= lookHoriz.z; }
        if (right)   { dx += rightVec.x;  dz += rightVec.z;  }
        if (left)    { dx -= rightVec.x;  dz -= rightVec.z;  }

        Vec3d result = new Vec3d(dx, 0, dz);
        if (result.lengthSquared() < 0.0001) return lookHoriz;
        return result.normalize();
    }

    /** 含 Y 分量的瞄准方向（子弹时间释放用）。 */
    public static Vec3d computeAimFromInput(PlayerEntity player,
                                            boolean forward, boolean back,
                                            boolean left, boolean right) {
        Vec3d look = player.getRotationVec(1.0F);
        if (!forward && !back && !left && !right) {
            return look.normalize();
        }

        Vec3d lookHoriz = new Vec3d(look.x, 0, look.z).normalize();
        Vec3d rightVec = new Vec3d(-lookHoriz.z, 0, lookHoriz.x);

        double dx = 0, dz = 0;
        if (forward) { dx += lookHoriz.x; dz += lookHoriz.z; }
        if (back)    { dx -= lookHoriz.x; dz -= lookHoriz.z; }
        if (right)   { dx += rightVec.x;  dz += rightVec.z;  }
        if (left)    { dx -= rightVec.x;  dz -= rightVec.z;  }

        if (dx * dx + dz * dz < 0.0001) return look.normalize();

        double vy = look.y;
        return new Vec3d(dx, vy, dz).normalize();
    }
}