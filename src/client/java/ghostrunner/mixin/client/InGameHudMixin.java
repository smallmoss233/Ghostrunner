package ghostrunner.mixin.client;

import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    /** 幽灵行者不显示任何状态条（血/食/甲/氧）。 */
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideStatusBars(CallbackInfo ci) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && GhostrunnerState.isGhostrunner(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideVanillaCrosshair(DrawContext ctx, CallbackInfo ci) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && GhostrunnerState.isGhostrunner(player)) {
            ci.cancel();
        }
    }
}