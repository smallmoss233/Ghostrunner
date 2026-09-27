package ghostrunner.mixin;

import ghostrunner.Ghostrunner;
import ghostrunner.api.BulletTimeState;
import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.GhostrunnerState;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.BulletTimeManager;
import ghostrunner.handler.DashHandler;
import ghostrunner.handler.WallRunHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin
        implements WallRunState, GhostrunnerState.GhostrunnerStateAccessor,
        GhostrunnerStamina, BulletTimeState {

    // ============ 常量 ============
    @Unique private static final float GR_STAMINA_MAX = 100.0f;
    @Unique private static final float GR_STAMINA_RECOVERY_PER_TICK = 0.625f;
    @Unique private static final float BT_STAMINA_PER_TICK = 1.0f;
    @Unique private static final double BT_RELEASE_SPEED = 1.5;

    // ============ 耐力 ============
    @Unique private float ghostrunner$stamina = 100.0f;
    @Unique private boolean ghostrunner$airDashUsed = false;
    @Unique private float ghostrunner$lastSentStamina = -1.0f;

    // ============ 冲刺 ============
    @Unique private int ghostrunner$dashWindowTicks = 0;
    @Unique private int ghostrunner$dashDecayTicks = 0;

    // ============ 跑墙 ============
    @Unique private boolean ghostrunner$wallRunning = false;
    @Unique private Direction ghostrunner$wallSide = null;
    @Unique private Vec3d    ghostrunner$lockedDirection = Vec3d.ZERO;
    @Unique private int      ghostrunner$cooldown = 0;
    @Unique private int      ghostrunner$wallRunTicks = 0;
    @Unique private int      ghostrunner$airborneTicks = 0;

    // ============ 网络缓存 ============
    @Unique private boolean   ghostrunner$lastSentRunning = false;
    @Unique private Direction ghostrunner$lastSentSide = null;

    // ============ 标记 ============
    @Unique private boolean ghostrunner$ascended = false;

    // ============ 子弹时间 ============
    @Unique private boolean ghostrunner$inBulletTime = false;
    @Unique private Vec3d   ghostrunner$btAim = Vec3d.ZERO;
    @Unique private int     ghostrunner$btTicks = 0;

    // ================================================================
    //                       WallRunState
    // ================================================================

    @Override public boolean ghostrunner$isWallRunning() { return ghostrunner$wallRunning; }

    @Override public void ghostrunner$clearWallRunCooldown() { ghostrunner$cooldown = 0; }

    @Override public void ghostrunner$startDashWindow(int ticks) { ghostrunner$dashWindowTicks = ticks; }

    @Override public void ghostrunner$startDashDecay(int ticks) { ghostrunner$dashDecayTicks = ticks; }

    @Override
    public void ghostrunner$jumpOffWall() {
        if (!ghostrunner$wallRunning || ghostrunner$wallSide == null) return;
        if (ghostrunner$wallRunTicks < WallRunHandler.MIN_WALL_RUN_TICKS) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
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
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self instanceof ServerPlayerEntity sp) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(value);
            ServerPlayNetworking.send(sp, Ghostrunner.ASCENDED_STATE_PACKET, buf);
        }
    }

    // ================================================================
    //                           Tick
    // ================================================================

    @Inject(method = "tick", at = @At("HEAD"))
    private void ghostrunner$onTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        // 冲刺后衰减
        if (ghostrunner$dashDecayTicks > 0) {
            boolean grounded = self.isOnGround() || WallRunHandler.hasGroundBelow(self);
            if (ghostrunner$wallRunning || grounded
                    || self.isTouchingWater() || self.isInLava()) {
                ghostrunner$dashDecayTicks = 0;
            } else {
                ghostrunner$dashDecayTicks--;
                Vec3d vel = self.getVelocity();
                self.setVelocity(vel.x * 0.90, vel.y, vel.z * 0.90);
                self.velocityModified = true;
            }
        }

        if (!GhostrunnerState.isGhostrunner(self)) {
            if (ghostrunner$wallRunning) {
                ghostrunner$exitWallRun();
                ghostrunner$syncState();
            }
            return;
        }

        // 饱食度锁满
        HungerManager hunger = self.getHungerManager();
        hunger.setFoodLevel(20);
        hunger.setSaturationLevel(20.0F);
        hunger.setExhaustion(0.0F);

        // 子弹时间：只处理耐力和倒计时，速度衰减在 LivingEntityTravelMixin
        if (ghostrunner$inBulletTime) {
            ghostrunner$btTicks++;
            ghostrunner$consumeStamina(BT_STAMINA_PER_TICK);
            if (ghostrunner$stamina <= 0) {
                ghostrunner$exitBulletTimeAndDash();
            }
        }

        // 溶于水即死
        if (self.isTouchingWater() || self.isInLava()) {
            self.damage(self.getDamageSources().genericKill(), 1.0F);
            return;
        }

        // 摔落清零
        if (self.fallDistance > 0) {
            self.fallDistance = 0;
        }

        if (ghostrunner$cooldown > 0) ghostrunner$cooldown--;
        if (ghostrunner$dashWindowTicks > 0) ghostrunner$dashWindowTicks--;

        if (self.isOnGround() || WallRunHandler.hasGroundBelow(self)) {
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

        // 恢复耐力
        if (ghostrunner$stamina < GR_STAMINA_MAX) {
            ghostrunner$stamina = Math.min(GR_STAMINA_MAX,
                    ghostrunner$stamina + GR_STAMINA_RECOVERY_PER_TICK);
        }

        // 重置空中冲刺
        if (self.isOnGround()
                || WallRunHandler.hasGroundBelow(self)
                || ghostrunner$wallRunning
                || self.isTouchingWater()) {
            ghostrunner$airDashUsed = false;
        }

        ghostrunner$syncStamina();
    }

    // ================================================================
    //                       NBT
    // ================================================================

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void ghostrunner$writeNbt(NbtCompound nbt, CallbackInfo ci) {
        nbt.putBoolean("GhostrunnerAscended", ghostrunner$ascended);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void ghostrunner$readNbt(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("GhostrunnerAscended")) {
            ghostrunner$ascended = nbt.getBoolean("GhostrunnerAscended");
        }
    }

    // ================================================================
    //                       跑墙
    // ================================================================

    @Unique
    private void ghostrunner$tryEnterWallRun(PlayerEntity self) {
        // 冲刺窗口：贴墙必触发
        if (ghostrunner$dashWindowTicks > 0) {
            Direction wall = WallRunHandler.findWall(self);
            if (wall != null) {
                Vec3d locked = WallRunHandler.computeLockedDirection(self, wall);
                if (locked != null) {
                    ghostrunner$wallRunning = true;
                    ghostrunner$wallSide = wall;
                    ghostrunner$lockedDirection = locked;
                    ghostrunner$wallRunTicks = 0;
                    ghostrunner$dashWindowTicks = 0;
                    return;
                }
            }
        }

        // 常规流程
        if (ghostrunner$cooldown > 0) return;
        if (ghostrunner$airborneTicks < WallRunHandler.MIN_AIRBORNE_TICKS) return;
        if (!WallRunHandler.canEnter(self)) return;

        Direction wall = WallRunHandler.findWall(self);
        if (wall == null) return;
        if (!WallRunHandler.isMovingTowardWall(self, wall)) return;

        Vec3d locked = WallRunHandler.computeLockedDirection(self, wall);
        if (locked == null) return;

        ghostrunner$wallRunning = true;
        ghostrunner$wallSide = wall;
        ghostrunner$lockedDirection = locked;
        ghostrunner$wallRunTicks = 0;
    }

    @Unique
    private void ghostrunner$updateWallRun(PlayerEntity self) {
        ghostrunner$wallRunTicks++;

        Direction wall = WallRunHandler.findWall(self);
        if (wall == null) { ghostrunner$exitWallRun(); return; }
        if (self.isTouchingWater() || self.isInLava()) { ghostrunner$exitWallRun(); return; }

        ghostrunner$wallSide = wall;

        Vec3d velocity = WallRunHandler.velocityFromLockedDirection(ghostrunner$lockedDirection);
        self.setVelocity(velocity.x, velocity.y, velocity.z);
        self.velocityModified = true;
        self.fallDistance = 0;
    }

    @Unique
    private void ghostrunner$exitWallRun() {
        ghostrunner$wallRunning = false;
        ghostrunner$wallSide = null;
        ghostrunner$lockedDirection = Vec3d.ZERO;
        ghostrunner$wallRunTicks = 0;
        ghostrunner$cooldown = WallRunHandler.REENTRY_COOLDOWN;
    }

    // ================================================================
    //                       网络同步
    // ================================================================

    @Unique
    private void ghostrunner$syncStamina() {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity sp)) return;
        if (Math.abs(ghostrunner$stamina - ghostrunner$lastSentStamina) < 0.1f) return;
        ghostrunner$lastSentStamina = ghostrunner$stamina;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(ghostrunner$stamina);
        ServerPlayNetworking.send(sp, Ghostrunner.STAMINA_PACKET, buf);
    }

    @Unique
    private void ghostrunner$syncState() {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity sp)) return;

        if (ghostrunner$wallRunning == ghostrunner$lastSentRunning
                && ghostrunner$wallSide == ghostrunner$lastSentSide) {
            return;
        }

        ghostrunner$lastSentRunning = ghostrunner$wallRunning;
        ghostrunner$lastSentSide = ghostrunner$wallSide;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(ghostrunner$wallRunning);
        if (ghostrunner$wallRunning && ghostrunner$wallSide != null) {
            buf.writeEnumConstant(ghostrunner$wallSide);
        }
        ServerPlayNetworking.send(sp, Ghostrunner.WALL_RUN_STATE_PACKET, buf);
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

    // ================================================================
    //                    BulletTimeState
    // ================================================================

    @Override public boolean ghostrunner$isInBulletTime() { return ghostrunner$inBulletTime; }

    @Override
    public boolean ghostrunner$tryEnterBulletTime() {
        if (ghostrunner$inBulletTime) return false;

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isOnGround() || WallRunHandler.hasGroundBelow(self)) return false;
        if (self.isTouchingWater() || self.isInLava()) return false;
        if (self.isFallFlying()) return false;
        if (ghostrunner$stamina < 10.0f) return false;

        // 空中冲刺已用 → 不允许进入
        if (ghostrunner$airDashUsed) return false;

        ghostrunner$inBulletTime = true;
        ghostrunner$btTicks = 0;
        ghostrunner$btAim = self.getRotationVec(1.0F).normalize();

        // 消耗空中冲刺
        ghostrunner$airDashUsed = true;

        if (self instanceof ServerPlayerEntity sp) {
            BulletTimeManager.enter(sp);

            // 进入音效
            sp.getWorld().playSound(null,
                    sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.PLAYERS, 0.6f, 1.8f);

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(true);
            ServerPlayNetworking.send(sp, Ghostrunner.BULLET_TIME_STATE_PACKET, buf);
        }
        return true;
    }

    @Override
    public void ghostrunner$updateBulletTimeAim(Vec3d direction) {
        if (!ghostrunner$inBulletTime) return;
        if (direction.lengthSquared() < 0.0001) return;
        ghostrunner$btAim = direction.normalize();
    }

    @Override public Vec3d ghostrunner$getBulletTimeAim() { return ghostrunner$btAim; }

    @Override
    public void ghostrunner$exitBulletTimeAndDash() {
        if (!ghostrunner$inBulletTime) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        ghostrunner$inBulletTime = false;

        if (self instanceof ServerPlayerEntity sp) {
            BulletTimeManager.exit(sp);
        }

        Vec3d dir = ghostrunner$btAim;
        if (dir.lengthSquared() < 0.0001) {
            dir = self.getRotationVec(1.0F).normalize();
        }
        self.setVelocity(
                dir.x * BT_RELEASE_SPEED,
                dir.y * BT_RELEASE_SPEED + 0.05,
                dir.z * BT_RELEASE_SPEED);
        self.velocityModified = true;
        self.fallDistance = 0;

        ghostrunner$startDashDecay(DashHandler.DASH_DECAY_TICKS);
        ghostrunner$startDashWindow(DashHandler.DASH_WALL_WINDOW_TICKS);

        if (self instanceof ServerPlayerEntity sp) {
            // 释放音效
            sp.getWorld().playSound(null,
                    sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS, 1.0f, 1.4f);

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(false);
            ServerPlayNetworking.send(sp, Ghostrunner.BULLET_TIME_STATE_PACKET, buf);

            PacketByteBuf fx = PacketByteBufs.create();
            ServerPlayNetworking.send(sp, Ghostrunner.DASH_SUCCESS_PACKET, fx);
        }
    }

    // ================================================================
    //                       死亡清理
    // ================================================================

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void ghostrunner$onDeath(DamageSource source, CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;

        ghostrunner$inBulletTime = false;

        if (self instanceof ServerPlayerEntity sp) {
            BulletTimeManager.exit(sp);
        }
    }
}