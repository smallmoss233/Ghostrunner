package ghostrunner.mixin.client;

import ghostrunner.GhostrunnerKeys;
import ghostrunner.api.GhostrunnerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    /** R 键快速复活（仅幽灵行者）。 */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$quickRespawnOnDeath(int keyCode, int scanCode, int modifiers,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof DeathScreen)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!GhostrunnerState.isGhostrunner(client.player)) return;

        if (GhostrunnerKeys.RESPAWN == null) return;
        if (!GhostrunnerKeys.RESPAWN.matchesKey(keyCode, scanCode)) return;

        client.player.requestRespawn();
        client.setScreen(null);
        cir.setReturnValue(true);
    }
}