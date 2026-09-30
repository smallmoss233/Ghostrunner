package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerState;
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

import java.util.Random;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    @Unique private static final String GLYPHS = "0123456789ABCDEF#@$%&*!?<>|/\\";
    @Unique private static final Random RANDOM = new Random();
    @Unique private static long animationStartTime = 0L;

    // ================================================================
    //                        生命周期
    // ================================================================

    @Inject(method = "init", at = @At("HEAD"))
    private void ghostrunner$resetAnimation(CallbackInfo ci) {
        animationStartTime = 0L;
    }

    // ================================================================
    //                  主渲染（背景 + 图形 + 文字）
    // ================================================================

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$customRender(GuiGraphicsExtractor ctx,
                                          int mouseX, int mouseY,
                                          float partialTick,
                                          CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;

        ci.cancel();

        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        long now = System.currentTimeMillis();
        if (animationStartTime == 0L) animationStartTime = now;
        long elapsed = now - animationStartTime;

        // ---- 动画进度 ----
        float pCover    = easeOutCubic((elapsed - 0)   / 150f);
        float pScanline = easeOutCubic((elapsed - 80)  / 200f);
        float pData     = easeOutCubic((elapsed - 180) / 250f);
        float pTopBar   = easeOutCubic((elapsed - 240) / 200f);
        float pBanner   = easeOutBack ((elapsed - 300) / 350f);
        float pTitle    = easeOutBack ((elapsed - 420) / 300f);
        float pDeco     = easeOutCubic((elapsed - 500) / 200f);
        float pKeyBar   = easeOutCubic((elapsed - 550) / 200f);
        float pKeyHint  = easeOutCubic((elapsed - 650) / 200f);

        float borderPulse = 0.7f + 0.3f * (float) Math.sin(elapsed / 120.0);

        // ============================================================
        // 1. 红色渐变遮罩
        // ============================================================
        if (pCover > 0.001f) {
            int alpha = (int) (0xA0 * pCover);
            ctx.fill(0, 0, w, h, (alpha << 24) | 0x00B00000);
            int topAlpha = (int) (0x30 * pCover);
            ctx.fill(0, 0, w, h / 3, (topAlpha << 24) | 0x00000000);
        }

        // ============================================================
        // 2. 扫描线
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
        // 3. 两侧滚动数据流
        // ============================================================
        if (pData > 0.001f) {
            RANDOM.setSeed(now / 80);
            int dataAlpha = (int) (0xFF * pData);

            for (int i = 0; i < 32; i++) {
                int x = 8 + RANDOM.nextInt(70);
                int y = RANDOM.nextInt(h);
                int a = Math.min(0xFF, (0x40 + RANDOM.nextInt(0x60)) * dataAlpha / 0xFF);
                int color = (a << 24) | 0x00FF8888;
                ctx.text(client.font, randomGlyph(), x, y, color, false);
            }
            for (int i = 0; i < 32; i++) {
                int x = w - 78 + RANDOM.nextInt(70);
                int y = RANDOM.nextInt(h);
                int a = Math.min(0xFF, (0x40 + RANDOM.nextInt(0x60)) * dataAlpha / 0xFF);
                int color = (a << 24) | 0x00FF8888;
                ctx.text(client.font, randomGlyph(), x, y, color, false);
            }
        }

        // ============================================================
        // 4. 顶部装饰条
        // ============================================================
        if (pTopBar > 0.001f) {
            int topBarH = 16;
            int halfW = (int) (w * 0.5f * pTopBar);
            int x0 = w / 2 - halfW;
            int x1 = w / 2 + halfW;

            int barAlpha = (int) (0xD0 * pTopBar);
            ctx.fill(x0, 0, x1, topBarH, (barAlpha << 24) | 0x00000000);
            ctx.fill(x0, topBarH, x1, topBarH + 1, 0xFFFF2244);

            int textAlpha = (int) (0xFF * pTopBar);
            int textColor = (textAlpha << 24) | 0x00FF6688;
            ctx.text(client.font,
                    Component.literal("GHOSTRUNNER // SYSTEM"),
                    8, 4, textColor, false);
            String timeStr = formatTime(now);
            ctx.text(client.font, timeStr,
                    w - 8 - client.font.width(timeStr), 4,
                    textColor, false);
        }

        // ============================================================
        // 5. 中央横幅
        // ============================================================
        int bannerH = 56;
        int bannerY = h / 2 - bannerH / 2;

        if (pBanner > 0.001f) {
            int halfW = (int) (w * 0.5f * Mth.clamp(pBanner, 0f, 1f));
            int x0 = w / 2 - halfW;
            int x1 = w / 2 + halfW;

            ctx.fill(x0, bannerY, x1, bannerY + bannerH, 0xE8000000);

            int borderAlpha = (int) (0xFF * borderPulse);
            int borderColor = (borderAlpha << 24) | 0x00FF2244;
            ctx.fill(x0, bannerY, x1, bannerY + 2, borderColor);
            ctx.fill(x0, bannerY + bannerH - 2, x1, bannerY + bannerH, borderColor);
            ctx.fill(x0, bannerY, x0 + 4, bannerY + bannerH, borderColor);
            ctx.fill(x1 - 4, bannerY, x1, bannerY + bannerH, borderColor);
        }

        // ============================================================
        // 6. 标题（缩放 + 闪烁）
        // ============================================================
        if (pTitle > 0.001f) {
            Component title = Component.translatable("gui.ghostrunner.death_title");
            float alpha = Mth.clamp(pTitle, 0f, 1f);

            long flashPhase = elapsed % 2500;
            if (flashPhase < 80) {
                boolean on = (flashPhase / 40) % 2 == 0;
                alpha *= on ? 0.15f : 1.0f;
            } else if (flashPhase < 180) {
                alpha *= 0.55f;
            }

            int alphaInt = (int) (0xFF * alpha);
            int titleColor = (alphaInt << 24) | 0x00FF3344;

            ctx.pose().pushMatrix();
            ctx.pose().translate(w / 2.0f, (float) (bannerY + bannerH / 2.0 - 16));
            ctx.pose().scale(4.0f, 4.0f);

            int titleW = client.font.width(title);
            ctx.text(client.font, title, -titleW / 2, 0, titleColor, true);

            ctx.pose().popMatrix();
        }

        // ============================================================
        // 7. 横幅下方装饰线
        // ============================================================
        if (pDeco > 0.001f) {
            int decoY = bannerY + bannerH + 8;
            int halfW = (int) ((w / 4) * pDeco);
            int decoAlpha = (int) (0x60 * pDeco);
            ctx.fill(w / 2 - halfW, decoY, w / 2 + halfW, decoY + 1,
                    (decoAlpha << 24) | 0x00FF2244);
        }

        // ============================================================
        // 8. 底部按键条
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

            int decoAlpha = (int) (0xFF * pKeyBar);
            for (int i = 0; i < 12; i++) {
                ctx.fill(20 + i * 5, keyBarY + 12, 20 + i * 5 + 3, keyBarY + 20,
                        (decoAlpha << 24) | 0x004488FF);
            }
            for (int i = 0; i < 12; i++) {
                int a = i < 6 ? decoAlpha : (decoAlpha * 0x40 / 0xFF);
                int color = (a << 24) | 0x00FF2244;
                ctx.fill(w - 20 - i * 5 - 3, keyBarY + 12, w - 20 - i * 5, keyBarY + 20, color);
            }
        }

        // ============================================================
        // 9. 按键提示
        // ============================================================
        if (pKeyHint > 0.001f) {
            Component keyName = GhostrunnerKeys.RESPAWN != null
                    ? GhostrunnerKeys.RESPAWN.getTranslatedKeyMessage()
                    : Component.literal("R");
            Component hint = Component.translatable("gui.ghostrunner.respawn_hint", keyName);

            int hintW = client.font.width(hint);
            int hintX = w / 2 - hintW / 2;
            int baseY = keyBarY + (keyBarH - 8) / 2;
            int hintY = baseY + (int) ((1f - pKeyHint) * 12);

            int alpha = (int) (0xFF * pKeyHint);

            int bgAlpha = (int) (0xC0 * pKeyHint);
            ctx.fill(hintX - 6, hintY - 2, hintX + hintW + 6, hintY + 10,
                    (bgAlpha << 24) | 0x00202020);
            ctx.text(client.font, hint, hintX, hintY,
                    (alpha << 24) | 0x00FFAA33, true);
        }
    }

    // ================================================================
    //         屏蔽原版 visitText（我们已经自己画了标题）
    // ================================================================

    @Inject(method = "visitText", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$cancelVanillaText(ActiveTextCollector output, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;
        ci.cancel();
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$skipVanillaButtons(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;
        ci.cancel();
    }

    // ================================================================
    //                          缓动函数
    // ================================================================

    @Unique
    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 3);
    }

    @Unique
    private static float easeOutBack(float t) {
        t = Mth.clamp(t, 0f, 1f);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    @Unique
    private static String randomGlyph() {
        return String.valueOf(GLYPHS.charAt(RANDOM.nextInt(GLYPHS.length())));
    }

    @Unique
    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000L;
        long h = (totalSeconds / 3600) % 24;
        long m = (totalSeconds / 60) % 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}