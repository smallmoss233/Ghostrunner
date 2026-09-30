package ghostrunner.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    /** R 键快速复活（仅幽灵行者）。 */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$quickRespawnOnDeath(KeyEvent event,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;
        if (GhostrunnerKeys.RESPAWN == null) return;

        // KeyEvent.key() 返回的就是 SDL 扫描码，与注册时的 InputConstants.KEY_R 同类型
        InputConstants.Key pressed =
                InputConstants.Type.KEYBOARD.getOrCreate(event.key());
        if (!GhostrunnerKeys.RESPAWN.matches(pressed)) return;

        client.player.respawn();
        client.gui.setScreen(null);
        cir.setReturnValue(true);
    }
}