package ghostrunner.mixin;

import ghostrunner.Ghostrunner;
import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.GhostrunnerState;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.WallRunHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
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
        GhostrunnerStamina {

    // ============ 耐力系统 ============
    @Unique private float ghostrunner$stamina = 100.0f;
    @Unique private boolean ghostrunner$airDashUsed = false;
    @Unique private float ghostrunner$lastSentStamina = -1.0f;

    // 常量
    @Unique private static final float GR_STAMINA_MAX = 100.0f;
    @Unique private static final float GR_STAMINA_RECOVERY_PER_TICK = 0.625f;  // 每秒 12.5，8 秒回满

    @Unique private int ghostrunner$dashWindowTicks = 0;
    @Unique private int ghostrunner$dashDecayTicks = 0;

    // ============ 跑墙状态 ============
    @Unique private boolean ghostrunner$wallRunning = false;
    @Unique private Direction ghostrunner$wallSide = null;
    @Unique private Vec3d    ghostrunner$lockedDirection = Vec3d.ZERO;
    @Unique private int      ghostrunner$cooldown = 0;
    @Unique private int      ghostrunner$wallRunTicks = 0;
    @Unique private int      ghostrunner$airborneTicks = 0;

    // ============ 网络同步缓存 ============
    @Unique private boolean   ghostrunner$lastSentRunning = false;
    @Unique private Direction ghostrunner$lastSentSide = null;

    // ============ 幽灵行者标记 ============
    @Unique private boolean ghostrunner$ascended = false;

    // ================================================================
    //                       WallRunState
    // ================================================================

    @Override
    public boolean ghostrunner$isWallRunning() {
        return ghostrunner$wallRunning;
    }

    @Override
    public void ghostrunner$clearWallRunCooldown() {
        ghostrunner$cooldown = 0;
    }

    @Override
    public void ghostrunner$startDashWindow(int ticks) {
        ghostrunner$dashWindowTicks = ticks;
    }

    @Override
    public void ghostrunner$startDashDecay(int ticks) {
        ghostrunner$dashDecayTicks = ticks;
    }

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

    @Override
    public boolean ghostrunner$isAscended() {
        return ghostrunner$ascended;
    }

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

        // ★ 冲刺衰减：让冲刺后快速刹停（但落地/跑墙/水中立即取消）
        if (ghostrunner$dashDecayTicks > 0) {
            boolean grounded = self.isOnGround() || WallRunHandler.hasGroundBelow(self);

            if (ghostrunner$wallRunning || grounded
                    || self.isTouchingWater() || self.isInLava()) {
                // 已经落地/跑墙/进水 → 无需继续衰减
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

        //饱食度不消耗
        HungerManager hunger = self.getHungerManager();
        hunger.setFoodLevel(20);
        hunger.setSaturationLevel(20.0F);
        hunger.setExhaustion(0.0F);

        // ★ 溶于水即死
        if (self.isTouchingWater() || self.isInLava()) {
            self.damage(self.getDamageSources().genericKill(), 1.0F);
            return;
        }

        // ★ 摔落伤害清零
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

        // ★ 恢复耐力
        if (ghostrunner$stamina < GR_STAMINA_MAX) {
            ghostrunner$stamina = Math.min(GR_STAMINA_MAX,
                    ghostrunner$stamina + GR_STAMINA_RECOVERY_PER_TICK);
        }

        // ★ 重置空中冲刺（落地 / 跑墙 / 爬墙触发时）
        if (self.isOnGround()
                || WallRunHandler.hasGroundBelow(self)
                || ghostrunner$wallRunning
                || self.isTouchingWater()) {
            ghostrunner$airDashUsed = false;
        }

        // ★ 同步耐力到客户端
        ghostrunner$syncStamina();
    }

    // ================================================================
    //                       NBT 持久化
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
    //                       跑墙内部
    // ================================================================

    @Unique
    private void ghostrunner$tryEnterWallRun(PlayerEntity self) {
        // ============ 冲刺窗口：贴墙必触发，无视一切条件 ============
        if (ghostrunner$dashWindowTicks > 0) {
            Direction wall = WallRunHandler.findWall(self);
            if (wall != null) {
                Vec3d locked = WallRunHandler.computeLockedDirection(self, wall);
                if (locked != null) {
                    ghostrunner$wallRunning = true;
                    ghostrunner$wallSide = wall;
                    ghostrunner$lockedDirection = locked;
                    ghostrunner$wallRunTicks = 0;
                    ghostrunner$dashWindowTicks = 0; // 用掉就清空，避免连续触发
                    return;
                }
            }
        }

        // ============ 常规流程 ============
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
        if (wall == null) {
            ghostrunner$exitWallRun();
            return;
        }

        if (self.isTouchingWater() || self.isInLava()) {
            ghostrunner$exitWallRun();
            return;
        }

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

    @Unique
    private void ghostrunner$syncStamina() {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity sp)) return;

        // 只在变化超过 0.1 时才发包（减少网络开销）
        if (Math.abs(ghostrunner$stamina - ghostrunner$lastSentStamina) < 0.1f) return;
        ghostrunner$lastSentStamina = ghostrunner$stamina;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(ghostrunner$stamina);
        ServerPlayNetworking.send(sp, Ghostrunner.STAMINA_PACKET, buf);
    }

    // ================================================================
    //                       网络同步
    // ================================================================

    @Unique
    private void ghostrunner$syncState() {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity sp)) return;

        // 状态没变不发包
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
    //                    GhostrunnerStamina 实现
    // ================================================================

    @Override
    public float ghostrunner$getStamina() {
        return ghostrunner$stamina;
    }

    @Override
    public void ghostrunner$setStamina(float value) {
        ghostrunner$stamina = Math.max(0, Math.min(GR_STAMINA_MAX, value));
    }

    @Override
    public void ghostrunner$consumeStamina(float amount) {
        ghostrunner$setStamina(ghostrunner$stamina - amount);
    }

    @Override
    public boolean ghostrunner$isAirDashUsed() {
        return ghostrunner$airDashUsed;
    }

    @Override
    public void ghostrunner$setAirDashUsed(boolean used) {
        ghostrunner$airDashUsed = used;
    }
}