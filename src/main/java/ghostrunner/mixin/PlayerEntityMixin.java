package ghostrunner.mixin;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.data.GhostrunnerData;
import ghostrunner.handler.BulletTimeManager;
import ghostrunner.handler.GhostrunnerTickHandler;
import ghostrunner.handler.WallRunHandler;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
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
public abstract class PlayerEntityMixin implements GhostrunnerPlayer {

    /** 唯一的 @Unique 字段。所有状态都在里面。 */
    @Unique
    private final GhostrunnerData ghostrunner$data = new GhostrunnerData();

    // ================================================================
    //                      数据容器
    // ================================================================

    @Override
    public GhostrunnerData ghostrunner$data() {
        return ghostrunner$data;
    }

    // ================================================================
    //                      改造标记
    // ================================================================

    @Override
    public boolean ghostrunner$isAscended() {
        return ghostrunner$data.ascended;
    }

    @Override
    public void ghostrunner$setAscended(boolean value) {
        ghostrunner$data.ascended = value;
        if ((Object) this instanceof ServerPlayer sp) {
            ServerPlayNetworking.send(sp, new GhostrunnerNetworking.AscendedStatePayload(value));
        }
    }

    // ================================================================
    //                      耐力
    // ================================================================

    @Override
    public float ghostrunner$getStamina() {
        return ghostrunner$data.stamina;
    }

    @Override
    public void ghostrunner$setStamina(float value) {
        ghostrunner$data.stamina = Math.max(0f, Math.min(GhostrunnerConfig.STAMINA_MAX, value));
    }

    @Override
    public void ghostrunner$consumeStamina(float amount) {
        ghostrunner$setStamina(ghostrunner$data.stamina - amount);
    }

    // ================================================================
    //                      格挡
    // ================================================================

    @Override
    public boolean ghostrunner$isBlocking() {
        return ghostrunner$data.blockHeld;
    }

    @Override
    public void ghostrunner$setBlocking(boolean held) {
        if (held == ghostrunner$data.blockHeld) return;

        long now = System.currentTimeMillis();

        if (held) {
            // 硬直期内不能重新格挡
            if (now - ghostrunner$data.blockReleaseMs < GhostrunnerConfig.BLOCK_RECOVERY_MS) {
                return;
            }
            ghostrunner$data.blockStartMs = now;
        } else {
            ghostrunner$data.blockReleaseMs = now;
        }

        ghostrunner$data.blockHeld = held;
    }

    // ================================================================
    //                      跑墙
    // ================================================================

    @Override
    public boolean ghostrunner$isWallRunning() {
        return ghostrunner$data.wallRunning;
    }

    @Override
    public void ghostrunner$jumpOffWall() {
        GhostrunnerTickHandler.jumpOffWall((Player) (Object) this, ghostrunner$data);
    }

    // ================================================================
    //                      子弹时间
    // ================================================================

    @Override
    public boolean ghostrunner$isInBulletTime() {
        return ghostrunner$data.inBulletTime;
    }

    @Override
    public boolean ghostrunner$tryEnterBulletTime() {
        return GhostrunnerTickHandler.tryEnterBulletTime((Player) (Object) this, ghostrunner$data);
    }

    @Override
    public void ghostrunner$exitBulletTimeAndDash() {
        GhostrunnerTickHandler.exitBulletTimeAndDash((Player) (Object) this, ghostrunner$data);
    }

    @Override
    public void ghostrunner$updateBulletTimeAim(Vec3 direction) {
        GhostrunnerTickHandler.updateBulletTimeAim(ghostrunner$data, direction);
    }

    @Override
    public Vec3 ghostrunner$getBulletTimeAim() {
        return ghostrunner$data.btAim;
    }

    // ================================================================
    //                      Tick
    // ================================================================

    @Inject(method = "tick", at = @At("HEAD"))
    private void ghostrunner$onTick(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) return;
        GhostrunnerTickHandler.serverTick(self, ghostrunner$data);
    }

    // ================================================================
    //                      NBT
    // ================================================================

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void ghostrunner$writeNbt(ValueOutput output, CallbackInfo ci) {
        ghostrunner$data.save(output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void ghostrunner$readNbt(ValueInput input, CallbackInfo ci) {
        ghostrunner$data.load(input);
    }

    // ================================================================
    //                      死亡清理
    // ================================================================

    @Inject(method = "die", at = @At("HEAD"))
    private void ghostrunner$onDeath(DamageSource source, CallbackInfo ci) {
        GhostrunnerTickHandler.onDeath((Player) (Object) this, ghostrunner$data);
    }

    // ================================================================
    //                      摔落免疫
    // ================================================================

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$noFallDamageDuringBulletTime(double fallDistance, float damageModifier,
                                                          DamageSource source,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (System.currentTimeMillis() < ghostrunner$data.fallGraceUntilMs) {
            cir.setReturnValue(false);
        }
    }
}