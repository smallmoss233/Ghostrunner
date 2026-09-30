package ghostrunner.handler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 子弹时间的 tick rate 协调器。
 * <p><b>只管一件事</b>：有几个玩家处于子弹时间，从而决定服务器 tick rate。
 * <p>玩家状态、耐力计算、摔落免疫全部在 {@link ghostrunner.data.GhostrunnerData}
 * 和 {@link GhostrunnerTickHandler} 里，本类不掺和。
 */
public final class BulletTimeManager {

    private BulletTimeManager() {}

    public static final float NORMAL_RATE = 20.0f;
    public static final float BULLET_TIME_RATE = 2.0f;

    /** 当前处于子弹时间的玩家数。 */
    private static int activeCount = 0;

    /** 缓存 server 引用。玩家下线时 getServer() 可能为 null。 */
    private static MinecraftServer cachedServer;

    // ================================================================

    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server != null) cachedServer = server;
        activeCount++;
        applyTickRate();
    }

    public static void exit(ServerPlayer player) {
        if (activeCount > 0) activeCount--;
        applyTickRate();
    }

    /** 强制清理。服务器停止/异常时用。 */
    public static void forceReset() {
        activeCount = 0;
        applyTickRate();
    }

    public static boolean hasAnyActive() {
        return activeCount > 0;
    }

    private static void applyTickRate() {
        if (cachedServer == null) return;
        float target = activeCount > 0 ? BULLET_TIME_RATE : NORMAL_RATE;
        if (cachedServer.tickRateManager().tickrate() != target) {
            cachedServer.tickRateManager().setTickRate(target);
        }
    }
}