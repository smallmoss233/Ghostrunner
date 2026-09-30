package ghostrunner.mixin.client;

import ghostrunner.api.GhostrunnerPlayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 隐藏原版 HUD 元素。
 * <p>两个注入点：
 * <ul>
 *   <li>{@code extractPlayerHealth}——血量 / 护甲 / 食物 / 气泡都在它内部调用，一次取消全解决</li>
 *   <li>{@code extractCrosshair}——准星独立于血量系统，单独取消</li>
 * </ul>
 */
@Mixin(Hud.class)
public abstract class InGameHudMixin {

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (isLocalGhostrunner()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideVanillaCrosshair(GuiGraphicsExtractor graphics,
                                                  DeltaTracker deltaTracker,
                                                  CallbackInfo ci) {
        if (isLocalGhostrunner()) ci.cancel();
    }

    @Unique
    private static boolean isLocalGhostrunner() {
        Player player = Minecraft.getInstance().player;
        return player != null && GhostrunnerPlayer.isGhostrunner(player);
    }
}