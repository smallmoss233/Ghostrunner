package ghostrunner.api;

import net.minecraft.entity.player.PlayerEntity;

public final class GhostrunnerState {

    private GhostrunnerState() {}

    /** 玩家是否已被改造成幽灵行者。 */
    public static boolean isGhostrunner(PlayerEntity player) {
        return player instanceof GhostrunnerStateAccessor a && a.ghostrunner$isAscended();
    }

    /**
     * 由 {@code PlayerEntityMixin} 实现，用于存取"幽灵行者"标记。
     * 纯接口，不要注册到 mixins.json。
     */
    public interface GhostrunnerStateAccessor {
        boolean ghostrunner$isAscended();
        void ghostrunner$setAscended(boolean value);
    }
}