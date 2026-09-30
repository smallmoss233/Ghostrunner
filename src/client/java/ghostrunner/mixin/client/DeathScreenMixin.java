package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    // ================================================================
    //                      常量
    // ================================================================

    @Unique private static final String GLYPHS = "0123456789ABCDEF#@$%&*!?<>|/\\";

    // 布局
    @Unique private static final int TOP_BAR_H       = 16;
    @Unique private static final int BANNER_H        = 56;
    @Unique private static final int KEY_BAR_H       = 32;
    @Unique private static final int KEY_BAR_BOTTOM  = 30;
    @Unique private static final int DATA_COL_W      = 70;
    @Unique private static final int DATA_GLYPH_CNT  = 32;

    // 配色
    @Unique private static final int C_BG_RED        = 0x00B00000;
    @Unique private static final int C_BG_BLACK      = 0x00000000;
    @Unique private static final int C_SCANLINE      = 0x18000000;
    @Unique private static final int C_DATA_RED      = 0x00FF8888;
    @Unique private static final int C_RED           = 0x00FF2244;
    @Unique private static final int C_RED_BRIGHT    = 0x00FF3344;
    @Unique private static final int C_RED_GLOW      = 0x00FF6688;
    @Unique private static final int C_BLUE_DECO     = 0x004488FF;
    @Unique private static final int C_KEY_HINT      = 0x00FFAA33;
    @Unique private static final int C_KEY_HINT_BG   = 0x00202020;

    // 数据流刷新周期（毫秒）
    @Unique private static final long DATA_REFRESH_MS = 80L;

    // ================================================================
    //                      状态
    // ================================================================

    @Unique private static long animationStartMs = 0L;

    // ================================================================
    //                      生命周期
    // ================================================================

    @Inject(method = "init", at = @At("HEAD"))
    private void ghostrunner$resetAnimation(CallbackInfo ci) {
        animationStartMs = 0L;
    }

    /** 跳过原版按钮创建（重生 / 标题屏幕）。 */
    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$skipVanillaButtons(CallbackInfo ci) {
        if (!isGhostrunner()) return;
        ci.cancel();
    }

    /** 屏蔽原版死亡文本（我们自己在 extractBackground 里画）。 */
    @Inject(method = "visitText", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$cancelVanillaText(ActiveTextCollector output, CallbackInfo ci) {
        if (!isGhostrunner()) return;
        ci.cancel();
    }

    // ================================================================
    //                      主渲染
    // ================================================================

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$customRender(GuiGraphicsExtractor ctx,
                                          int mouseX, int mouseY,
                                          float partialTick,
                                          CallbackInfo ci) {
        if (!isGhostrunner()) return;
        ci.cancel();

        Minecraft client = Minecraft.getInstance();
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        long now = System.currentTimeMillis();
        if (animationStartMs == 0L) animationStartMs = now;
        long t = now - animationStartMs;

        // ---- 动画进度 ----
        float pCover    = progressCubic(t, 0,   150);
        float pScanline = progressCubic(t, 80,  200);
        float pData     = progressCubic(t, 180, 250);
        float pTopBar   = progressCubic(t, 240, 200);
        float pBanner   = progressBack (t, 300, 350);
        float pTitle    = progressBack (t, 420, 300);
        float pDeco     = progressCubic(t, 500, 200);
        float pKeyBar   = progressCubic(t, 550, 200);
        float pKeyHint  = progressCubic(t, 650, 200);

        drawCover(ctx, w, h, pCover);
        drawScanlines(ctx, w, h, pScanline);
        drawDataStreams(ctx, client, w, h, now, pData);
        drawTopBar(ctx, client, w, now, pTopBar);
        drawBanner(ctx, w, h, pBanner, t);
        drawTitle(ctx, client, w, h, pTitle, t);
        drawDecoLine(ctx, w, h, pDeco);
        drawKeyBar(ctx, w, h, pKeyBar);
        drawKeyHint(ctx, client, w, h, pKeyHint);
    }

    // ================================================================
    //                      各绘制单元
    // ================================================================

    /** 全屏红色遮罩 + 顶部深色渐变。 */
    @Unique
    private static void drawCover(GuiGraphicsExtractor ctx, int w, int h, float p) {
        if (p <= 0.001f) return;
        int redA = (int) (0xA0 * p);
        ctx.fill(0, 0, w, h, (redA << 24) | C_BG_RED);
        int topA = (int) (0x30 * p);
        ctx.fill(0, 0, w, h / 3, (topA << 24) | C_BG_BLACK);
    }

    /** CRT 扫描线。 */
    @Unique
    private static void drawScanlines(GuiGraphicsExtractor ctx, int w, int h, float p) {
        if (p <= 0.001f) return;
        int visible = (int) ((h / 3) * p);
        for (int i = 0; i < visible; i++) {
            int y = i * 3;
            ctx.fill(0, y, w, y + 1, C_SCANLINE);
        }
    }

    /** 两侧数据流。种子每 {@code DATA_REFRESH_MS} 变一次，整体图案刷新。 */
    @Unique
    private static void drawDataStreams(GuiGraphicsExtractor ctx, Minecraft client,
                                        int w, int h, long now, float p) {
        if (p <= 0.001f) return;

        long seed = now / DATA_REFRESH_MS;
        int dataAlpha = (int) (0xFF * p);

        drawDataColumn(ctx, client, seed,             8,                          DATA_COL_W, h, dataAlpha);
        drawDataColumn(ctx, client, seed ^ 0xCAFE5EEDL, w - DATA_COL_W - 8,       DATA_COL_W, h, dataAlpha);
    }

    /** 单列数据流。位置和透明度由种子确定性派生。 */
    @Unique
    private static void drawDataColumn(GuiGraphicsExtractor ctx, Minecraft client,
                                       long seed, int xBase, int width, int h, int dataAlpha) {
        int glyphCount = GLYPHS.length();
        for (int i = 0; i < DATA_GLYPH_CNT; i++) {
            float r1 = hash01(seed, i * 3);
            float r2 = hash01(seed, i * 3 + 1);
            float r3 = hash01(seed, i * 3 + 2);

            int x = xBase + (int) (r1 * width);
            int y = (int) (r2 * h);
            int baseA = 0x40 + (int) (r3 * 0x60);
            int a = Math.min(0xFF, baseA * dataAlpha / 0xFF);

            char glyph = GLYPHS.charAt((int) (r1 * glyphCount) % glyphCount);
            ctx.text(client.font, String.valueOf(glyph), x, y, (a << 24) | C_DATA_RED, false);
        }
    }

    /** 顶部状态栏（左：系统名，右：时钟）。 */
    @Unique
    private static void drawTopBar(GuiGraphicsExtractor ctx, Minecraft client,
                                   int w, long now, float p) {
        if (p <= 0.001f) return;

        int halfW = (int) (w * 0.5f * p);
        int x0 = w / 2 - halfW;
        int x1 = w / 2 + halfW;

        int barA = (int) (0xD0 * p);
        ctx.fill(x0, 0, x1, TOP_BAR_H, (barA << 24) | C_BG_BLACK);
        ctx.fill(x0, TOP_BAR_H, x1, TOP_BAR_H + 1, 0xFFFF2244);

        int textColor = ((int) (0xFF * p) << 24) | C_RED_GLOW;
        ctx.text(client.font, "GHOSTRUNNER // SYSTEM", 8, 4, textColor, false);

        String timeStr = formatTime(now);
        ctx.text(client.font, timeStr,
                w - 8 - client.font.width(timeStr), 4, textColor, false);
    }

    /** 中央横幅框。 */
    @Unique
    private static void drawBanner(GuiGraphicsExtractor ctx, int w, int h,
                                   float p, long elapsed) {
        if (p <= 0.001f) return;

        int bannerY = h / 2 - BANNER_H / 2;
        int halfW = (int) (w * 0.5f * Mth.clamp(p, 0f, 1f));
        int x0 = w / 2 - halfW;
        int x1 = w / 2 + halfW;

        ctx.fill(x0, bannerY, x1, bannerY + BANNER_H, 0xE8000000);

        float pulse = 0.7f + 0.3f * (float) Math.sin(elapsed / 120.0);
        int borderColor = ((int) (0xFF * pulse) << 24) | C_RED;
        ctx.fill(x0, bannerY, x1, bannerY + 2, borderColor);
        ctx.fill(x0, bannerY + BANNER_H - 2, x1, bannerY + BANNER_H, borderColor);
        ctx.fill(x0, bannerY, x0 + 4, bannerY + BANNER_H, borderColor);
        ctx.fill(x1 - 4, bannerY, x1, bannerY + BANNER_H, borderColor);
    }

    /** 标题文本，含 4 倍缩放 + 周期性闪烁。 */
    @Unique
    private static void drawTitle(GuiGraphicsExtractor ctx, Minecraft client,
                                  int w, int h, float p, long elapsed) {
        if (p <= 0.001f) return;

        Component title = Component.translatable("gui.ghostrunner.death_title");
        float alpha = Mth.clamp(p, 0f, 1f);

        // 闪烁：每 2.5 秒一组（80ms 快闪 + 100ms 半亮）
        long flashPhase = elapsed % 2500;
        if (flashPhase < 80) {
            alpha *= ((flashPhase / 40) % 2 == 0) ? 0.15f : 1.0f;
        } else if (flashPhase < 180) {
            alpha *= 0.55f;
        }

        int titleColor = ((int) (0xFF * alpha) << 24) | C_RED_BRIGHT;
        int bannerY = h / 2 - BANNER_H / 2;

        ctx.pose().pushMatrix();
        ctx.pose().translate(w / 2.0f, (float) (bannerY + BANNER_H / 2.0 - 16));
        ctx.pose().scale(4.0f, 4.0f);

        int titleW = client.font.width(title);
        ctx.text(client.font, title, -titleW / 2, 0, titleColor, true);

        ctx.pose().popMatrix();
    }

    /** 横幅下方的装饰线。 */
    @Unique
    private static void drawDecoLine(GuiGraphicsExtractor ctx, int w, int h, float p) {
        if (p <= 0.001f) return;
        int bannerY = h / 2 - BANNER_H / 2;
        int decoY = bannerY + BANNER_H + 8;
        int halfW = (int) ((w / 4) * p);
        int decoA = (int) (0x60 * p);
        ctx.fill(w / 2 - halfW, decoY, w / 2 + halfW, decoY + 1,
                (decoA << 24) | C_RED);
    }

    /** 底部按键条（黑底红边 + 左右两侧小装饰块）。 */
    @Unique
    private static void drawKeyBar(GuiGraphicsExtractor ctx, int w, int h, float p) {
        if (p <= 0.001f) return;

        int keyBarY = h - KEY_BAR_H - KEY_BAR_BOTTOM;
        int halfW = (int) (w * 0.5f * p);
        int x0 = w / 2 - halfW;
        int x1 = w / 2 + halfW;

        int barA = (int) (0xE8 * p);
        ctx.fill(x0, keyBarY, x1, keyBarY + KEY_BAR_H, (barA << 24) | C_BG_BLACK);
        ctx.fill(x0, keyBarY, x1, keyBarY + 1, 0xFFFF2244);
        ctx.fill(x0, keyBarY + KEY_BAR_H - 1, x1, keyBarY + KEY_BAR_H, 0xFFFF2244);

        int decoA = (int) (0xFF * p);
        // 左侧蓝块
        for (int i = 0; i < 12; i++) {
            ctx.fill(20 + i * 5, keyBarY + 12, 20 + i * 5 + 3, keyBarY + 20,
                    (decoA << 24) | C_BLUE_DECO);
        }
        // 右侧红块（后半段半透明）
        for (int i = 0; i < 12; i++) {
            int a = i < 6 ? decoA : (decoA * 0x40 / 0xFF);
            int color = (a << 24) | C_RED;
            ctx.fill(w - 20 - i * 5 - 3, keyBarY + 12, w - 20 - i * 5, keyBarY + 20, color);
        }
    }

    /** 按键提示（"按 R 复活"）。 */
    @Unique
    private static void drawKeyHint(GuiGraphicsExtractor ctx, Minecraft client,
                                    int w, int h, float p) {
        if (p <= 0.001f) return;

        Component keyName = GhostrunnerKeys.RESPAWN != null
                ? GhostrunnerKeys.RESPAWN.getTranslatedKeyMessage()
                : Component.literal("R");
        Component hint = Component.translatable("gui.ghostrunner.respawn_hint", keyName);

        int hintW = client.font.width(hint);
        int hintX = w / 2 - hintW / 2;
        int keyBarY = h - KEY_BAR_H - KEY_BAR_BOTTOM;
        int baseY = keyBarY + (KEY_BAR_H - 8) / 2;
        int hintY = baseY + (int) ((1f - p) * 12);

        int alpha = (int) (0xFF * p);
        int bgA = (int) (0xC0 * p);
        ctx.fill(hintX - 6, hintY - 2, hintX + hintW + 6, hintY + 10,
                (bgA << 24) | C_KEY_HINT_BG);
        ctx.text(client.font, hint, hintX, hintY, (alpha << 24) | C_KEY_HINT, true);
    }

    // ================================================================
    //                      工具
    // ================================================================

    @Unique
    private static boolean isGhostrunner() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && GhostrunnerPlayer.isGhostrunner(client.player);
    }

    /** 缓出三次曲线进度。{@code elapsed < delayMs} 时返回 0。 */
    @Unique
    private static float progressCubic(long elapsed, long delayMs, long durationMs) {
        if (elapsed < delayMs) return 0f;
        float t = Mth.clamp((float) (elapsed - delayMs) / durationMs, 0f, 1f);
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** 缓出回弹曲线进度。用于横幅和标题的"弹出"效果。 */
    @Unique
    private static float progressBack(long elapsed, long delayMs, long durationMs) {
        if (elapsed < delayMs) return 0f;
        float t = Mth.clamp((float) (elapsed - delayMs) / durationMs, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** 确定性伪随机 [0, 1)。同一 (seed, salt) 永远返回同一个值。 */
    @Unique
    private static float hash01(long seed, int salt) {
        long x = seed ^ (salt * 0x9E3779B97F4A7C15L);
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        return (x >>> 11) / (float) (1L << 53);
    }

    /** 把时间戳格式化为 HH:MM:SS。 */
    @Unique
    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000L;
        long h = (totalSeconds / 3600) % 24;
        long m = (totalSeconds / 60) % 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}