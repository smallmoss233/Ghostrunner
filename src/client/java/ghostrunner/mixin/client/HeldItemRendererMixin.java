package ghostrunner.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ghostrunner.GhostrunnerClient;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$blockPose(PlayerRenderState playerState,
                                       FirstPersonHandsAndItemsRenderState state,
                                       float partialTicks,
                                       float xRot,
                                       InteractionHand hand,
                                       float attack,
                                       ItemStack itemStack,
                                       float inverseArmHeight,
                                       PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector,
                                       int lightCoords,
                                       CallbackInfo ci) {

        // 只处理主手
        if (hand != InteractionHand.MAIN_HAND) return;
        // 手持剑
        if (!itemStack.is(ItemTags.SWORDS)) return;

        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null) return;

        // 确认是本地玩家（第一人称，只用本地玩家的渲染状态）
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.getId() != avatar.id) return;

        // 幽灵行者
        if (!GhostrunnerState.isGhostrunner(client.player)) return;

        // 触发条件：按住右键（格挡）或 弹反瞬间
        boolean blocking = client.options != null && client.options.keyUse.isDown();
        boolean parryPose = GhostrunnerClient.parryPoseTimer > 0f;
        if (!blocking && !parryPose) return;

        // 接管渲染
        ci.cancel();

        HumanoidArm arm = avatar.mainArm;
        boolean rightArm = arm == HumanoidArm.RIGHT;
        int sign = rightArm ? 1 : -1;

        poseStack.pushPose();

        // 位置：胸前偏中
        poseStack.translate(sign * 0.55F, -0.40F, -0.60F);

        // 剑横过来
        poseStack.rotateDegrees(Axis.ZP, sign * 75.0F);
        // 剑尖朝前
        poseStack.rotateDegrees(Axis.YP, sign * 80.0F);
        // 略微上倾
        poseStack.rotateDegrees(Axis.XP, -10.0F);

        // 装备进度补偿
        poseStack.translate(0.0F, inverseArmHeight * -0.2F, 0.0F);

        // 提交物品渲染
        ItemStackRenderState renderState = state.mainHandRenderState;
        renderState.submit(poseStack, submitNodeCollector, lightCoords,
                OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}