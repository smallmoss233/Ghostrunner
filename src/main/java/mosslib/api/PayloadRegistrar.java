package mosslib.api;

import ghostrunner.Ghostrunner;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络包注册器。
 * <p>声明即注册：{@code PayloadRegistrar} 的实例在类字段初始化时收集所有注册任务，
 * 由 {@link #commit()} 统一执行。
 * <p>用法：
 * <pre>{@code
 * private static final PayloadRegistrar REGISTRAR = new PayloadRegistrar();
 *
 * public static final Type<ActionPayload> ACTION = REGISTRAR.c2s("action", ActionPayload.CODEC);
 * public static final Type<NoticePayload> NOTICE = REGISTRAR.s2c("notice", NoticePayload.CODEC);
 *
 * public static void registerPayloads() {
 *     REGISTRAR.commit();
 * }
 * }</pre>
 * <p>注意：{@code registerPayloads()} 必须在 {@code onInitialize} 早期调用，
 * 因为 Fabric 要求 payload 类型在游戏启动阶段注册。
 */
public final class PayloadRegistrar {

    private final List<Runnable> tasks = new ArrayList<>();

    /** 声明一个 C2S payload 类型。 */
    public <T extends CustomPacketPayload> Type<T> c2s(
            String path, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        Type<T> type = new Type<>(Ghostrunner.id(path));
        tasks.add(() -> PayloadTypeRegistry.serverboundPlay().register(type, codec));
        return type;
    }

    /** 声明一个 S2C payload 类型。 */
    public <T extends CustomPacketPayload> Type<T> s2c(
            String path, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        Type<T> type = new Type<>(Ghostrunner.id(path));
        tasks.add(() -> PayloadTypeRegistry.clientboundPlay().register(type, codec));
        return type;
    }

    /** 执行所有注册。幂等——重复调用只会注册一次。 */
    public void commit() {
        for (Runnable task : tasks) task.run();
        tasks.clear();
    }
}