package ghostrunner.mixin;

import ghostrunner.handler.BulletTimeManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class ServerWorldTickEntityMixin {

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$bulletTimeSkip(Entity entity, CallbackInfo ci) {
        ServerWorld self = (ServerWorld) (Object) this;
        if (BulletTimeManager.shouldSkip(self, entity)) {
            ci.cancel();
        }
    }
}