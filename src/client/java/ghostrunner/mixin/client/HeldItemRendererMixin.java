package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerClient;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack,
                                    ModelTransformationMode renderMode, boolean leftHanded,
                                    MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                    int light);

    /**
     * 格挡 / 弹反姿态：主手持剑 + 幽灵行者 + (按住右键 或 弹反瞬间)
     * → 接管第一人称渲染，把剑横举在胸前。
     */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$blockPose(AbstractClientPlayerEntity player, float tickDelta,
                                       float pitch, Hand hand, float swingProgress,
                                       ItemStack item, float equipProgress,
                                       MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                       int light, CallbackInfo ci) {

        // 只处理主手
        if (hand != Hand.MAIN_HAND) return;
        // 手持剑
        if (!item.isIn(ItemTags.SWORDS)) return;
        // 幽灵行者
        if (!GhostrunnerState.isGhostrunner(player)) return;

        // 触发条件：按住右键（格挡）或 弹反瞬间
        MinecraftClient client = MinecraftClient.getInstance();
        boolean blocking = client.options != null && client.options.useKey.isPressed();
        boolean parryPose = GhostrunnerClient.parryPoseTicks > 0;

        if (!blocking && !parryPose) return;

        // 接管渲染
        ci.cancel();

        Arm arm = player.getMainArm();
        boolean rightArm = arm == Arm.RIGHT;
        int sign = rightArm ? 1 : -1;

        matrices.push();

        // 位置：胸前偏中
        matrices.translate(sign * 0.55F, -0.40F, -0.60F);

        // 剑横过来
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sign * 75.0F));
        // 剑尖朝前
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sign * 80.0F));
        // 略微上倾
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-10.0F));

        // 装备进度补偿
        matrices.translate(0, equipProgress * -0.2F, 0);

        this.renderItem(player, item,
                rightArm ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                        : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                !rightArm, matrices, vertexConsumers, light);

        matrices.pop();
    }
}