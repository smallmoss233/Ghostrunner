package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    private static final String GLYPHS = "0123456789ABCDEF#@$%&*!?<>|/\\";
    private static final Random RANDOM = new Random();

    /** 当前死亡界面的动画起点（毫秒）。0 = 未初始化。 */
    @Unique private static long animationStartTime = 0L;

    /** 每次打开死亡界面时重置动画。 */
    @Inject(method = "init", at = @At("HEAD"))
    private void ghostrunner$resetAnimation(CallbackInfo ci) {
        animationStartTime = 0L;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$renderCustomDeath(DrawContext ctx, int mouseX, int mouseY,
                                               float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;

        ci.cancel();

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();

        if (animationStartTime == 0L) animationStartTime = now;
        long elapsed = now - animationStartTime;

        // ============================================================
        // 动画进度（0~1）
        // ============================================================
        float pCover    = easeOutCubic((elapsed - 0)   / 150f);
        float pScanline = easeOutCubic((elapsed - 80)  / 200f);
        float pData     = easeOutCubic((elapsed - 180) / 250f);
        float pTopBar   = easeOutCubic((elapsed - 240) / 200f);
        float pBanner   = easeOutBack ((elapsed - 300) / 350f);
        float pTitle    = easeOutBack ((elapsed - 420) / 300f);
        float pDeco     = easeOutCubic((elapsed - 500) / 200f);
        float pKeyBar   = easeOutCubic((elapsed - 550) / 200f);
        float pKeyHint  = easeOutCubic((elapsed - 650) / 200f);

        // 呼吸脉冲
        float titlePulse  = 0.85f + 0.15f * (float) Math.sin(elapsed / 180.0);
        float borderPulse = 0.7f  + 0.3f  * (float) Math.sin(elapsed / 120.0);

        // ============================================================
        // 1. 红色渐变遮罩（淡入）
        // ============================================================
        if (pCover > 0.001f) {
            int alpha = (int) (0xA0 * pCover);
            ctx.fill(0, 0, w, h, (alpha << 24) | 0x00B00000);
            int topAlpha = (int) (0x30 * pCover);
            ctx.fill(0, 0, w, h / 3, (topAlpha << 24) | 0x00000000);
        }

        // ============================================================
        // 2. 扫描线（逐行从上往下出现）
        // ============================================================
        if (pScanline > 0.001f) {
            int lineCount = h / 3;
            int visible = (int) (lineCount * pScanline);
            for (int i = 0; i < visible; i++) {
                int y = i * 3;
                ctx.fill(0, y, w, y + 1, 0x18000000);
            }
        }

        // ============================================================
        // 3. 两侧滚动数据流（淡入）
        // ============================================================
        if (pData > 0.001f) {
            RANDOM.setSeed(now / 80);
            int dataAlpha = (int) (0xFF * pData);

            for (int i = 0; i < 32; i++) {
                int x = 8 + RANDOM.nextInt(70);
                int y = RANDOM.nextInt(h);
                int a = Math.min(0xFF, (0x40 + RANDOM.nextInt(0x60)) * dataAlpha / 0xFF);
                int color = (a << 24) | 0x00FF8888;
                ctx.drawText(client.textRenderer, Text.literal(randomGlyph()), x, y, color, false);
            }
            for (int i = 0; i < 32; i++) {
                int x = w - 78 + RANDOM.nextInt(70);
                int y = RANDOM.nextInt(h);
                int a = Math.min(0xFF, (0x40 + RANDOM.nextInt(0x60)) * dataAlpha / 0xFF);
                int color = (a << 24) | 0x00FF8888;
                ctx.drawText(client.textRenderer, Text.literal(randomGlyph()), x, y, color, false);
            }
        }

        // ============================================================
        // 4. 顶部装饰条（从中央向两侧展开）
        // ============================================================
        if (pTopBar > 0.001f) {
            int topBarH = 16;
            int halfW = (int) (w * 0.5f * pTopBar);
            int x0 = w / 2 - halfW;
            int x1 = w / 2 + halfW;

            int barAlpha = (int) (0xD0 * pTopBar);
            ctx.fill(x0, 0, x1, topBarH, (barAlpha << 24) | 0x00000000);
            ctx.fill(x0, topBarH, x1, topBarH + 1, 0xFFFF2244);

            // 顶部文字（淡入）
            int textAlpha = (int) (0xFF * pTopBar);
            int textColor = (textAlpha << 24) | 0x00FF6688;
            ctx.drawText(client.textRenderer,
                    Text.literal("GHOSTRUNNER // SYSTEM"),
                    8, 4, textColor, false);
            String timeStr = formatTime(now);
            ctx.drawText(client.textRenderer,
                    Text.literal(timeStr),
                    w - 8 - client.textRenderer.getWidth(timeStr), 4,
                    textColor, false);
        }

        // ============================================================
        // 5. 中央横贯横幅 —— "致命错误"（从中央弹开）
        // ============================================================
        int bannerH = 56;
        int bannerY = h / 2 - bannerH / 2;

        if (pBanner > 0.001f) {
            int halfW = (int) (w * 0.5f * MathHelper.clamp(pBanner, 0f, 1f));
            int x0 = w / 2 - halfW;
            int x1 = w / 2 + halfW;

            ctx.fill(x0, bannerY, x1, bannerY + bannerH, 0xE8000000);

            // 边框脉冲
            int borderAlpha = (int) (0xFF * borderPulse);
            int borderColor = (borderAlpha << 24) | 0x00FF2244;
            ctx.fill(x0, bannerY, x1, bannerY + 2, borderColor);
            ctx.fill(x0, bannerY + bannerH - 2, x1, bannerY + bannerH, borderColor);
            ctx.fill(x0, bannerY, x0 + 4, bannerY + bannerH, borderColor);
            ctx.fill(x1 - 4, bannerY, x1, bannerY + bannerH, borderColor);
        }

        // ============================================================
        // 6. 标题（固定大小，偶尔闪烁）
        // ============================================================
        if (pTitle > 0.001f) {
            Text title = Text.translatable("gui.ghostrunner.death_title");

            // 基础透明度（淡入）
            float alpha = MathHelper.clamp(pTitle, 0f, 1f);

            // ★ 闪烁效果：用一段时间内的"窗口脉冲"
            // 每 ~2.5 秒触发一次，持续 ~80ms，闪 2~3 下
            long flashPhase = elapsed % 2500;
            if (flashPhase < 80) {
                // 高频闪烁：40ms 一次
                boolean on = (flashPhase / 40) % 2 == 0;
                alpha *= on ? 0.15f : 1.0f;
            } else if (flashPhase < 180) {
                // 后面还跟着一个稍微淡的抖动
                alpha *= 0.55f;
            }

            float scale = 4.0f;
            int alphaInt = (int) (0xFF * alpha);

            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2.0, bannerY + bannerH / 2.0 - 16, 0);
            ctx.getMatrices().scale(scale, scale, 1.0f);

            int titleW = client.textRenderer.getWidth(title);

            // 阴影
            int shadowAlpha = alphaInt / 2;
            int shadowColor = (shadowAlpha << 24) | 0x00330000;
            ctx.drawText(client.textRenderer, title, -titleW / 2 + 1, 1, shadowColor, false);

            // 主文字
            int mainColor = (alphaInt << 24) | 0x00FF3344;
            ctx.drawText(client.textRenderer, title, -titleW / 2, 0, mainColor, true);

            ctx.getMatrices().pop();
        }

        // ============================================================
        // 7. 横幅下方装饰线（从中心向两侧展开）
        // ============================================================
        if (pDeco > 0.001f) {
            int decoY = bannerY + bannerH + 8;
            int halfW = (int) ((w / 4) * pDeco);
            int decoAlpha = (int) (0x60 * pDeco);
            ctx.fill(w / 2 - halfW, decoY, w / 2 + halfW, decoY + 1,
                    (decoAlpha << 24) | 0x00FF2244);
        }

        // ============================================================
        // 8. 底部按键条（从中央向两侧展开）
        // ============================================================
        int keyBarH = 32;
        int keyBarY = h - keyBarH - 30;

        if (pKeyBar > 0.001f) {
            int halfW = (int) (w * 0.5f * pKeyBar);
            int x0 = w / 2 - halfW;
            int x1 = w / 2 + halfW;

            int barAlpha = (int) (0xE8 * pKeyBar);
            ctx.fill(x0, keyBarY, x1, keyBarY + keyBarH, (barAlpha << 24) | 0x00000000);
            ctx.fill(x0, keyBarY, x1, keyBarY + 1, 0xFFFF2244);
            ctx.fill(x0, keyBarY + keyBarH - 1, x1, keyBarY + keyBarH, 0xFFFF2244);

            // 左侧装饰进度条（淡入）
            int decoAlpha = (int) (0xFF * pKeyBar);
            for (int i = 0; i < 12; i++) {
                ctx.fill(20 + i * 5, keyBarY + 12, 20 + i * 5 + 3, keyBarY + 20,
                        (decoAlpha << 24) | 0x004488FF);
            }
            // 右侧装饰
            for (int i = 0; i < 12; i++) {
                int a = i < 6 ? decoAlpha : (decoAlpha * 0x40 / 0xFF);
                int color = (a << 24) | 0x00FF2244;
                ctx.fill(w - 20 - i * 5 - 3, keyBarY + 12, w - 20 - i * 5, keyBarY + 20, color);
            }
        }

        // ============================================================
        // 9. 按键提示（淡入 + 上滑）
        // ============================================================
        if (pKeyHint > 0.001f) {
            Text keyName = GhostrunnerKeys.RESPAWN != null
                    ? GhostrunnerKeys.RESPAWN.getBoundKeyLocalizedText()
                    : Text.literal("R");
            Text hint = Text.translatable("gui.ghostrunner.respawn_hint", keyName);

            int hintW = client.textRenderer.getWidth(hint);
            int hintX = w / 2 - hintW / 2;
            // 从下往上滑入
            int baseY = keyBarY + (keyBarH - 8) / 2;
            int hintY = baseY + (int) ((1f - pKeyHint) * 12);

            int alpha = (int) (0xFF * pKeyHint);

            // 背景块
            int bgAlpha = (int) (0xC0 * pKeyHint);
            ctx.fill(hintX - 6, hintY - 2, hintX + hintW + 6, hintY + 10,
                    (bgAlpha << 24) | 0x00202020);
            // 文字
            ctx.drawText(client.textRenderer, hint, hintX, hintY,
                    (alpha << 24) | 0x00FFAA33, true);
        }
    }

    // ================================================================
    //                          缓动函数
    // ================================================================

    /** 出场：先快后慢 */
    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 3);
    }

    /** 回弹：略微超出再收回来 */
    private static float easeOutBack(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // ================================================================

    private static String randomGlyph() {
        return String.valueOf(GLYPHS.charAt(RANDOM.nextInt(GLYPHS.length())));
    }

    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000L;
        long h = (totalSeconds / 3600) % 24;
        long m = (totalSeconds / 60) % 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}