package ghostrunner.handler;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 所有服务端全局事件的注册。
 * <p>由 {@link ghostrunner.Ghostrunner#onInitialize} 通过 {@link #register()} 调用。
 */
public final class GhostrunnerServerEvents {

    private GhostrunnerServerEvents() {}

    public static void register() {

        // ---------- 每 tick：冷却递减 ----------
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DashHandler.tickCooldowns();
            ClimbHandler.tickCooldowns();
            ParryHandler.tickCooldowns();
        });

        // ---------- 玩家加载 → 同步改造标记 ----------
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
            ServerPlayNetworking.send(player,
                    new GhostrunnerNetworking.AscendedStatePayload(gr.ghostrunner$isAscended()));
        });

        // ---------- 玩家重生 / 维度切换 → 复制持久状态 ----------
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            GhostrunnerPlayer.of(newPlayer).ghostrunner$data()
                    .copyFrom(GhostrunnerPlayer.of(oldPlayer).ghostrunner$data());

            BlockHandler.forceCancel(newPlayer);
        });

        // ---------- 玩家断开 → 清理子弹时间 ----------
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BulletTimeManager.exit(handler.getPlayer()));

        // ---------- 玩家从世界卸载 → 清理 ----------
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayer sp) {
                BulletTimeManager.exit(sp);
                BlockHandler.forceCancel(sp);
            }
        });

        // ---------- 服务器启动 → 复位 tick rate ----------
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                server.tickRateManager().setTickRate(BulletTimeManager.NORMAL_RATE));
    }
}