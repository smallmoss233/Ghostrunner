package ghostrunner.handler;

import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.data.GhostrunnerData;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;

/**
 * 幽灵行者玩家的主 tick 调度。
 * <p>每个 {@code tickXxx} 方法只负责一个子系统，按 tick 执行顺序调用。
 * <p>所有状态都在传入的 {@link GhostrunnerData} 里，本类无状态。
 */
public final class GhostrunnerTickHandler {

    private GhostrunnerTickHandler() {}

    // ================================================================
    //                          主调度
    // ================================================================

    public static void serverTick(Player self, GhostrunnerData data) {
        tickDashDecay(self, data);

        // 非 Ghostrunner 玩家提前退出
        if (!data.ascended) {
            tickNonGhostrunnerExit(self, data);
            return;
        }

        tickEnvironmentLocks(self, data);
        tickBulletTime(self, data);

        // 溶于水/岩浆即死，本 tick 到此为止
        if (tickWaterLavaDeath(self)) return;

        tickFallDistanceReset(self);
        tickTimers(data);
        tickAirborneTracker(self, data);

        tickWallRunStateMachine(self, data);

        // ★ 顺序：先处理格挡状态（可能强制退出），再决定是否恢复耐力
        tickBlocking(self, data);
        tickStaminaRecovery(data);

        tickAirDashReset(self, data);

        syncNetwork(self, data);
    }

    // ================================================================
    //                      1. 冲刺衰减
    // ================================================================

    private static void tickDashDecay(Player self, GhostrunnerData data) {
        if (data.dashDecayTicks <= 0) return;

        if (self.onGround() || data.wallRunning || self.isInWater() || self.isInLava()) {
            data.dashDecayTicks = 0;
            return;
        }

        data.dashDecayTicks--;
        Vec3 vel = self.getDeltaMovement();
        MotionSync.setAndSync(self,
                vel.x * GhostrunnerConfig.DASH_DECAY_AIR,
                vel.y,
                vel.z * GhostrunnerConfig.DASH_DECAY_AIR);
    }

    // ================================================================
    //                      2. 非 Ghostrunner 退出
    // ================================================================

    private static void tickNonGhostrunnerExit(Player self, GhostrunnerData data) {
        if (data.wallRunning) {
            exitWallRun(data);
            syncWallRunState(self, data);
        }
    }

    // ================================================================
    //                      3. 环境锁定
    // ================================================================

    private static void tickEnvironmentLocks(Player self, GhostrunnerData data) {
        FoodData food = self.getFoodData();
        if (food.getFoodLevel() < 20) food.setFoodLevel(20);
        if (food.getSaturationLevel() < 20.0F) food.setSaturation(20.0F);
    }

    // ================================================================
    //                      4. 子弹时间
    // ================================================================

    public static float computeCurrentStamina(GhostrunnerData data, long nowMs) {
        if (!data.inBulletTime) return data.stamina;
        double elapsedSec = (nowMs - data.btStartedMs) / 1000.0;
        float consumed = (float) (elapsedSec * GhostrunnerConfig.BT_STAMINA_PER_SECOND);
        return Math.max(0f, data.btStaminaAtStart - consumed);
    }

    private static void tickBulletTime(Player self, GhostrunnerData data) {
        if (!data.inBulletTime) return;

        float current = computeCurrentStamina(data, System.currentTimeMillis());
        if (current <= 0f) {
            exitBulletTimeAndDash(self, data);
        }
    }

    public static boolean tryEnterBulletTime(Player self, GhostrunnerData data) {
        if (data.inBulletTime) return false;
        if (self.onGround() || WallRunHandler.hasGroundBelow(self)) return false;
        if (self.isInWater() || self.isInLava()) return false;
        if (self.isFallFlying()) return false;
        if (data.stamina < GhostrunnerConfig.BT_MIN_STAMINA) return false;
        if (data.airDashUsed) return false;

        long nowMs = System.currentTimeMillis();
        data.inBulletTime = true;
        data.btStartedMs = nowMs;
        data.btStaminaAtStart = data.stamina;
        data.btAim = self.getViewVector(1.0F).normalize();
        data.airDashUsed = true;

        if (self instanceof ServerPlayer sp) {
            BulletTimeManager.enter(sp);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
            ServerPlayNetworking.send(sp,
                    new GhostrunnerNetworking.BulletTimeStatePayload(true, 0f));
        }
        return true;
    }

