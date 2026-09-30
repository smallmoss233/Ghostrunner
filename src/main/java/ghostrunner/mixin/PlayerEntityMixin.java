package ghostrunner.mixin;

import ghostrunner.api.BulletTimeState;
import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.GhostrunnerState;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.BulletTimeManager;
import ghostrunner.handler.DashHandler;
import ghostrunner.handler.MotionSync;
import ghostrunner.handler.WallRunHandler;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerEntityMixin
        implements WallRunState, GhostrunnerState.GhostrunnerStateAccessor,
        GhostrunnerStamina, BulletTimeState {

    @Unique private static final float GR_STAMINA_MAX = 100.0f;
    @Unique private static final float GR_STAMINA_RECOVERY_PER_TICK = 0.625f;
    @Unique private static final float BT_STAMINA_PER_TICK = 1.0f;
    @Unique private static final float BT_MIN_STAMINA = 10.0f;
    @Unique private static final double BT_RELEASE_SPEED = 1.5;
    @Unique private static final float STAMINA_SYNC_THRESHOLD = 0.1f;
    @Unique private static final double VEC_EPSILON_SQ = 1e-6;
    @Unique private static final double AIM_EPSILON_SQ = 0.0001;

    // ★ 冲刺衰减参数
    @Unique private static final double DASH_DECAY_AIR = 0.90;

    // ============ 耐力 ============
    @Unique private float ghostrunner$stamina = 100.0f;
    @Unique private boolean ghostrunner$airDashUsed = false;
    @Unique private float ghostrunner$lastSentStamina = -1.0f;

    // ============ 冲刺 ============
    @Unique private int ghostrunner$dashWindowTicks = 0;
    @Unique private int ghostrunner$dashDecayTicks = 0;
    @Unique private Vec3 ghostrunner$dashDirection = Vec3.ZERO;
    @Unique private static final double BT_RELEASE_UP_MAX = 0.8;
    @Unique private static final double BT_RELEASE_DOWN_MAX = 1.0;

    // ============ 跑墙 ============
    @Unique private boolean ghostrunner$wallRunning = false;
    @Unique private Direction ghostrunner$wallSide = null;
    @Unique private Vec3    ghostrunner$lockedDirection = Vec3.ZERO;
    @Unique private int     ghostrunner$cooldown = 0;
    @Unique private int     ghostrunner$wallRunTicks = 0;
    @Unique private int     ghostrunner$airborneTicks = 0;

    // ============ 网络缓存 ============
    @Unique private boolean   ghostrunner$lastSentRunning = false;
    @Unique private Direction ghostrunner$lastSentSide = null;

    // ============ 标记 ============
    @Unique private boolean ghostrunner$ascended = false;

    // ============ 子弹时间 ============
    @Unique private boolean ghostrunner$inBulletTime = false;
    @Unique private Vec3    ghostrunner$btAim = Vec3.ZERO;
    @Unique private long ghostrunner$btLastConsumeMs = 0L;

    // ============ 格挡 ============
    @Unique private boolean ghostrunner$blocking = false;
    @Unique private int     ghostrunner$blockTicks = 0;

    // ================================================================
    //                       WallRunState
    // ================================================================

    @Override public boolean ghostrunner$isWallRunning() { return ghostrunner$wallRunning; }
    @Override public void ghostrunner$clearWallRunCooldown() { ghostrunner$cooldown = 0; }
    @Override public void ghostrunner$startDashWindow(int ticks) { ghostrunner$dashWindowTicks = ticks; }
    @Override public void ghostrunner$startDashDecay(int ticks) { ghostrunner$dashDecayTicks = ticks; }

    @Override
    public void ghostrunner$setDashDirection(Vec3 direction) {
        ghostrunner$dashDirection = (direction == null || direction.lengthSqr() < VEC_EPSILON_SQ)
                ? Vec3.ZERO : direction.normalize();
    }

    @Override public Vec3 ghostrunner$getDashDirection() { return ghostrunner$dashDirection; }

    @Override
    public void ghostrunner$jumpOffWall() {
        if (!ghostrunner$wallRunning || ghostrunner$wallSide == null) return;
        if (ghostrunner$wallRunTicks < WallRunHandler.MIN_WALL_RUN_TICKS) return;

        Player self = (Player) (Object) this;
        WallRunHandler.jumpOffWall(self, ghostrunner$wallSide);
        ghostrunner$exitWallRun();
    }

    // ================================================================
    //                   GhostrunnerStateAccessor
    // ================================================================

    @Override public boolean ghostrunner$isAscended() { return ghostrunner$ascended; }

    @Override
    public void ghostrunner$setAscended(boolean value) {
        ghostrunner$ascended = value;
        if ((Object) this instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new GhostrunnerNetworking.AscendedStatePayload(value));
        }
    }

    // ================================================================
    //                           Tick
    // ================================================================

    @Inject(method = "tick", at = @At("HEAD"))
    private void ghostrunner$onTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;

        // ---- 冲刺后衰减 ----
        if (ghostrunner$dashDecayTicks > 0) {
            if (self.onGround() || ghostrunner$wallRunning || self.isInWater() || self.isInLava()) {
                // 触地/跑墙/在水里 → 立刻停止衰减，速度交给游戏自身摩擦力处理
                ghostrunner$dashDecayTicks = 0;
            } else {
                // 只在空中衰减，抵消飞太远
                ghostrunner$dashDecayTicks--;
                Vec3 vel = self.getDeltaMovement();
                MotionSync.setAndSync(self, vel.x * DASH_DECAY_AIR, vel.y, vel.z * DASH_DECAY_AIR);
            }
        }

        // ---- 非 Ghostrunner 退出 ----
        if (!GhostrunnerState.isGhostrunner(self)) {
            if (ghostrunner$wallRunning) {
                ghostrunner$exitWallRun();
                ghostrunner$syncState();
            }
            return;
        }

        // ---- 饱食度锁满 ----
        FoodData food = self.getFoodData();
        if (food.getFoodLevel() < 20) food.setFoodLevel(20);
        if (food.getSaturationLevel() < 20.0F) food.setSaturation(20.0F);

        // ---- 子弹时间 ----
        if (ghostrunner$inBulletTime) {

            long nowMs = System.currentTimeMillis();
            if (ghostrunner$btLastConsumeMs == 0L) {
                ghostrunner$btLastConsumeMs = nowMs;
            }
            long dtMs = nowMs - ghostrunner$btLastConsumeMs;
            if (dtMs > 0) {
                // 每秒扣 20 * BT_STAMINA_PER_TICK，与原 20Hz 手感一致
                float consumeAmount = dtMs / 1000.0f * 20.0f * BT_STAMINA_PER_TICK;
                ghostrunner$consumeStamina(consumeAmount);
                ghostrunner$btLastConsumeMs = nowMs;
            }

            if (ghostrunner$stamina <= 0) {
                ghostrunner$exitBulletTimeAndDash();
            }
        } else {
            ghostrunner$btLastConsumeMs = 0L;
        }

        // ---- 溶于水/岩浆即死 ----
        if (self.isInWater() || self.isInLava()) {
            if (self.level() instanceof ServerLevel serverLevel) {
                self.hurtServer(serverLevel, self.damageSources().genericKill(), 1.0F);
            }
            return;
        }

        // ---- 摔落清零 ----
        if (self.fallDistance > 0) self.fallDistance = 0;

        if (ghostrunner$cooldown > 0) ghostrunner$cooldown--;
        if (ghostrunner$dashWindowTicks > 0) ghostrunner$dashWindowTicks--;

        boolean grounded = self.onGround() || WallRunHandler.hasGroundBelow(self);
        if (grounded) {
            ghostrunner$airborneTicks = 0;
        } else {
            ghostrunner$airborneTicks++;
        }

        if (ghostrunner$wallRunning) {
            ghostrunner$updateWallRun(self);
        } else {
            ghostrunner$tryEnterWallRun(self);
        }
        ghostrunner$syncState();

        // ---- 恢复耐力 ----
        if (!ghostrunner$blocking && ghostrunner$stamina < GR_STAMINA_MAX) {
            ghostrunner$stamina = Math.min(GR_STAMINA_MAX, ghostrunner$stamina + GR_STAMINA_RECOVERY_PER_TICK);
        }

        if (ghostrunner$blocking) ghostrunner$blockTicks++;

        // ---- 重置空中冲刺 ----
        if (grounded || ghostrunner$wallRunning || self.isInWater()) {
            ghostrunner$airDashUsed = false;
        }

        ghostrunner$syncStamina();
    }

    // ================================================================
    //                       NBT
    // ================================================================

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ghostrunner$writeNbt(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("GhostrunnerAscended", ghostrunner$ascended);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ghostrunner$readNbt(ValueInput input, CallbackInfo ci) {
        ghostrunner$ascended = input.getBooleanOr("GhostrunnerAscended", false);
    }

    // ================================================================
    //                       跑墙
    // ================================================================

    @Unique
    private void ghostrunner$tryEnterWallRun(Player self) {
        if (ghostrunner$dashWindowTicks > 0) {
            Direction wall = WallRunHandler.findWall(self);
            if (wall != null && WallRunHandler.isApproachingWallAtAngle(ghostrunner$dashDirection, wall)) {
                Vec3 locked = WallRunHandler.computeLockedDirection(self, wall);
                if (locked != null) {
                    ghostrunner$enterWallRun(wall, locked);
                    ghostrunner$dashWindowTicks = 0;
                    return;
                }
            }
        }

        if (ghostrunner$cooldown > 0) return;
        if (ghostrunner$airborneTicks < WallRunHandler.MIN_AIRBORNE_TICKS) return;
        if (!WallRunHandler.canEnter(self)) return;

        Direction wall = WallRunHandler.findWall(self);
        if (wall == null) return;
        if (!WallRunHandler.isMovingTowardWall(self, wall)) return;

        Vec3 locked = WallRunHandler.computeLockedDirection(self, wall);
        if (locked == null) return;

        ghostrunner$enterWallRun(wall, locked);
    }

    @Unique
    private void ghostrunner$enterWallRun(Direction wall, Vec3 lockedDirection) {
        ghostrunner$wallRunning = true;
        ghostrunner$wallSide = wall;
        ghostrunner$lockedDirection = lockedDirection;
        ghostrunner$wallRunTicks = 0;
    }

    @Unique
    private void ghostrunner$updateWallRun(Player self) {
        ghostrunner$wallRunTicks++;

        Direction wall = WallRunHandler.findWall(self);
        if (wall == null || self.isInWater() || self.isInLava()) {
            ghostrunner$exitWallRun();
            return;
        }

        ghostrunner$wallSide = wall;

        Vec3 velocity = WallRunHandler.velocityFromLockedDirection(ghostrunner$lockedDirection);

        // ★ 水平速度恒定，vy 直接归零覆盖重力
        MotionSync.setAndSync(self, velocity.x, 0.0, velocity.z);
        self.fallDistance = 0;
    }

    @Unique
    private void ghostrunner$exitWallRun() {
        ghostrunner$wallRunning = false;
        ghostrunner$wallSide = null;
        ghostrunner$lockedDirection = Vec3.ZERO;
        ghostrunner$wallRunTicks = 0;
        ghostrunner$cooldown = WallRunHandler.REENTRY_COOLDOWN;
    }

    // ================================================================
    //                       网络同步
    // ================================================================

    @Unique
    private void ghostrunner$syncStamina() {
        if (!((Object) this instanceof ServerPlayer sp)) return;

        // ★ 子弹时间期间不覆盖客户端预测
        if (ghostrunner$inBulletTime) {
            ghostrunner$lastSentStamina = ghostrunner$stamina;
            return;
        }

        if (Math.abs(ghostrunner$stamina - ghostrunner$lastSentStamina) < STAMINA_SYNC_THRESHOLD) return;
        ghostrunner$lastSentStamina = ghostrunner$stamina;

        ServerPlayNetworking.send(sp, new GhostrunnerNetworking.StaminaPayload(ghostrunner$stamina));
    }

    @Unique
    private void ghostrunner$syncState() {
        if (!((Object) this instanceof ServerPlayer sp)) return;

        if (ghostrunner$wallRunning == ghostrunner$lastSentRunning
                && ghostrunner$wallSide == ghostrunner$lastSentSide) {
            return;
        }

        ghostrunner$lastSentRunning = ghostrunner$wallRunning;
        ghostrunner$lastSentSide = ghostrunner$wallSide;

        int ordinal = (ghostrunner$wallRunning && ghostrunner$wallSide != null)
                ? ghostrunner$wallSide.ordinal() : 0;

        ServerPlayNetworking.send(sp, new GhostrunnerNetworking.WallRunStatePayload(ghostrunner$wallRunning, ordinal));
    }

    // ================================================================
    //                    GhostrunnerStamina
    // ================================================================

    @Override public float ghostrunner$getStamina() { return ghostrunner$stamina; }

    @Override public void ghostrunner$setStamina(float value) {
        ghostrunner$stamina = Math.max(0, Math.min(GR_STAMINA_MAX, value));
    }

    @Override public void ghostrunner$consumeStamina(float amount) {
        ghostrunner$setStamina(ghostrunner$stamina - amount);
    }

    @Override public boolean ghostrunner$isAirDashUsed() { return ghostrunner$airDashUsed; }
    @Override public void ghostrunner$setAirDashUsed(boolean used) { ghostrunner$airDashUsed = used; }
    @Override public boolean ghostrunner$isBlocking() { return ghostrunner$blocking; }

    @Override
    public void ghostrunner$setBlocking(boolean blocking) {
        if (blocking && !ghostrunner$blocking) ghostrunner$blockTicks = 0;
        ghostrunner$blocking = blocking;
    }

    @Override public int ghostrunner$getBlockTicks() { return ghostrunner$blockTicks; }

    // ================================================================
    //                    BulletTimeState
    // ================================================================

    @Override public boolean ghostrunner$isInBulletTime() { return ghostrunner$inBulletTime; }

    @Override
    public boolean ghostrunner$tryEnterBulletTime() {
        if (ghostrunner$inBulletTime) return false;

        Player self = (Player) (Object) this;
        if (self.onGround() || WallRunHandler.hasGroundBelow(self)) return false;
        if (self.isInWater() || self.isInLava()) return false;
        if (self.isFallFlying()) return false;
        if (ghostrunner$stamina < BT_MIN_STAMINA) return false;
        if (ghostrunner$airDashUsed) return false;

        ghostrunner$inBulletTime = true;
        ghostrunner$btAim = self.getViewVector(1.0F).normalize();
        ghostrunner$airDashUsed = true;

        if (self instanceof ServerPlayer sp) {
            BulletTimeManager.enter(sp);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
            ServerPlayNetworking.send(sp, new GhostrunnerNetworking.BulletTimeStatePayload(true));
        }
        return true;
    }

    @Override
    public void ghostrunner$updateBulletTimeAim(Vec3 direction) {
        if (!ghostrunner$inBulletTime) return;
        if (direction.lengthSqr() < AIM_EPSILON_SQ) return;
        ghostrunner$btAim = direction.normalize();
    }

    @Override public Vec3 ghostrunner$getBulletTimeAim() { return ghostrunner$btAim; }

    @Override
    public void ghostrunner$exitBulletTimeAndDash() {
        if (!ghostrunner$inBulletTime) return;

        Player self = (Player) (Object) this;
        ghostrunner$inBulletTime = false;

        if (self instanceof ServerPlayer sp) BulletTimeManager.exit(sp);

        Vec3 dir = ghostrunner$btAim;
        if (dir.lengthSqr() < AIM_EPSILON_SQ) dir = self.getViewVector(1.0F).normalize();

        // 计算带限幅的速度
        double vx = dir.x * BT_RELEASE_SPEED;
        double vz = dir.z * BT_RELEASE_SPEED;
        double vy = dir.y * BT_RELEASE_SPEED + 0.05;
        vy = Math.max(-BT_RELEASE_DOWN_MAX, Math.min(BT_RELEASE_UP_MAX, vy));

        MotionSync.setAndSync(self, vx, vy, vz);
        self.fallDistance = 0;

        // 记录水平方向（跑墙窗口用）
        Vec3 horizDir = new Vec3(dir.x, 0, dir.z);
        if (horizDir.lengthSqr() > VEC_EPSILON_SQ) {
            ghostrunner$setDashDirection(horizDir.normalize());
        }

        ghostrunner$startDashDecay(DashHandler.DASH_DECAY_TICKS);
        ghostrunner$startDashWindow(DashHandler.DASH_WALL_WINDOW_TICKS);

        if (self instanceof ServerPlayer sp) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.4f);
            ServerPlayNetworking.send(sp, new GhostrunnerNetworking.BulletTimeStatePayload(false));
            ServerPlayNetworking.send(sp, new GhostrunnerNetworking.DashSuccessPayload());
        }
    }

    // ================================================================
    //                       死亡清理
    // ================================================================

    @Inject(method = "die", at = @At("HEAD"))
    private void ghostrunner$onDeath(DamageSource source, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        ghostrunner$inBulletTime = false;
        ghostrunner$blocking = false;
        if (self instanceof ServerPlayer sp) BulletTimeManager.exit(sp);
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$noFallDamageDuringBulletTime(double fallDistance, float damageModifier,
                                                          DamageSource source,
                                                          CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (BulletTimeManager.shouldImmuneFall(self)) {
            cir.setReturnValue(false);
        }
    }
}