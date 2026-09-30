package ghostrunner.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 死亡界面的 R 键快速复活。
 * <p>只对幽灵行者生效。触发后调用 {@code respawn()} 并关闭死亡界面。
 * <p>匹配键：{@link GhostrunnerKeys#RESPAWN}（默认 R，可在设置中修改）。
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$quickRespawnOnDeath(KeyEvent event,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerPlayer.isGhostrunner(client.player)) return;
        if (GhostrunnerKeys.RESPAWN == null) return;
        if (!matchesRespawnKey(event)) return;

        client.player.respawn();
        client.gui.setScreen(null);
        cir.setReturnValue(true);
    }

    /** 判断按键事件是否匹配复活键。 */
    private static boolean matchesRespawnKey(KeyEvent event) {
        InputConstants.Key pressed =
                InputConstants.Type.KEYBOARD.getOrCreate(event.key());
        return GhostrunnerKeys.RESPAWN.matches(pressed);
    }
}