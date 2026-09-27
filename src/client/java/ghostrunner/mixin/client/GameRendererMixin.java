package ghostrunner.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import ghostrunner.GhostrunnerClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setInverseViewRotationMatrix(Lorg/joml/Matrix3f;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void ghostrunner$addWallRunRoll(CallbackInfo ci,
                                            @Local(argsOnly = true) MatrixStack matrices) {
        float rollRad = GhostrunnerClient.currentRoll;
        if (rollRad == 0.0F) return;

        // rotateLocalZ = 左乘 Rz，作用于相机本地坐标系，等价于"绕视线轴翻滚"
        matrices.peek().getPositionMatrix().rotateLocalZ(rollRad);
    }
}