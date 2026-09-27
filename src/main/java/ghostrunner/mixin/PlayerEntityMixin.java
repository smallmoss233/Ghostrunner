package ghostrunner.mixin;

import ghostrunner.api.WallRunState;
import ghostrunner.handler.WallRunHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements WallRunState {

    @Unique private boolean ghostrunner$wallRunning = false;
    @Unique private Direction ghostrunner$wallSide = null;
    @Unique private Vec3d    ghostrunner$lockedDirection = Vec3d.ZERO;
    @Unique private int      ghostrunner$cooldown = 0;
    @Unique private int      ghostrunner$wallRunTicks = 0;
    @Unique private int      ghostrunner$airborneTicks = 0;

    // ============ WallRunState ============

    @Override
    public boolean ghostrunner$isWallRunning() {
        return ghostrunner$wallRunning;
    }

    @Override
    public void ghostrunner$jumpOffWall() {
        if (!ghostrunner$wallRunning || ghostrunner$wallSide == null) return;
        // 防止刚进入跑墙还没到 MIN 就被跳出
        if (ghostrunner$wallRunTicks < WallRunHandler.MIN_WALL_RUN_TICKS) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        WallRunHandler.jumpOffWall(self, ghostrunner$wallSide);
        ghostrunner$exitWallRun();
    }

    // ============ Tick ============

    @Inject(method = "tick", at = @At("HEAD"))
    private void ghostrunner$onTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        if (ghostrunner$cooldown > 0) ghostrunner$cooldown--;

        // 维护离地 tick 计数
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
    }

    // ============ 进入跑墙 ============

    @Unique
    private void ghostrunner$tryEnterWallRun(PlayerEntity self) {
        if (ghostrunner$cooldown > 0) return;
        if (ghostrunner$airborneTicks < WallRunHandler.MIN_AIRBORNE_TICKS) return;
        if (!WallRunHandler.canEnter(self)) return;

        Direction wall = WallRunHandler.findWall(self);
        if (wall == null) return;

        // 速度必须朝向墙面，否则不触发（防止贴墙擦过去或掉下悬崖时误触发）
        if (!WallRunHandler.isMovingTowardWall(self, wall)) return;

        Vec3d locked = WallRunHandler.computeLockedDirection(self, wall);
        if (locked == null) return;

        ghostrunner$wallRunning = true;
        ghostrunner$wallSide = wall;
        ghostrunner$lockedDirection = locked;
        ghostrunner$wallRunTicks = 0;
    }

    // ============ 跑墙物理 ============

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

        // 用锁定的方向，不再看当前视角
        Vec3d velocity = WallRunHandler.velocityFromLockedDirection(ghostrunner$lockedDirection);
        self.setVelocity(velocity.x, velocity.y, velocity.z);
        self.velocityModified = true;
        self.fallDistance = 0;
    }

    // ============ 退出 ============

    @Unique
    private void ghostrunner$exitWallRun() {
        ghostrunner$wallRunning = false;
        ghostrunner$wallSide = null;
        ghostrunner$lockedDirection = Vec3d.ZERO;
        ghostrunner$wallRunTicks = 0;
        ghostrunner$cooldown = WallRunHandler.REENTRY_COOLDOWN;
    }
}