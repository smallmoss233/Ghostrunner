package ghostrunner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public class GhostrunnerClient implements ClientModInitializer {

    /** 上一 tick 跳跃键是否按下，用于检测上升沿 */
    private static boolean prevJumpPressed = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                prevJumpPressed = false;
                return;
            }

            boolean pressed = client.options.jumpKey.isPressed();

            // 只在"未按 → 按下"的瞬间发包
            if (pressed && !prevJumpPressed) {
                ClientPlayNetworking.send(Ghostrunner.JUMP_OFF_WALL_PACKET,
                        PacketByteBufs.empty());
            }

            prevJumpPressed = pressed;
        });
    }
}