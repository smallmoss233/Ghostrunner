package ghostrunner.gui;

import ghostrunner.GhostrunnerClient;
import ghostrunner.api.GhostrunnerState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class GhostrunnerHud {

    private GhostrunnerHud() {}

    // ============ 尺寸 ============
    private static final int HALF_WIDTH = 100;      // 弧线半宽
    private static final int CURVE_DEPTH = 8;       // 向下凹的深度
    private static final float Y_RATIO = 0.58f;     // 屏幕竖直位置

    // ============ 颜色 ============
    private static final int COLOR_TRACK_OUTER = 0x40FFFFFF;  // 底虚线（轨道）
    private static final int COLOR_GLOW_WHITE  = 0x30FFFFFF;  // 最外层白晕
    private static final int COLOR_GLOW_BLUE   = 0x6000DDFF;  // 蓝光层
    private static final int COLOR_CORE        = 0xFF00DDFF;  // 亮蓝核心
    private static final int COLOR_CORE_HI     = 0xFFFFFFFF;  // 中央高光
    private static final int COLOR_END_BG      = 0xFF101820;  // 铆钉底色
    private static final int COLOR_END_FRAME   = 0xFFB0B8C0;  // 铆钉边框
    private static final int COLOR_END_CORE    = 0xFF303840;  // 铆钉内芯

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            if (!GhostrunnerState.isGhostrunner(client.player)) return;

            float stamina = GhostrunnerClient.currentStamina;
            float max = GhostrunnerClient.STAMINA_MAX;

            // 满耐力隐藏
            if (stamina >= max - 0.5f) return;

            float ratio = Math.max(0, Math.min(1, stamina / max));

            int screenW = ctx.getScaledWindowWidth();
            int screenH = ctx.getScaledWindowHeight();
            int centerX = screenW / 2;
            int baseY = (int) (screenH * Y_RATIO);

            drawBar(ctx, centerX, baseY, ratio);
        });
    }

    // ================================================================

    private static void drawBar(DrawContext ctx, int centerX, int baseY, float ratio) {
        int segments = HALF_WIDTH * 2;
        float halfR = ratio * 0.5f;
        int startI = (int) ((0.5f - halfR) * segments);
        int endI   = (int) ((0.5f + halfR) * segments);

        // ============ 0. 完整外框（始终显示，表示总耐力范围） ============
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            int px = (int) (centerX - HALF_WIDTH + 2f * HALF_WIDTH * t);
            int py = (int) (baseY + Math.sin(t * Math.PI) * CURVE_DEPTH);

            // 白色轮廓线，上下各一条（间距 13px）
            ctx.fill(px, py - 6, px + 1, py - 5, 0xFFE8F0F8);   // 上边
            ctx.fill(px, py + 5, px + 1, py + 6, 0xFFE8F0F8);   // 下边
        }

        // ============ 1. 底部轨道虚线（保留原视觉） ============
        for (int i = 0; i <= segments; i += 4) {
            float t = (float) i / segments;
            int px = (int) (centerX - HALF_WIDTH + 2f * HALF_WIDTH * t);
            int py = (int) (baseY + Math.sin(t * Math.PI) * CURVE_DEPTH);
            ctx.fill(px, py, px + 1, py + 1, COLOR_TRACK_OUTER);
        }

        // ============ 2. 亮段（带多层发光） ============
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

        // ============ 3. 中央竖向高光 ============
        int midY = baseY + CURVE_DEPTH;
        ctx.fill(centerX, midY - 7, centerX + 1, midY + 6, COLOR_CORE_HI);
        ctx.fill(centerX - 4, midY - 6, centerX + 5, midY + 5, 0x4000DDFF);

        // ============ 4. 端点铆钉 ============
        drawEndCap(ctx, centerX - HALF_WIDTH, baseY);
        drawEndCap(ctx, centerX + HALF_WIDTH, baseY);
    }

    /** 端点铆钉：多层方块，模仿截图里的方块端头。 */
    private static void drawEndCap(DrawContext ctx, int x, int y) {
        // 外框（深色，带轻微阴影）
        ctx.fill(x - 4, y - 4, x + 4, y + 4, 0xFF000000);
        // 边框（银灰）
        ctx.fill(x - 3, y - 3, x + 3, y + 3, COLOR_END_FRAME);
        // 内芯（深色凹槽）
        ctx.fill(x - 2, y - 2, x + 2, y + 2, COLOR_END_CORE);
        // 中央高光点
        ctx.fill(x - 1, y - 1, x + 1, y + 1, COLOR_CORE_HI);
    }
}