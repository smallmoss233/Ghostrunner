package ghostrunner.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 通用冷却追踪器。按 UUID 记录剩余 tick 数。
 * <p>替代 {@code DashHandler} / {@code ClimbHandler} / {@code ParryHandler} 里
 * 各自维护的 {@code HashMap<UUID, Integer>}。
 */
public final class CooldownTracker {

    private final Map<UUID, Integer> cooldowns = new HashMap<>();

    /** 每 tick 递减所有冷却，由 {@code Ghostrunner} 统一调度。 */
    public void tickAll() {
        cooldowns.replaceAll((uuid, t) -> Math.max(0, t - 1));
    }

    public boolean isOnCooldown(UUID id) {
        Integer t = cooldowns.get(id);
        return t != null && t > 0;
    }

    public void set(UUID id, int ticks) {
        cooldowns.put(id, ticks);
    }

    public void clear(UUID id) {
        cooldowns.remove(id);
    }
}