package ghostrunner.gui;

import ghostrunner.GhostrunnerClient;
import ghostrunner.api.GhostrunnerState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class GhostrunnerHud {

    private GhostrunnerHud() {}

    // ============ 尺寸 ============
    // 大幅缩小：半宽从 140 降到 90（总长 180，不再横跨大半个屏幕）
    private static final int HALF_WIDTH = 90;
    // 弧度减小，避免小条时显得弧度过于夸张
    private static final int CURVE_DEPTH = 6;
    private static final float Y_RATIO = 0.58f;

    // ============ 颜色 ============
    private static final int COLOR_TRACK_OUTER = 0x40FFFFFF;
    private static final int COLOR_GLOW_WHITE  = 0x30FFFFFF;
    private static final int COLOR_GLOW_BLUE   = 0x6000DDFF;
    private static final int COLOR_CORE        = 0xFF00DDFF;
    private static final int COLOR_CORE_HI     = 0xFFFFFFFF;
    private static final int COLOR_END_FRAME   = 0xFFB0B8C0;
    private static final int COLOR_END_CORE    = 0xFF303840;

    public static void register() {
        // ---- 耐力 ----
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            if (!GhostrunnerState.isGhostrunner(client.player)) return;

            float stamina = GhostrunnerClient.currentStamina;
            float max = GhostrunnerClient.STAMINA_MAX;

            if (stamina >= max - 0.5f) return;

            float ratio = Math.max(0, Math.min(1, stamina / max));

            int screenW = ctx.getScaledWindowWidth();
            int screenH = ctx.getScaledWindowHeight();
            int centerX = screenW / 2;
            int baseY = (int) (screenH * Y_RATIO);

            drawBar(ctx, centerX, baseY, ratio);
        });

        // ---- 准星 ----
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            if (!GhostrunnerState.isGhostrunner(client.player)) return;

            int screenW = ctx.getScaledWindowWidth();
            int screenH = ctx.getScaledWindowHeight();
            int cx = screenW / 2;
            int cy = screenH / 2;

            drawGhostrunnerCrosshair(ctx, cx, cy);
        });

        // ---- 冲刺视觉特效 ----
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            if (!GhostrunnerState.isGhostrunner(client.player)) return;

            int ticks = GhostrunnerClient.dashEffectTicks;
            if (ticks <= 0) return;

            float total = GhostrunnerClient.DASH_EFFECT_DURATION;
            float t = 1.0f - (ticks / total);
            float intensity = (float) Math.sin(t * Math.PI);
            if (intensity < 0.01f) return;

            int screenW = ctx.getScaledWindowWidth();
            int screenH = ctx.getScaledWindowHeight();

            drawDashVignette(ctx, screenW, screenH, intensity);
            drawSpeedLines(ctx, screenW, screenH, intensity, GhostrunnerClient.dashEffectSeed);
        });

        // ---- 子弹时间滤镜 ----
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            if (!GhostrunnerState.isGhostrunner(client.player)) return;
            if (!GhostrunnerClient.inBulletTime) return;

            int w = ctx.getScaledWindowWidth();
            int h = ctx.getScaledWindowHeight();

            // 蓝色半透明叠加层
            ctx.fill(0, 0, w, h, 0x303060A0);
        });
    }

    // ================================================================

    private static void drawBar(DrawContext ctx, int centerX, int baseY, float ratio) {
        int segments = HALF_WIDTH * 2;
        float halfR = ratio * 0.5f;
        int startI = (int) ((0.5f - halfR) * segments);
        int endI   = (int) ((0.5f + halfR) * segments);

        // 0. 完整外框（整体厚度降为 12px，上下各6）
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            int px = (int) (centerX - HALF_WIDTH + 2f * HALF_WIDTH * t);
            int py = (int) (baseY + Math.sin(t * Math.PI) * CURVE_DEPTH);

            ctx.fill(px, py - 6, px + 1, py - 5, 0xFFE8F0F8);   // 上边
            ctx.fill(px, py + 5, px + 1, py + 6, 0xFFE8F0F8);   // 下边
        }

        // 1. 底部轨道虚线
        for (int i = 0; i <= segments; i += 4) {
            float t = (float) i / segments;
            int px = (int) (centerX - HALF_WIDTH + 2f * HALF_WIDTH * t);
            int py = (int) (baseY + Math.sin(t * Math.PI) * CURVE_DEPTH);
            ctx.fill(px, py, px + 1, py + 1, COLOR_TRACK_OUTER);
        }

        // 2. 亮段（发光层和核心层同步变薄）
        for (int i = startI; i <= endI; i++) {
            float t = (float) i / segments;
            int px = (int) (centerX - HALF_WIDTH + 2f * HALF_WIDTH * t);
            int py = (int) (baseY + Math.sin(t * Math.PI) * CURVE_DEPTH);

            float centerDist = Math.abs(t - 0.5f) * 2f;
            float edgeFade = 1.0f - centerDist * centerDist;

            ctx.fill(px, py - 5, px + 1, py + 6, COLOR_GLOW_WHITE);
            ctx.fill(px, py - 4, px + 1, py + 5, COLOR_GLOW_BLUE);
            int coreHalf = edgeFade > 0.5f ? 2 : 1;
            ctx.fill(px, py - coreHalf, px + 1, py + coreHalf + 1, COLOR_CORE);
        }

        // 3. 中央竖向高光（缩短）
        int midY = baseY + CURVE_DEPTH;
        ctx.fill(centerX, midY - 7, centerX + 1, midY + 7, COLOR_CORE_HI);
        ctx.fill(centerX - 4, midY - 6, centerX + 5, midY + 6, 0x4000DDFF);

        // 4. 端点铆钉
        drawEndCap(ctx, centerX - HALF_WIDTH, baseY);
        drawEndCap(ctx, centerX + HALF_WIDTH, baseY);
    }

    /** 端点铆钉：尺寸缩小为 8x8 像素 */
    private static void drawEndCap(DrawContext ctx, int x, int y) {
        ctx.fill(x - 4, y - 4, x + 4, y + 4, 0xFF000000);
        ctx.fill(x - 3, y - 3, x + 3, y + 3, COLOR_END_FRAME);
        ctx.fill(x - 2, y - 2, x + 2, y + 2, COLOR_END_CORE);
        ctx.fill(x - 1, y - 1, x + 1, y + 1, COLOR_CORE_HI);
    }

    // ================================================================
    //                          准星
    // ================================================================

    private static final int CROSSHAIR_WHITE   = 0xFFFFFFFF;
    private static final int CROSSHAIR_BLUE    = 0xFF00DDFF;
    private static final int CROSSHAIR_TICK    = 0x60A0F0FF;

    /** 内层白六边形半径 */
    private static final float HEX_INNER_R = 2.0f;
    /** 中层蓝六边形半径 */
    private static final float HEX_MID_R   = 5.0f;
    /** 外层蓝六边形半径 */
    private static final float HEX_OUTER_R = 8.5f;

    /** 45° 刻线起止半径（往外推，留出明显空隙） */
    private static final float TICK_INNER_R = 14.0f; // 离六边形有 5.5px 的空隙
    private static final float TICK_OUTER_R = 20.0f; // 总长度 6px，更长更显眼

    private static void drawGhostrunnerCrosshair(DrawContext ctx, int cx, int cy) {
        // 外层蓝六边形（半透明）
        drawHexagon(ctx, cx, cy, HEX_OUTER_R, 1, 0xA0A0F0FF);

        // 中层蓝六边形
        drawHexagon(ctx, cx, cy, HEX_MID_R, 1, CROSSHAIR_BLUE);

        // 内层白六边形
        drawHexagon(ctx, cx, cy, HEX_INNER_R, 1, CROSSHAIR_WHITE);

        // 中心点
        ctx.fill(cx, cy, cx + 1, cy + 1, CROSSHAIR_WHITE);

        // 45° 四条刻线
        for (int dir = 0; dir < 4; dir++) {
            float angle = (float) (dir * Math.PI / 2 + Math.PI / 4);
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            drawTick(ctx, cx, cy, dx, dy);
        }
    }

    private static void drawHexagon(DrawContext ctx, int cx, int cy,
                                    float radius, int thickness, int color) {
        for (int i = 0; i < 6; i++) {
            float a1 = (float) (i * Math.PI / 3 - Math.PI / 2);
            float a2 = (float) ((i + 1) * Math.PI / 3 - Math.PI / 2);

            float x1 = cx + radius * (float) Math.cos(a1);
            float y1 = cy + radius * (float) Math.sin(a1);
            float x2 = cx + radius * (float) Math.cos(a2);
            float y2 = cy + radius * (float) Math.sin(a2);

            int samples = Math.max(4, (int) (radius * 2));
            for (int s = 0; s <= samples; s++) {
                float t = (float) s / samples;
                int px = Math.round(x1 + (x2 - x1) * t);
                int py = Math.round(y1 + (y2 - y1) * t);
                ctx.fill(px, py, px + thickness, py + thickness, color);
            }
        }
    }

    private static void drawTick(DrawContext ctx, int cx, int cy, float dx, float dy) {
        int steps = (int) (TICK_OUTER_R - TICK_INNER_R);
        for (int i = 0; i <= steps; i++) {
            float r = TICK_INNER_R + i;
            int px = Math.round(cx + dx * r);
            int py = Math.round(cy + dy * r);

            float fade = 1.0f - ((float) i / steps) * 0.5f;
            int alpha = (int) (0x60 * fade);
            int color = (alpha << 24) | (CROSSHAIR_TICK & 0x00FFFFFF);

            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    // ================================================================
    //                    冲刺视觉特效（优化版）
    // ================================================================

    private static void drawDashVignette(DrawContext ctx, int w, int h, float intensity) {
        int layers = 3;
        int maxThickness = Math.min(w, h) / 6;

        for (int i = 0; i < layers; i++) {
            int thickness = maxThickness * (layers - i) / layers;
            int alpha = (int) (intensity * 30 * (1.0f - (float) i / layers));
            if (alpha <= 0) continue;

            int color = (alpha << 24) | 0x00102040;

            ctx.fill(0, 0, w, thickness, color);
            ctx.fill(0, h - thickness, w, h, color);
            ctx.fill(0, 0, thickness, h, color);
            ctx.fill(w - thickness, 0, w, h, color);
        }
    }

    private static void drawSpeedLines(DrawContext ctx, int w, int h,
                                       float intensity, long seed) {
        java.util.Random rand = new java.util.Random(seed);
        int cx = w / 2;
        int cy = h / 2;

        float diag = (float) Math.sqrt(w * w + h * h) * 0.5f;
        int lineCount = 15;

        for (int i = 0; i < lineCount; i++) {
            float angle = (float) (rand.nextDouble() * Math.PI * 2);
            float startR = diag * (0.70f + rand.nextFloat() * 0.20f);
            float endR = diag * (1.00f + rand.nextFloat() * 0.20f);

            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);

            int alpha = (int) (intensity * (60 + rand.nextInt(40)));
            if (alpha > 255) alpha = 255;
            int color = (alpha << 24) | 0x00C0E8FF;

            int steps = (int) (endR - startR) / 4;
            for (int s = 0; s <= steps; s++) {
                float r = startR + s * 4;
                int px = (int) (cx + dx * r);
                int py = (int) (cy + dy * r);
                int lineLen = 4 + rand.nextInt(4);
                ctx.fill(px, py, px + lineLen, py + lineLen, color);
            }
        }
    }
}