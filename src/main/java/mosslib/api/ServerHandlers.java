package mosslib.api;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public final class ServerHandlers {

    private ServerHandlers() {}

    /** 无过滤直通。等价于 {@code of(type).handle(handler)}。 */
    public static <T extends CustomPacketPayload> void handle(
            Type<T> type, BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.server().execute(() -> handler.accept(payload, context.player())));
    }

    /** 需要过滤时用这个。 */
    public static <T extends CustomPacketPayload> Builder<T> of(Type<T> type) {
        return new Builder<>(type);
    }

    public static final class Builder<T extends CustomPacketPayload> {

        private final Type<T> type;
        private Predicate<ServerPlayer> filter = null;

        private Builder(Type<T> type) {
            this.type = type;
        }

        /** 添加过滤条件。可以多次调用，结果取交集。 */
        public Builder<T> filter(Predicate<ServerPlayer> predicate) {
            this.filter = (this.filter == null)
                    ? predicate
                    : this.filter.and(predicate);
            return this;
        }

        public void handle(BiConsumer<T, ServerPlayer> handler) {
            if (filter == null) {
                ServerHandlers.handle(type, handler);
                return;
            }
            ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                    context.server().execute(() -> {
                        ServerPlayer player = context.player();
                        if (!filter.test(player)) return;
                        handler.accept(payload, player);
                    }));
        }
    }
}