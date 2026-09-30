package ghostrunner.api;

import ghostrunner.data.GhostrunnerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 幽灵行者玩家。由 {@code PlayerGhostrunnerMixin} 实现。
 * <p>合并了原 {@code WallRunState} / {@code GhostrunnerStamina} /
 * {@code BulletTimeState} / {@code GhostrunnerDataHolder} / {@code GhostrunnerState.Accessor}。
 * <p>典型用法：
 * <pre>{@code
 * GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
 * GhostrunnerData data = gr.ghostrunner$data();
 * }</pre>
 */
public interface GhostrunnerPlayer {

    // ================================================================
    //                      数据容器
    // ================================================================

    /** 直接访问所有权威状态。永远可用，与是否 ascended 无关。 */
    GhostrunnerData ghostrunner$data();

    // ================================================================
    //                      改造标记
    // ================================================================

    boolean ghostrunner$isAscended();
    void ghostrunner$setAscended(boolean value);

    // ================================================================
    //                      耐力
    // ================================================================

    float ghostrunner$getStamina();
    void ghostrunner$setStamina(float value);
    void ghostrunner$consumeStamina(float amount);

    // ================================================================
    //                      格挡
    // ================================================================

    boolean ghostrunner$isBlocking();
    void ghostrunner$setBlocking(boolean blocking);

    // ================================================================
    //                      跑墙
    // ================================================================

    boolean ghostrunner$isWallRunning();
    void ghostrunner$jumpOffWall();

    // ================================================================
    //                      子弹时间
    // ================================================================

    boolean ghostrunner$isInBulletTime();
    boolean ghostrunner$tryEnterBulletTime();
    void ghostrunner$exitBulletTimeAndDash();
    void ghostrunner$updateBulletTimeAim(Vec3 direction);
    Vec3 ghostrunner$getBulletTimeAim();

    // ================================================================
    //                      静态工具
    // ================================================================

    /** 便捷 cast。玩家未实现接口时抛 ClassCastException。 */
    static GhostrunnerPlayer of(Player player) {
        return (GhostrunnerPlayer) player;
    }

    /**
     * 玩家是否已被改造成幽灵行者。
     * <p>所有能力门控入口：{@code if (!GhostrunnerPlayer.isGhostrunner(player)) return;}
     */
    static boolean isGhostrunner(Player player) {
        return player instanceof GhostrunnerPlayer gr && gr.ghostrunner$isAscended();
    }

    /** 拿到数据容器。非幽灵行者也能用（数据是默认值状态）。 */
    static GhostrunnerData data(Player player) {
        return ((GhostrunnerPlayer) player).ghostrunner$data();
    }
}