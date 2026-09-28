package ghostrunner.mixin;

import ghostrunner.api.BulletTimeState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {

    @Inject(method = "travel", at = @At("TAIL"))
    private void ghostrunner$bulletTimePostTravel(Vec3d movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerEntity player)) return;
        BulletTimeState bt = (BulletTimeState) player;
        if (!bt.ghostrunner$isInBulletTime()) return;

        Vec3d vel = player.getVelocity();

        // 水平：快速衰减
        double newVx = vel.x * 0.15;
        double newVz = vel.z * 0.15;

        // 垂直：衰减，但接近 0 时锁到缓慢下落（-0.01），绝不上飘
        double newVy = vel.y * 0.10;
        if (newVy > -0.01) {
            newVy = -0.01;   // 保证至少有 0.01 的下落速度
        }

        player.setVelocity(newVx, newVy, newVz);
        player.velocityModified = true;
        player.fallDistance = 0;
    }
}