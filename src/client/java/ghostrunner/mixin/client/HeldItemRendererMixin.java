package ghostrunner.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import ghostrunner.GhostrunnerClient;
import ghostrunner.api.GhostrunnerPlayer;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称剑的持握姿态。
 * <p>格挡时把剑从原版位置改到"横在胸前"，并随时间/弹反信号切换姿态。
 * <p><b>姿态定义</b>：每个姿态是 6 元组 {@code {tx, ty, tz, rx, ry, rz}}——
 * 位置是手臂相对偏移（单位：方块），旋转是欧拉角（度，应用顺序 Z→Y→X）。
 * <p><b>平滑</b>：每帧向目标姿态做指数衰减插值，切换阶段时自动有过渡动画。
 * <p><b>状态检测</b>：本地按住右键 = 意图格挡；{@code parryPoseTimer > 0} = 弹反瞬间。
 * 用本地输入和本地时间戳推算阶段，不做服务端同步——视觉即时反馈优先于权威同步。
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererMixin {

    // ================================================================
    //                      姿态常量
    // ================================================================

    /** 中立姿态：接近原版第一人称位置，松手后滑回这里。 */
    @Unique private static final float[] POSE_NEUTRAL  = {0.35f, -0.75f, -0.55f,   0f,   0f,  0f};
    /** 前摇：剑斜向上抬起，准备迎接攻击。 */
    @Unique private static final float[] POSE_PREPARE  = {0.45f, -0.55f, -0.65f,  15f,  45f, 50f};
    /** 完美窗口：剑竖起立在身前。 */
    @Unique private static final float[] POSE_PARRY    = {0.50f, -0.45f, -0.70f,   0f,  90f,  0f};
    /** 普通格挡：剑横在胸前。 */
    @Unique private static final float[] POSE_BLOCKING = {0.55f, -0.40f, -0.60f, -10f,  80f, 75f};
    /** 弹反瞬间：剑向前刺出。 */
    @Unique private static final float[] POSE_STRIKE   = {0.30f, -0.35f, -0.95f,   0f,   0f,  0f};

    // ================================================================
    //                      时间阈值
    // ================================================================

    /** 前摇时长（毫秒）。和 {@code GhostrunnerConfig.BLOCK_PREPARE_MS} 语义对应。 */
    @Unique private static final long HOLD_PREPARE_MS = 300L;
    /** 完美窗口时长（毫秒）。 */
    @Unique private static final long HOLD_PARRY_MS   = 300L;
    /** 松手后姿态回落的接管时长——这段内继续渲染，让剑平滑滑回中立。 */
    @Unique private static final long TRANSITION_MS   = 300L;
    /** 平滑速率。越大越快，25 ≈ 每帧约 40% 趋近。 */
    @Unique private static final float SMOOTH_RATE    = 25f;

    // ================================================================
    //                      状态
    // ================================================================

    /** 本地按下右键的时间戳。0 = 未按住。 */
    @Unique private static long pressStartMs = 0L;
    /** 最近一次"接管渲染"的时间戳——用于控制松手后的过渡。 */
    @Unique private static long lastActiveMs = 0L;
    /** 上一帧的纳秒时间戳——用于算 dt。 */
    @Unique private static long lastFrameNs = 0L;
    /** 当前姿态，逐帧向目标姿态插值。 */
    @Unique private static final float[] currentPose = POSE_NEUTRAL.clone();

    // ================================================================
    //                      主注入
    // ================================================================

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$customPose(PlayerRenderState playerState,
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

        // ---- 过滤 ----
        if (hand != InteractionHand.MAIN_HAND) return;
        if (!itemStack.is(ItemTags.SWORDS)) return;

        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.getId() != avatar.id) return;
        if (!GhostrunnerPlayer.isGhostrunner(client.player)) return;

        // ---- 是否接管渲染 ----
        long nowMs = System.currentTimeMillis();
        boolean blocking = client.options != null && client.options.keyUse.isDown();
        boolean parryPose = GhostrunnerClient.parryPoseTimer > 0f;
        boolean active = blocking || parryPose;

        if (active) lastActiveMs = nowMs;
        boolean inTransition = nowMs - lastActiveMs < TRANSITION_MS;

        if (!active && !inTransition) return;
        ci.cancel();

        // ---- 姿态目标 ----
        float[] target = selectTargetPose(nowMs, blocking, parryPose);

        // ---- 平滑 + 应用 ----
        float k = smoothingFactor();
        for (int i = 0; i < 6; i++) {
            currentPose[i] += (target[i] - currentPose[i]) * k;
        }

        applyPose(poseStack, currentPose, avatar.mainArm,
                inverseArmHeight, state.mainHandRenderState,
                submitNodeCollector, lightCoords);
    }

    // ================================================================
    //                      姿态选择
    // ================================================================

    @Unique
    private static float[] selectTargetPose(long nowMs, boolean blocking, boolean parryPose) {
        // 弹反瞬间最高优先级
        if (parryPose) return POSE_STRIKE;

        // 未按住 → 中立
        if (!blocking) {
            pressStartMs = 0L;
            return POSE_NEUTRAL;
        }

        // 记录按下时刻
        if (pressStartMs == 0L) pressStartMs = nowMs;

        long heldMs = nowMs - pressStartMs;
        if (heldMs < HOLD_PREPARE_MS) return POSE_PREPARE;
        if (heldMs < HOLD_PREPARE_MS + HOLD_PARRY_MS) return POSE_PARRY;
        return POSE_BLOCKING;
    }

    // ================================================================
    //                      平滑
    // ================================================================

    /**
     * 计算本帧的平滑系数。用指数衰减 {@code 1 - exp(-dt * rate)}——
     * 与帧率无关，掉帧时也稳定。
     */
    @Unique
    private static float smoothingFactor() {
        long nowNs = System.nanoTime();
        float dtSec = lastFrameNs == 0L ? 0.1f
                : (nowNs - lastFrameNs) / 1_000_000_000f;
        lastFrameNs = nowNs;

        if (dtSec > 0.5f) dtSec = 0.5f;
        if (dtSec < 0f) dtSec = 0f;

        return 1f - (float) Math.exp(-dtSec * SMOOTH_RATE);
    }

    // ================================================================
    //                      姿态应用
    // ================================================================

    @Unique
    private static void applyPose(PoseStack poseStack,
                                  float[] pose,
                                  HumanoidArm mainArm,
                                  float inverseArmHeight,
                                  ItemStackRenderState itemState,
                                  SubmitNodeCollector collector,
                                  int lightCoords) {
        boolean rightArm = mainArm == HumanoidArm.RIGHT;
        int sign = rightArm ? 1 : -1;

        poseStack.pushPose();

        // 位置偏移
        poseStack.translate(sign * pose[0], pose[1], pose[2]);

        // 欧拉角旋转（顺序 Z→Y→X）
        poseStack.rotateDegrees(Axis.ZP, sign * pose[5]);
        poseStack.rotateDegrees(Axis.YP, sign * pose[4]);
        poseStack.rotateDegrees(Axis.XP, pose[3]);

        // 装备进度补偿——保持和原版一致，避免切换时跳
        poseStack.translate(0.0F, inverseArmHeight * -0.2F, 0.0F);

        // 提交物品渲染
        itemState.submit(poseStack, collector, lightCoords,
                OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}