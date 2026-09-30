package ghostrunner.gui;

import ghostrunner.GhostrunnerClient;
import ghostrunner.api.GhostrunnerPlayer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 幽灵行者 HUD。
 * <p>挂载顺序 & 职责：
 * <ul>
 *   <li>{@code frame_driver} —— 每帧驱动 {@link GhostrunnerClient#onFrame()}</li>
 *   <li>{@code stamina_bar} —— 弧形耐力条</li>
 *   <li>{@code crosshair} —— 六边形自定义准星（仅第一人称）</li>
 *   <li>{@code dash_effect} —— 冲刺时的边缘暗化 + 速度线</li>
 *   <li>{@code bullet_time} —— 子弹时间的蓝色滤镜</li>
 *   <li>{@code parry_flash} —— 弹反成功的全屏闪光</li>
 * </ul>
 * <p>所有视觉参数（尺寸 / 颜色 / 曲线）都在类内常量区，不在 {@code GhostrunnerConfig} 里——
 * 那些是可调手感的数值参数，这里是固定视觉风格。
 */
public final class GhostrunnerHud {

    private GhostrunnerHud() {}

    // ================================================================
    //                      常量
    // ================================================================

    // 元素 ID
    private static final Identifier ID_FRAME_DRIVER = Identifier.fromNamespaceAndPath("ghostrunner", "frame_driver");
    private static final Identifier ID_STAMINA_BAR  = Identifier.fromNamespaceAndPath("ghostrunner", "stamina_bar");
    private static final Identifier ID_CROSSHAIR    = Identifier.fromNamespaceAndPath("ghostrunner", "crosshair");
    private static final Identifier ID_DASH_EFFECT  = Identifier.fromNamespaceAndPath("ghostrunner", "dash_effect");
    private static final Identifier ID_BULLET_TIME  = Identifier.fromNamespaceAndPath("ghostrunner", "bullet_time");
    private static final Identifier ID_PARRY_FLASH  = Identifier.fromNamespaceAndPath("ghostrunner", "parry_flash");

    // 耐力条布局
    private static final int   BAR_HALF_WIDTH  = 90;
    private static final int   BAR_CURVE_DEPTH = 6;
    private static final float BAR_Y_RATIO     = 0.58f;

    // 耐力条配色
    private static final int BAR_TRACK_OUTER = 0x40FFFFFF;
    private static final int BAR_GLOW_WHITE  = 0x30FFFFFF;
    private static final int BAR_GLOW_BLUE   = 0x6000DDFF;
    private static final int BAR_CORE        = 0xFF00DDFF;
    private static final int BAR_CORE_HI     = 0xFFFFFFFF;
    private static final int BAR_END_FRAME   = 0xFFB0B8C0;
    private static final int BAR_END_CORE    = 0xFF303840;

    // 准星
    private static final int   CH_WHITE     = 0xFFFFFFFF;
    private static final int   CH_BLUE      = 0xFF00DDFF;
    private static final int   CH_TICK_RGB  = 0xA0F0FF;
    private static final float CH_HEX_INNER = 2.0f;
    private static final float CH_HEX_MID   = 5.0f;
    private static final float CH_HEX_OUTER = 8.5f;
    private static final float CH_TICK_IN   = 14.0f;
    private static final float CH_TICK_OUT  = 20.0f;

    // 全屏滤镜
    private static final int BT_FILTER_BASE_ALPHA = 0x30;
    private static final int BT_FILTER_RGB        = 0x3060A0;
    private static final int PARRY_FLASH_BASE_ALPHA = 0x50;
    private static final int PARRY_FLASH_RGB        = 0xE0FFFF;

    // 冲刺特效
    private static final int DASH_VIGNETTE_LAYERS = 3;
    private static final int DASH_LINE_COUNT      = 18;

    // ================================================================
    //                      注册
    // ================================================================

    public static void register() {
        // frame_driver 挂在准星之后——但只是"相对准星的位置"，
        // 它的实际执行时机由 HudElementRegistry 的挂载顺序决定。
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
                ID_FRAME_DRIVER, GhostrunnerHud::driveFrame);

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                ID_STAMINA_BAR, GhostrunnerHud::renderStaminaBar);

        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,
                ID_CROSSHAIR, GhostrunnerHud::renderCrosshair);

        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                ID_DASH_EFFECT, GhostrunnerHud::renderDashEffect);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                ID_BULLET_TIME, GhostrunnerHud::renderBulletTimeFilter);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                ID_PARRY_FLASH, GhostrunnerHud::renderParryFlash);
    }

    // ================================================================
    //                      可见性
    // ================================================================

    /**
     * HUD 是否应该跳过渲染。三条件：
     * <ul>
     *   <li>本地玩家存在</li>
     *   <li>HUD 未隐藏（F1）</li>
     *   <li>玩家已被改造成幽灵行者</li>
     * </ul>
     */
    private static boolean isVisible(Minecraft client) {
        return client.player != null
                && !client.gui.hud.isHidden()
                && GhostrunnerPlayer.isGhostrunner(client.player);
    }

    // ================================================================
    //                      帧驱动
    // ================================================================

    private static void driveFrame(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        GhostrunnerClient.onFrame();
    }

    // ================================================================
    //                      耐力条
    // ================================================================

    private static void renderStaminaBar(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!isVisible(client)) return;

        float alpha = GhostrunnerClient.staminaBarAlpha;
        if (alpha <= 0.01f) return;

        float ratio = Math.max(0f, Math.min(1f,
                GhostrunnerClient.currentStamina / GhostrunnerClient.STAMINA_MAX));

        int centerX = ctx.guiWidth() / 2;
        int baseY = (int) (ctx.guiHeight() * BAR_Y_RATIO);

        drawStaminaBar(ctx, centerX, baseY, ratio, alpha);
    }

    private static void drawStaminaBar(GuiGraphicsExtractor ctx,
                                       int centerX, int baseY,
                                       float ratio, float alpha) {
        int segments = BAR_HALF_WIDTH * 2;
        float halfR = ratio * 0.5f;
        int startI = (int) ((0.5f - halfR) * segments);
        int endI   = (int) ((0.5f + halfR) * segments);

        // 1. 外框上下边缘
        int frameColor = applyAlpha(0xFFE8F0F8, alpha);
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            int px = centerX - BAR_HALF_WIDTH + (int) (2f * BAR_HALF_WIDTH * t);
            int py = baseY + (int) (Math.sin(t * Math.PI) * BAR_CURVE_DEPTH);
            ctx.fill(px, py - 6, px + 1, py - 5, frameColor);
            ctx.fill(px, py + 5, px + 1, py + 6, frameColor);
        }

        // 2. 底部虚线轨道
        int trackColor = applyAlpha(BAR_TRACK_OUTER, alpha);
        for (int i = 0; i <= segments; i += 4) {
            float t = (float) i / segments;
            int px = centerX - BAR_HALF_WIDTH + (int) (2f * BAR_HALF_WIDTH * t);
            int py = baseY + (int) (Math.sin(t * Math.PI) * BAR_CURVE_DEPTH);
            ctx.fill(px, py, px + 1, py + 1, trackColor);
        }

        // 3. 亮段（耐力填充部分）
        int glowWhite = applyAlpha(BAR_GLOW_WHITE, alpha);
        int glowBlue  = applyAlpha(BAR_GLOW_BLUE, alpha);
        int core      = applyAlpha(BAR_CORE, alpha);
        for (int i = startI; i <= endI; i++) {
            float t = (float) i / segments;
            int px = centerX - BAR_HALF_WIDTH + (int) (2f * BAR_HALF_WIDTH * t);
            int py = baseY + (int) (Math.sin(t * Math.PI) * BAR_CURVE_DEPTH);

            // 中央最亮、两端渐暗
            float centerDist = Math.abs(t - 0.5f) * 2f;
            float edgeFade = 1f - centerDist * centerDist;
            int coreHalf = edgeFade > 0.5f ? 2 : 1;

            ctx.fill(px, py - 5, px + 1, py + 6, glowWhite);
            ctx.fill(px, py - 4, px + 1, py + 5, glowBlue);
            ctx.fill(px, py - coreHalf, px + 1, py + coreHalf + 1, core);
        }

        // 4. 中央竖向高光
        int midY = baseY + BAR_CURVE_DEPTH;
        ctx.fill(centerX, midY - 7, centerX + 1, midY + 7, applyAlpha(BAR_CORE_HI, alpha));
        ctx.fill(centerX - 4, midY - 6, centerX + 5, midY + 6, applyAlpha(0x4000DDFF, alpha));

        // 5. 端点铆钉
        drawEndCap(ctx, centerX - BAR_HALF_WIDTH, baseY, alpha);
        drawEndCap(ctx, centerX + BAR_HALF_WIDTH, baseY, alpha);
    }

    private static void drawEndCap(GuiGraphicsExtractor ctx, int x, int y, float alpha) {
        ctx.fill(x - 4, y - 4, x + 4, y + 4, applyAlpha(0xFF000000, alpha));
        ctx.fill(x - 3, y - 3, x + 3, y + 3, applyAlpha(BAR_END_FRAME, alpha));
        ctx.fill(x - 2, y - 2, x + 2, y + 2, applyAlpha(BAR_END_CORE, alpha));
        ctx.fill(x - 1, y - 1, x + 1, y + 1, applyAlpha(BAR_CORE_HI, alpha));
    }

    // ================================================================
    //                      准星
    // ================================================================

    private static void renderCrosshair(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!isVisible(client)) return;

        // 只在第一人称显示
        if (!client.options.getCameraType().isFirstPerson()) return;

        drawCrosshair(ctx, ctx.guiWidth() / 2, ctx.guiHeight() / 2);
    }

    private static void drawCrosshair(GuiGraphicsExtractor ctx, int cx, int cy) {
        // 三层六边形（由外到内）
        drawHexagon(ctx, cx, cy, CH_HEX_OUTER, 0xA0A0F0FF);
        drawHexagon(ctx, cx, cy, CH_HEX_MID,   CH_BLUE);
        drawHexagon(ctx, cx, cy, CH_HEX_INNER, CH_WHITE);

        // 中心点
        ctx.fill(cx, cy, cx + 1, cy + 1, CH_WHITE);

        // 四个 45° 刻度
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + Math.PI / 4;
            drawTick(ctx, cx, cy,
                    (float) Math.cos(angle),
                    (float) Math.sin(angle));
        }
    }

    /**
     * 画六边形轮廓。
     * <p>采样密度用 {@code radius * 3}（原来 {@code radius * 2}），配合边缘采样让线条
     * 更平滑——GUI 坐标只有 int，但更密的采样能显著减少阶梯。
     */
    private static void drawHexagon(GuiGraphicsExtractor ctx, int cx, int cy,
                                    float radius, int color) {
        for (int edge = 0; edge < 6; edge++) {
            float a1 = (float) (edge * Math.PI / 3 - Math.PI / 2);
            float a2 = (float) ((edge + 1) * Math.PI / 3 - Math.PI / 2);

            float x1 = cx + radius * (float) Math.cos(a1);
            float y1 = cy + radius * (float) Math.sin(a1);
            float x2 = cx + radius * (float) Math.cos(a2);
            float y2 = cy + radius * (float) Math.sin(a2);

            int samples = Math.max(8, (int) (radius * 3));
            for (int s = 0; s <= samples; s++) {
                float t = (float) s / samples;
                float fx = x1 + (x2 - x1) * t;
                float fy = y1 + (y2 - y1) * t;

                // 主像素
                int px = Math.round(fx);
                int py = Math.round(fy);
                ctx.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    /**
     * 画一条 45° 刻度。从内圈到外圈有淡出效果。
     */
    private static void drawTick(GuiGraphicsExtractor ctx, int cx, int cy, float dx, float dy) {
        int steps = (int) (CH_TICK_OUT - CH_TICK_IN);
        for (int i = 0; i <= steps; i++) {
            float r = CH_TICK_IN + i;
            int px = Math.round(cx + dx * r);
            int py = Math.round(cy + dy * r);

            // 越靠外越暗
            float fade = 1f - ((float) i / steps) * 0.5f;
            int alpha = (int) (0x60 * fade);
            ctx.fill(px, py, px + 1, py + 1,
                    (alpha << 24) | CH_TICK_RGB);
        }
    }

    // ================================================================
    //                      冲刺特效
    // ================================================================

    private static void renderDashEffect(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!isVisible(client)) return;

        float remaining = GhostrunnerClient.dashEffectTimer;
        if (remaining <= 0f) return;

        float total = GhostrunnerClient.DASH_EFFECT_DURATION;
        float t = 1f - (remaining / total);
        float intensity = (float) Math.sin(t * Math.PI);   // 0 → 1 → 0 抛物线
        if (intensity < 0.01f) return;

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();

        drawDashVignette(ctx, w, h, intensity);
        drawSpeedLines(ctx, w, h, intensity, GhostrunnerClient.dashEffectSeed);
    }

    /** 屏幕边缘暗化。多层叠加营造"速度挤压"感。 */
    private static void drawDashVignette(GuiGraphicsExtractor ctx, int w, int h, float intensity) {
        int maxThickness = Math.min(w, h) / 6;

        for (int i = 0; i < DASH_VIGNETTE_LAYERS; i++) {
            int thickness = maxThickness * (DASH_VIGNETTE_LAYERS - i) / DASH_VIGNETTE_LAYERS;
            int alpha = (int) (intensity * 30 * (1f - (float) i / DASH_VIGNETTE_LAYERS));
            if (alpha <= 0) continue;

            int color = (alpha << 24) | 0x00102040;
            ctx.fill(0, 0, w, thickness, color);
            ctx.fill(0, h - thickness, w, h, color);
            ctx.fill(0, 0, thickness, h, color);
            ctx.fill(w - thickness, 0, w, h, color);
        }
    }

    /**
     * 速度线。
     * <p>从屏幕中心向外发射的短线段。
     * <p><b>确定性</b>：角度用黄金角均匀分布，随机量用 {@link #hash01} 从种子派生——
     * 不使用 {@code java.util.Random}，同一 seed 每帧图案一致，不闪烁，也不分配对象。
     */
    private static void drawSpeedLines(GuiGraphicsExtractor ctx, int w, int h,
                                       float intensity, long seed) {
        int cx = w / 2;
        int cy = h / 2;
        float diag = (float) Math.sqrt(w * w + h * h) * 0.5f;

        // seed 决定整体旋转相位
        float phase = (seed & 0xFF) / 255f * (float) (Math.PI * 2);

        for (int i = 0; i < DASH_LINE_COUNT; i++) {
            // 黄金角均匀分布
            double angle = i * 2.399963229728653 + phase;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);

            // 确定性随机
            float r1 = hash01(seed, i * 2);
            float r2 = hash01(seed, i * 2 + 1);

            float startR = diag * (0.70f + r1 * 0.20f);
            float endR   = diag * (1.00f + r2 * 0.20f);

            int alpha = (int) (intensity * (60 + (int) (r1 * 40)));
            if (alpha > 255) alpha = 255;
            int color = (alpha << 24) | 0x00C0E8FF;

            int steps = (int) (endR - startR) / 4;
            int lineLen = 4 + (int) (r2 * 4);
            for (int s = 0; s <= steps; s++) {
                float r = startR + s * 4;
                int px = (int) (cx + dx * r);
                int py = (int) (cy + dy * r);
                ctx.fill(px, py, px + lineLen, py + lineLen, color);
            }
        }
    }

    /** 确定性伪随机 [0, 1)。同一 (seed, salt) 永远返回同一个值。 */
    private static float hash01(long seed, int salt) {
        long x = seed ^ (salt * 0x9E3779B97F4A7C15L);
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        return (x >>> 11) / (float) (1L << 53);
    }

    // ================================================================
    //                      全屏滤镜
    // ================================================================

    private static void renderBulletTimeFilter(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!isVisible(client)) return;

        float a = GhostrunnerClient.bulletTimeFilterAlpha;
        if (a <= 0.01f) return;

        int alpha = (int) (BT_FILTER_BASE_ALPHA * a);
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(),
                (alpha << 24) | BT_FILTER_RGB);
    }

    private static void renderParryFlash(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!isVisible(client)) return;

        float a = GhostrunnerClient.parryFlashAlpha;
        if (a <= 0.01f) return;

        int alpha = (int) (a * PARRY_FLASH_BASE_ALPHA);
        ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(),
                (alpha << 24) | PARRY_FLASH_RGB);
    }

    // ================================================================
    //                      工具
    // ================================================================

    /** 按 alpha 缩放颜色的不透明度。alpha ≥ 1 时返回原色，避免无谓的位运算。 */
    private static int applyAlpha(int color, float alpha) {
        if (alpha >= 0.999f) return color;
        int a = (color >>> 24) & 0xFF;
        int newA = (int) (a * alpha);
        return (newA << 24) | (color & 0x00FFFFFF);
    }
}