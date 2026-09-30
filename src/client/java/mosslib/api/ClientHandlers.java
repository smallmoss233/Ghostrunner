package mosslib.api;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

import java.util.function.Consumer;

/**
 * 客户端 S2C 接收器注册器。
 * <p>简化模式：
 * <pre>{@code
 * ClientHandlers.handle(StaminaPayload.TYPE, payload -> {
 *     currentStamina = payload.stamina();
 * });
 * }</pre>
 */
public final class ClientHandlers {

    private ClientHandlers() {}

    /**
     * 注册处理器。
     * <p>自动切到客户端主线程（{@code context.client().execute}）。
     */
    public static <T extends CustomPacketPayload> void handle(Type<T> type, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.client().execute(() -> handler.accept(payload)));
    }
}