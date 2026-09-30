package ghostrunner.mixin;

import ghostrunner.api.BulletTimeState;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityTravelMixin {

    @Inject(method = "travel", at = @At("TAIL"))
    private void ghostrunner$bulletTimePostTravel(Vec3 movementInput, CallbackInfo ci) {
        if (!((Object) this instanceof Player player)) return;
        BulletTimeState bt = (BulletTimeState) player;
        if (!bt.ghostrunner$isInBulletTime()) return;

        Vec3 vel = player.getDeltaMovement();

        double newVx = vel.x * 0.15;
        double newVz = vel.z * 0.15;
        double newVy = vel.y * 0.10;
        if (newVy > -0.01) {
            newVy = -0.01;
        }

        player.setDeltaMovement(newVx, newVy, newVz);

        // ★ 修正：26.3 无 hurtMarked，直接发包同步速度
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }

        player.fallDistance = 0;
    }
}