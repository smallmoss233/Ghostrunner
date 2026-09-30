package ghostrunner.mixin;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.handler.MotionSync;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 子弹时间期间压制玩家移动速度。
 * <p>挂在 {@code LivingEntity.travel} 的末尾——原版已经算完移动、落到最终速度后，
 * 我们乘一个衰减系数，让玩家在子弹时间里也看起来在"慢动作"。
 * <p>只处理服务端权威速度。客户端预测由同一方法在客户端侧跑，
 * 两端参数一致所以不冲突。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {

    @Inject(method = "travel", at = @At("TAIL"))
    private void ghostrunner$bulletTimePostTravel(Vec3 movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        if (!GhostrunnerPlayer.of(player).ghostrunner$isInBulletTime()) return;

        Vec3 vel = player.getDeltaMovement();

        double newVx = vel.x * GhostrunnerConfig.BT_TRAVEL_DAMP_H;
        double newVz = vel.z * GhostrunnerConfig.BT_TRAVEL_DAMP_H;
        double newVy = vel.y * GhostrunnerConfig.BT_TRAVEL_DAMP_V;

        // 垂直速度下限：浮空时至少缓慢下落，避免卡在"静止"状态
        if (newVy > GhostrunnerConfig.BT_TRAVEL_MIN_VY) {
            newVy = GhostrunnerConfig.BT_TRAVEL_MIN_VY;
        }

        MotionSync.setAndSync(player, newVx, newVy, newVz);
        player.fallDistance = 0;
    }
}