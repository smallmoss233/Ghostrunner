package ghostrunner.mixin;

import ghostrunner.Ghostrunner;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.WallRunHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
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
public abstract class PlayerEntityMixin implements WallRunState {

    @Unique private boolean ghostrunner$wallRunning = false;
    @Unique private Direction ghostrunner$wallSide = null;
    @Unique private Vec3d    ghostrunner$lockedDirection = Vec3d.ZERO;
    @Unique private int      ghostrunner$cooldown = 0;
    @Unique private int      ghostrunner$wallRunTicks = 0;
    @Unique private int      ghostrunner$airborneTicks = 0;

    // ★ 用于检测状态变化，避免每 tick 发包
    @Unique private boolean  ghostrunner$lastSentRunning = false;
    @Unique private Direction ghostrunner$lastSentSide = null;

    // ============ WallRunState ============

    @Override
    public boolean ghostrunner$isWallRunning() {
        return ghostrunner$wallRunning;
    }

    @Override
    public void ghostrunner$jumpOffWall() {
        if (!ghostrunner$wallRunning || ghostrunner$wallSide == null) return;
        if (ghostrunner$wallRunTicks < WallRunHandler.MIN_WALL_RUN_TICKS) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        WallRunHandler.jumpOffWall(self, ghostrunner$wallSide);
        ghostrunner$exitWallRun();
        ghostrunner$syncState();
    }

    // ============ Tick ============

    @Inject(method = "tick", at = @At("HEAD"))
    private void ghostrunner$onTick(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        if (ghostrunner$cooldown > 0) ghostrunner$cooldown--;

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

        // 每 tick 末尾同步状态（只在变化时真发包）
        ghostrunner$syncState();
    }

    @Unique
    private void ghostrunner$tryEnterWallRun(PlayerEntity self) {
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

    // ============ 状态同步 ============

    @Unique
    private void ghostrunner$syncState() {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!(self instanceof ServerPlayerEntity sp)) return;

        // 状态没变就不发包
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
}