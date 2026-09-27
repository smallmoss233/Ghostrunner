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
        // 水平几乎停住 + 垂直抵消重力（缓慢下落）
        player.setVelocity(
                vel.x * 0.15,
                vel.y * 0.10 + 0.02,
                vel.z * 0.15);
        player.velocityModified = true;
        player.fallDistance = 0;
    }
}