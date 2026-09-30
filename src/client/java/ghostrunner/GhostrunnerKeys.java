package ghostrunner;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class GhostrunnerKeys {

    private GhostrunnerKeys() {}

    public static KeyMapping RESPAWN;

    public static void register() {
        // 定义按键分类
        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("ghostrunner", "general")
        );

        // 注册按键映射
        RESPAWN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.ghostrunner.respawn",
                InputConstants.Type.KEYBOARD,   // ★ KEYSYM → KEYBOARD
                InputConstants.KEY_R,           // = 21（SDL scancode），值本身没变
                CATEGORY
        ));
    }
}