package ghostrunner;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class GhostrunnerKeys {

    private GhostrunnerKeys() {}

    public static KeyBinding RESPAWN;

    public static void register() {
        RESPAWN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostrunner.respawn",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.ghostrunner"
        ));
    }
}