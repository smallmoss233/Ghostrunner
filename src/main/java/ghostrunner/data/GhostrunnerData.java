package ghostrunner.data;

import ghostrunner.config.GhostrunnerConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Ghostrunner 的全部服务端权威状态。
 * <p>由 {@code PlayerGhostrunnerMixin} 作为 {@code @Unique} 字段持有。
 * <p><b>字段直接 public</b>，不做 getter/setter——这是数据容器，不是 API 边界。
 * <p>只有 {@link #ascended} 会持久化。
 */
public final class GhostrunnerData {

    // ================================================================
    //                      持久字段
    // ================================================================

    public boolean ascended = false;

    // ================================================================
    //                      耐力
    // ================================================================

    public float stamina = GhostrunnerConfig.STAMINA_MAX;
    public boolean airDashUsed = false;
    public float lastSentStamina = -1.0f;

    // ================================================================
    //                      冲刺
    // ================================================================

    public int dashWindowTicks = 0;
    public int dashDecayTicks = 0;
    public Vec3 dashDirection = Vec3.ZERO;

    // ================================================================
    //                      跑墙
    // ================================================================

    public boolean wallRunning = false;
    public Direction wallSide = null;
    public Vec3 lockedDirection = Vec3.ZERO;
    public int wallRunCooldown = 0;
    public int wallRunTicks = 0;
    public int airborneTicks = 0;

    public boolean lastSentRunning = false;
    public Direction lastSentSide = null;

    // ================================================================
    //                      子弹时间
    // ================================================================

    /** 是否处于子弹时间。 */
    public boolean inBulletTime = false;
    /** 进入子弹时间的时间戳。 */
    public long btStartedMs = 0L;
    /** 进入子弹时间时冻结的耐力。 */
    public float btStaminaAtStart = 0f;
    /** 当前瞄准方向（单位向量）。 */
    public Vec3 btAim = Vec3.ZERO;
    /** 摔落免疫截止时间戳（毫秒）。 */
    public long fallGraceUntilMs = 0L;

    // ================================================================
    //                      格挡
    // ================================================================

    /** 玩家是否按住右键（"想格挡"的意图）。 */
    public boolean blockHeld = false;
    /** 当前这次按下的时间戳（毫秒）。 */
    public long blockStartMs = 0L;
    /** 上次松开的时间戳（毫秒）。用于硬直判定。 */
    public long blockReleaseMs = 0L;

    // ================================================================
    //                      NBT 持久化
    // ================================================================

    public void save(ValueOutput output) {
        output.putBoolean("GhostrunnerAscended", ascended);
    }

    public void load(ValueInput input) {
        ascended = input.getBooleanOr("GhostrunnerAscended", false);
    }

    // ================================================================
    //                      生命周期
    // ================================================================

    /**
     * 重生 / 维度切换时调用。
     * <p>复制持久字段，并重置所有临时字段。
     */
    public void copyFrom(GhostrunnerData other) {
        this.ascended = other.ascended;
        resetTransient();
    }

    /** 重置所有临时字段到初始值。 */
    public void resetTransient() {
        stamina = GhostrunnerConfig.STAMINA_MAX;
        airDashUsed = false;
        lastSentStamina = -1.0f;

        dashWindowTicks = 0;
        dashDecayTicks = 0;
        dashDirection = Vec3.ZERO;

        wallRunning = false;
        wallSide = null;
        lockedDirection = Vec3.ZERO;
        wallRunCooldown = 0;
        wallRunTicks = 0;
        airborneTicks = 0;
        lastSentRunning = false;
        lastSentSide = null;

        inBulletTime = false;
        btAim = Vec3.ZERO;
        blockHeld = false;
        blockStartMs = 0L;
        blockReleaseMs = 0L;
    }
}