    public static void exitBulletTimeAndDash(Player self, GhostrunnerData data) {
        if (!data.inBulletTime) return;

        long nowMs = System.currentTimeMillis();

        // 1. 结算耐力
        data.stamina = computeCurrentStamina(data, nowMs);

        // 2. 状态切换
        data.inBulletTime = false;
        data.btStartedMs = 0L;
        data.btStaminaAtStart = 0f;

        // 3. 摔落免疫宽限
        data.fallGraceUntilMs = nowMs + GhostrunnerConfig.BT_FALL_GRACE_MILLIS;

        // 4. 恢复 tick rate
        if (self instanceof ServerPlayer sp) BulletTimeManager.exit(sp);

        // 5. 释放冲量
        Vec3 dir = data.btAim;
        if (dir.lengthSqr() < GhostrunnerConfig.AIM_EPSILON_SQ) {
            dir = self.getViewVector(1.0F).normalize();
        }

        double vx = dir.x * GhostrunnerConfig.BT_RELEASE_SPEED;
        double vz = dir.z * GhostrunnerConfig.BT_RELEASE_SPEED;
        double vy = dir.y * GhostrunnerConfig.BT_RELEASE_SPEED + 0.05;
        vy = Math.max(-GhostrunnerConfig.BT_RELEASE_DOWN_MAX,
                Math.min(GhostrunnerConfig.BT_RELEASE_UP_MAX, vy));

        MotionSync.setAndSync(self, vx, vy, vz);
        self.fallDistance = 0;

        // 6. 记录水平方向（跑墙窗口用）
        Vec3 horizDir = new Vec3(dir.x, 0, dir.z);
        if (horizDir.lengthSqr() > GhostrunnerConfig.VEC_EPSILON_SQ) {
            data.dashDirection = horizDir.normalize();
        }

        data.dashDecayTicks = GhostrunnerConfig.DASH_DECAY_TICKS;
        data.dashWindowTicks = GhostrunnerConfig.DASH_WALL_WINDOW_TICKS;

        // 7. 通知客户端
        if (self instanceof ServerPlayer sp) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.4f);
            ServerPlayNetworking.send(sp,
                    new GhostrunnerNetworking.BulletTimeStatePayload(false, data.stamina));
            ServerPlayNetworking.send(sp,
                    new GhostrunnerNetworking.NoticePayload(
                            GhostrunnerNetworking.NoticePayload.Notice.DASH_SUCCESS));
        }
    }

    public static void updateBulletTimeAim(GhostrunnerData data, Vec3 direction) {
        if (!data.inBulletTime) return;
        if (direction.lengthSqr() < GhostrunnerConfig.AIM_EPSILON_SQ) return;
        data.btAim = direction.normalize();
    }

    // ================================================================
    //                      5. 水/岩浆致死
    // ================================================================

    private static boolean tickWaterLavaDeath(Player self) {
        if (!self.isInWater() && !self.isInLava()) return false;

        if (self.level() instanceof ServerLevel serverLevel) {
            self.hurtServer(serverLevel, self.damageSources().genericKill(), 1.0F);
        }
        return true;
    }

    // ================================================================
    //                      6. 摔落清零
    // ================================================================

    private static void tickFallDistanceReset(Player self) {
        if (self.fallDistance > 0) self.fallDistance = 0;
    }

    // ================================================================
    //                      7. 计时器
    // ================================================================

    private static void tickTimers(GhostrunnerData data) {
        if (data.wallRunCooldown > 0) data.wallRunCooldown--;
        if (data.dashWindowTicks > 0) data.dashWindowTicks--;
    }

    // ================================================================
    //                      8. 空中计时
    // ================================================================

    private static void tickAirborneTracker(Player self, GhostrunnerData data) {
        boolean grounded = self.onGround() || WallRunHandler.hasGroundBelow(self);
        if (grounded) {
            data.airborneTicks = 0;
        } else {
            data.airborneTicks++;
        }
    }

    // ================================================================
    //                      9. 跑墙状态机
    // ================================================================

    private static void tickWallRunStateMachine(Player self, GhostrunnerData data) {
        if (data.wallRunning) {
            updateWallRun(self, data);
        } else {
            tryEnterWallRun(self, data);
        }
        syncWallRunState(self, data);
    }

    private static void tryEnterWallRun(Player self, GhostrunnerData data) {
        // 冲刺窗口：斜撞触发
        if (data.dashWindowTicks > 0) {
            Direction wall = WallRunHandler.findWall(self);
            if (wall != null && WallRunHandler.isApproachingWallAtAngle(data.dashDirection, wall)) {
                Vec3 locked = WallRunHandler.computeLockedDirection(self, wall);
                if (locked != null) {
                    enterWallRun(data, wall, locked);
                    data.dashWindowTicks = 0;
                    return;
                }
            }
        }

// 常规流程
        if (data.wallRunCooldown > 0) return;
        if (data.airborneTicks < GhostrunnerConfig.WALL_MIN_AIRBORNE_TICKS) return;
        if (!WallRunHandler.canEnter(self)) return;

        Direction wall = WallRunHandler.findWall(self);
        if (wall == null) return;
        if (!WallRunHandler.isMovingTowardWall(self, wall)) return;

        Vec3 locked = WallRunHandler.computeLockedDirection(self, wall);
        if (locked == null) return;

        enterWallRun(data, wall, locked);
    }

    private static void updateWallRun(Player self, GhostrunnerData data) {
        data.wallRunTicks++;

// 环境失效 → 退出
        if (self.isInWater() || self.isInLava()) {
            exitWallRun(data);
            return;
        }

// 墙还在 → 继续；墙没了 → 退出
        if (!WallRunHandler.findWallInDirection(self, data.wallSide)) {
            exitWallRun(data);
            return;
        }

// 不再重新扫描、不再改 wallSide —— 锁定方向已定，墙面方向不变
        Vec3 velocity = WallRunHandler.velocityFromLockedDirection(data.lockedDirection);
        MotionSync.setAndSync(self, velocity.x, 0.0, velocity.z);
        self.fallDistance = 0;
    }

    private static void enterWallRun(GhostrunnerData data, Direction wall, Vec3 lockedDirection) {
        data.wallRunning = true;
        data.wallSide = wall;
        data.lockedDirection = lockedDirection;
        data.wallRunTicks = 0;
    }

    private static void exitWallRun(GhostrunnerData data) {
        data.wallRunning = false;
        data.wallSide = null;
        data.lockedDirection = Vec3.ZERO;
        data.wallRunTicks = 0;
        data.wallRunCooldown = GhostrunnerConfig.WALL_REENTRY_COOLDOWN;
    }

    public static void jumpOffWall(Player self, GhostrunnerData data) {
        if (!data.wallRunning || data.wallSide == null) return;
        if (data.wallRunTicks < GhostrunnerConfig.WALL_MIN_RUN_TICKS) return;

        WallRunHandler.jumpOffWall(self, data.wallSide);
        exitWallRun(data);
    }

    // ================================================================
    //                      10. 格挡状态维护
    // ================================================================

    /**
     * 格挡状态维护。
     * <p>职责：
     * <ul>
     *   <li>未持剑 → 强制退出</li>
     *   <li>耐力耗尽 → 强制退出</li>
     * </ul>
     * <p>完美 / 普通格挡的具体判定由 {@link BlockHandler} 在受到伤害时处理。
     * <p><b>注意</b>：本方法不处理耐力恢复——那是 {@link #tickStaminaRecovery} 的事，
     * 它会先检查 {@code data.blockHeld}。
     */
    private static void tickBlocking(Player self, GhostrunnerData data) {
        if (!data.blockHeld) return;

        // 未持剑 → 强制退出
        if (!self.getMainHandItem().is(ItemTags.SWORDS)) {
            exitBlock(data);
            return;
        }

        // 耐力耗尽 → 强制退出（避免卡死）
        if (data.stamina <= 0f) {
            exitBlock(data);
        }
    }

    /** 强制退出格挡，直接改 data，绕过 PlayerMixin 的硬直检查。 */
    private static void exitBlock(GhostrunnerData data) {
        data.blockHeld = false;
        data.blockReleaseMs = System.currentTimeMillis();
    }

    // ================================================================
    //                      11. 耐力恢复
    // ================================================================

    private static void tickStaminaRecovery(GhostrunnerData data) {
        // 格挡期间不恢复耐力
        if (data.blockHeld) return;
        // 子弹时间期间不恢复（耐力被冻结，按需计算）
        if (data.inBulletTime) return;
        if (data.stamina >= GhostrunnerConfig.STAMINA_MAX) return;

        data.stamina = Math.min(GhostrunnerConfig.STAMINA_MAX,
                data.stamina + GhostrunnerConfig.STAMINA_RECOVERY_PER_TICK);
    }

    // ================================================================
    //                      12. 空中冲刺重置
    // ================================================================

    private static void tickAirDashReset(Player self, GhostrunnerData data) {
        boolean grounded = self.onGround() || WallRunHandler.hasGroundBelow(self);
        if (grounded || data.wallRunning || self.isInWater()) {
            data.airDashUsed = false;
        }
    }

    // ================================================================
    //                      13. 网络同步
    // ================================================================

    private static void syncNetwork(Player self, GhostrunnerData data) {
        syncStamina(self, data);
    }

    private static void syncStamina(Player self, GhostrunnerData data) {
        if (!(self instanceof ServerPlayer sp)) return;

        // 子弹时间期间服务端本就不改 data.stamina，跳过
        if (data.inBulletTime) return;

        if (Math.abs(data.stamina - data.lastSentStamina) < GhostrunnerConfig.STAMINA_SYNC_THRESHOLD) {
            return;
        }
        data.lastSentStamina = data.stamina;
        ServerPlayNetworking.send(sp, new GhostrunnerNetworking.StaminaPayload(data.stamina));
    }

    private static void syncWallRunState(Player self, GhostrunnerData data) {
        if (!(self instanceof ServerPlayer sp)) return;

        if (data.wallRunning == data.lastSentRunning
                && data.wallSide == data.lastSentSide) {
            return;
        }

        data.lastSentRunning = data.wallRunning;
        data.lastSentSide = data.wallSide;

        int ordinal = (data.wallRunning && data.wallSide != null)
                ? data.wallSide.ordinal() : 0;

        ServerPlayNetworking.send(sp,
                new GhostrunnerNetworking.WallRunStatePayload(data.wallRunning, ordinal));
    }

    // ================================================================
    //                      死亡清理
    // ================================================================

    public static void onDeath(Player self, GhostrunnerData data) {
        data.inBulletTime = false;
        data.blockHeld = false;
        data.blockReleaseMs = System.currentTimeMillis();
        if (self instanceof ServerPlayer sp) BulletTimeManager.exit(sp);
    }
}