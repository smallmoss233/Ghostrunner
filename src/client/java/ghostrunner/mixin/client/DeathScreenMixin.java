package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    private static final String GLYPHS = "0123456789ABCDEF#@$%&*!?<>|/\\";
    private static final Random RANDOM = new Random();

    /**
     * 完全接管死亡界面渲染（仅幽灵行者）。
     * 非幽灵行者走原版逻辑。
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$renderCustomDeath(DrawContext ctx, int mouseX, int mouseY,
                                               float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;

        // 接管渲染
        ci.cancel();

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();

        // ============================================================
        // 1. 红色渐变遮罩（模拟原作故障滤镜）
        // ============================================================
        ctx.fill(0, 0, w, h, 0xA0B00000);       // 深红底
        ctx.fill(0, 0, w, h / 3, 0x30000000);    // 顶部再暗一点

        // ============================================================
        // 2. 扫描线
        // ============================================================
        for (int y = 0; y < h; y += 3) {
            ctx.fill(0, y, w, y + 1, 0x18000000);
        }

        // ============================================================
        // 3. 两侧滚动数据流（每 80ms 变化）
        // ============================================================
        RANDOM.setSeed(now / 80);
        // 左
        for (int i = 0; i < 32; i++) {
            int x = 8 + RANDOM.nextInt(70);
            int y = RANDOM.nextInt(h);
            int alpha = 0x40 + RANDOM.nextInt(0x60);
            int color = (alpha << 24) | 0x00FF8888;
            ctx.drawText(client.textRenderer, Text.literal(randomGlyph()), x, y, color, false);
        }
        // 右
        for (int i = 0; i < 32; i++) {
            int x = w - 78 + RANDOM.nextInt(70);
            int y = RANDOM.nextInt(h);
            int alpha = 0x40 + RANDOM.nextInt(0x60);
            int color = (alpha << 24) | 0x00FF8888;
            ctx.drawText(client.textRenderer, Text.literal(randomGlyph()), x, y, color, false);
        }

        // ============================================================
        // 4. 顶部装饰条
        // ============================================================
        int topBarH = 16;
        ctx.fill(0, 0, w, topBarH, 0xD0000000);
        ctx.fill(0, topBarH, w, topBarH + 1, 0xFFFF2244);
        ctx.drawText(client.textRenderer,
                Text.literal("GHOSTRUNNER // SYSTEM"),
                8, 4, 0xFFFF6688, false);
        ctx.drawText(client.textRenderer,
                Text.literal(formatTime(now)),
                w - 8 - client.textRenderer.getWidth(formatTime(now)), 4,
                0xFFFF6688, false);

        // ============================================================
        // 5. 中央横贯横幅 —— "致命错误"
        // ============================================================
        int bannerH = 56;
        int bannerY = h / 2 - bannerH / 2;

        // 深黑背景
        ctx.fill(0, bannerY, w, bannerY + bannerH, 0xE8000000);
        // 上下边框（亮红）
        ctx.fill(0, bannerY, w, bannerY + 2, 0xFFFF2244);
        ctx.fill(0, bannerY + bannerH - 2, w, bannerY + bannerH, 0xFFFF2244);
        // 左右装饰小竖条
        ctx.fill(0, bannerY, 4, bannerY + bannerH, 0xFFFF2244);
        ctx.fill(w - 4, bannerY, w, bannerY + bannerH, 0xFFFF2244);

        // 横幅中央文字（缩放 4 倍）
        Text title = Text.translatable("gui.ghostrunner.death_title");
        ctx.getMatrices().push();
        ctx.getMatrices().translate(w / 2.0, bannerY + bannerH / 2.0 - 16, 0);
        ctx.getMatrices().scale(4.0f, 4.0f, 1.0f);
        int titleW = client.textRenderer.getWidth(title);
        ctx.drawText(client.textRenderer, title, -titleW / 2, 0, 0xFFFF3344, true);
        ctx.getMatrices().pop();

        // ============================================================
        // 6. 横幅下方装饰线
        // ============================================================
        int decoY = bannerY + bannerH + 8;
        ctx.fill(w / 4, decoY, w * 3 / 4, decoY + 1, 0x60FF2244);

        // ============================================================
        // 7. 底部按键条 —— "R 返回"
        // ============================================================
        int keyBarH = 32;
        int keyBarY = h - keyBarH - 30;

        ctx.fill(0, keyBarY, w, keyBarY + keyBarH, 0xE8000000);
        ctx.fill(0, keyBarY, w, keyBarY + 1, 0xFFFF2244);
        ctx.fill(0, keyBarY + keyBarH - 1, w, keyBarY + keyBarH, 0xFFFF2244);

        // 左侧装饰：小进度条
        for (int i = 0; i < 12; i++) {
            ctx.fill(20 + i * 5, keyBarY + 12, 20 + i * 5 + 3, keyBarY + 20, 0xFF4488FF);
        }
        // 右侧装饰
        for (int i = 0; i < 12; i++) {
            int alpha = i < 6 ? 0xFF : 0x40;
            int color = (alpha << 24) | 0x00FF2244;
            ctx.fill(w - 20 - i * 5 - 3, keyBarY + 12, w - 20 - i * 5, keyBarY + 20, color);
        }

        // 中央按键提示
        Text keyName = GhostrunnerKeys.RESPAWN != null
                ? GhostrunnerKeys.RESPAWN.getBoundKeyLocalizedText()
                : Text.literal("R");
        Text hint = Text.translatable("gui.ghostrunner.respawn_hint", keyName);

        int hintW = client.textRenderer.getWidth(hint);
        int hintX = w / 2 - hintW / 2;
        int hintY = keyBarY + (keyBarH - 8) / 2;

        // 提示背后的深色块
        ctx.fill(hintX - 6, hintY - 2, hintX + hintW + 6, hintY + 10, 0xC0202020);
        ctx.drawText(client.textRenderer, hint, hintX, hintY, 0xFFFFAA33, true);
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