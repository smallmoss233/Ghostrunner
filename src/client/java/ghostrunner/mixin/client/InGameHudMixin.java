package ghostrunner.mixin.client;

import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {

    /** 隐藏血量（护甲和食物也一并隐藏，因为它们从本方法内部调用） */
    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && GhostrunnerState.isGhostrunner(player)) {
            ci.cancel();
        }
    }

    /** 隐藏护甲（static 方法，注入器必须也是 static） */
    @Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
    private static void ghostrunner$hideArmor(GuiGraphicsExtractor graphics, Player player,
                                              int yLineBase, int numHealthRows,
                                              int healthRowHeight, int xLeft,
                                              CallbackInfo ci) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && GhostrunnerState.isGhostrunner(localPlayer)) {
            ci.cancel();
        }
    }

    /** 隐藏食物 */
    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideFood(GuiGraphicsExtractor graphics, Player player,
                                      int yLineBase, int xRight, CallbackInfo ci) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && GhostrunnerState.isGhostrunner(localPlayer)) {
            ci.cancel();
        }
    }

    /** 隐藏原版准星 */
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$hideVanillaCrosshair(GuiGraphicsExtractor graphics,
                                                  DeltaTracker deltaTracker,
                                                  CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && GhostrunnerState.isGhostrunner(player)) {
            ci.cancel();
        }
    }
}