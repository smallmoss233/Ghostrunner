package ghostrunner.handler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BulletTimeManager {

    private BulletTimeManager() {}

    private static final Set<UUID> activePlayers = new HashSet<>();

    /** 子弹时间释放后，摔落免疫的宽限期（毫秒）。2000 = 2 秒。 */
    public static final long FALL_GRACE_MILLIS = 2000L;

    private static final Map<UUID, Long> fallGraceUntil = new HashMap<>();

    public static final float NORMAL_RATE = 20.0f;
    public static final float BULLET_TIME_RATE = 2.0f;

    private static MinecraftServer cachedServer;

    // ================================================================

    public static void enter(ServerPlayer player) {
        boolean wasEmpty = activePlayers.isEmpty();
        activePlayers.add(player.getUUID());
        fallGraceUntil.remove(player.getUUID());   // 进入时清掉旧宽限

        MinecraftServer server = player.level().getServer();
        if (server != null) cachedServer = server;

        if (wasEmpty && server != null) {
            server.tickRateManager().setTickRate(BULLET_TIME_RATE);
        }
    }

    public static void exit(ServerPlayer player) {
        activePlayers.remove(player.getUUID());
        fallGraceUntil.put(player.getUUID(),
                System.currentTimeMillis() + FALL_GRACE_MILLIS);

        if (activePlayers.isEmpty() && cachedServer != null) {
            cachedServer.tickRateManager().setTickRate(NORMAL_RATE);
        }
    }

    public static void forceReset() {
        activePlayers.clear();
        fallGraceUntil.clear();
        if (cachedServer != null) {
            cachedServer.tickRateManager().setTickRate(NORMAL_RATE);
        }
    }

    public static boolean hasAnyActive() {
        return !activePlayers.isEmpty();
    }

    public static boolean isActive(ServerPlayer player) {
        return activePlayers.contains(player.getUUID());
    }

    /**
     * 是否应该免疫摔落伤害。包含：
     * <ul>
     *   <li>正在子弹时间中</li>
     *   <li>刚释放子弹时间，还在宽限期内</li>
     * </ul>
     */
    public static boolean shouldImmuneFall(Player player) {
        if (activePlayers.contains(player.getUUID())) return true;
        Long until = fallGraceUntil.get(player.getUUID());
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            fallGraceUntil.remove(player.getUUID());
            return false;
        }
        return true;
    }

    public static float currentServerRate() {
        return cachedServer != null ? cachedServer.tickRateManager().tickrate() : NORMAL_RATE;
    }
}