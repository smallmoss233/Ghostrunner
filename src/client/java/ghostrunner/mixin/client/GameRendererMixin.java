package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerClient;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * renderLevel 里第一个 Matrix4f 局部变量是投影矩阵。
     * 叠加 Z 轴旋转产生跑墙的镜头倾斜（Roll）。
     */
    @ModifyVariable(
            method = "renderLevel",
            at = @At(value = "STORE", ordinal = 0),
            ordinal = 0
    )
    private Matrix4f ghostrunner$addWallRunRoll(Matrix4f projectionMatrix) {
        float rollRad = GhostrunnerClient.currentRoll;
        if (rollRad == 0.0F) return projectionMatrix;
        // rotateZ 会原地修改并返回 this
        return projectionMatrix.rotateZ(rollRad);
    }
}