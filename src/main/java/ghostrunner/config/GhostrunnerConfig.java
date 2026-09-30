package ghostrunner.config;

/**
 * Ghostrunner 全部可调参数。
 * <p>集中管理，方便调手感、做配置、写文档。
 * <p>所有值都是 {@code static final}，编译期常量，运行时零开销。
 */
public final class GhostrunnerConfig {

    private GhostrunnerConfig() {}

    // ================================================================
    //                          耐力
    // ================================================================

    public static final float STAMINA_MAX = 100.0f;
    public static final float STAMINA_RECOVERY_PER_TICK = 0.625f;
    /** 网络同步阈值：耐力变化小于此值不发包 */
    public static final float STAMINA_SYNC_THRESHOLD = 0.1f;
    public static final float STAMINA_PER_DASH = 25.0f;
    public static final float STAMINA_PER_BLOCK = 30.0f;
    public static final float STAMINA_PERFECT_PROJECTILE = 10.0f;

    // ================================================================
    //                          子弹时间
    // ================================================================

    /** 每 tick 消耗的耐力（配合真实时间换算使用） */
    public static final float BT_STAMINA_PER_TICK = 1.0f;
    /** 进入子弹时间所需的最低耐力 */
    public static final float BT_MIN_STAMINA = 10.0f;
    /** 释放时的初速度倍率 */
    public static final double BT_RELEASE_SPEED = 1.5;
    /** 释放时的最大向上速度 */
    public static final double BT_RELEASE_UP_MAX = 0.8;
    /** 释放时的最大向下速度 */
    public static final double BT_RELEASE_DOWN_MAX = 1.0;
    /** 子弹时间的 tick rate */
    public static final float BULLET_TIME_RATE = 2.0f;
    /** 正常 tick rate */
    public static final float NORMAL_RATE = 20.0f;
    /** 释放后摔落免疫宽限期（毫秒） */
    public static final long BT_FALL_GRACE_MILLIS = 2000L;
    /** 每秒消耗的耐力。 */
    public static final float BT_STAMINA_PER_SECOND = 20.0f * BT_STAMINA_PER_TICK;
    /** 子弹时间期间玩家水平速度的每 tick 保留比例。 */
    public static final double BT_TRAVEL_DAMP_H = 0.15;
    /** 子弹时间期间玩家垂直速度的每 tick 保留比例。 */
    public static final double BT_TRAVEL_DAMP_V = 0.10;
    /** 子弹时间期间玩家垂直速度的下限——避免浮空停滞，至少缓慢下落。 */
    public static final double BT_TRAVEL_MIN_VY = -0.01;

    // ================================================================
    //                          冲刺
    // ================================================================

    public static final double DASH_SPEED = 1.20;
    public static final double DASH_UPWARD = 0.08;
    public static final int DASH_COOLDOWN = 20;
    public static final int DASH_WALL_WINDOW_TICKS = 8;
    public static final int DASH_DECAY_TICKS = 20;
    /** 空中每 tick 衰减因子 */
    public static final double DASH_DECAY_AIR = 0.90;

    // ================================================================
    //                          跑墙
    // ================================================================

    /** 沿墙奔跑的水平速度。 */
    public static final double WALL_RUN_SPEED = 0.30;
    /** 墙面探测距离。玩家 AABB 沿方向偏移这么远后检测碰撞。 */
    public static final double WALL_PROBE = 0.15;
    /** 跳出时的水平反推速度。 */
    public static final double WALL_JUMP_OUT_H = 0.35;
    /** 跳出时的竖直初速。 */
    public static final double WALL_JUMP_OUT_V = 0.40;
    /** 退出跑墙后的重入冷却（tick）。 */
    public static final int WALL_REENTRY_COOLDOWN = 15;
    /** 跑墙保持的最短 tick 数——低于此时不能跳出。 */
    public static final int WALL_MIN_RUN_TICKS = 4;

    /** 进入跑墙所需的最低水平速度（格/tick）。 */
    public static final double WALL_MIN_ENTRY_H_SPEED = 0.04;
    /** 进入跑墙所需的"朝墙分量"阈值。 */
    public static final double WALL_MIN_ENTRY_TOWARD = 0.3;
    /** 进入跑墙所需的最短离地 tick 数。 */
    public static final int WALL_MIN_AIRBORNE_TICKS = 2;

    /** 冲刺窗口的斜撞角度下界。低于此视为"擦过"。 */
    public static final double WALL_DASH_WINDOW_TOWARD_MIN = 0.15;
    /** 冲刺窗口的斜撞角度上界。高于此视为"正撞"。 */
    public static final double WALL_DASH_WINDOW_TOWARD_MAX = 0.75;

    // ================================================================
    //                          格挡
    // ================================================================

    /** 前摇：按下右键后的这段时间格挡不生效（毫秒）。 */
    public static final long BLOCK_PREPARE_MS = 300L;
    /** 完美窗口：前摇结束后的这段时间可完美格挡（毫秒）。 */
    public static final long BLOCK_PARRY_MS = 300L;
    /** 释放格挡后的硬直：这段时间内不能重新格挡（毫秒）。 */
    public static final long BLOCK_RECOVERY_MS = 400L;
    public static final float PARRY_REFLECT_DAMAGE = 1.0f;

    // ================================================================
    //                          爬墙
    // ================================================================

    /** 能爬的最大高度（格）。 */
    public static final int CLIMB_MAX_HEIGHT = 3;
    /** 高度差 1 格时给的竖直初速。 */
    public static final double CLIMB_VY_1 = 0.42;
    /** 高度差 2 格时给的竖直初速。 */
    public static final double CLIMB_VY_2 = 0.60;
    /** 高度差 3 格时给的竖直初速。 */
    public static final double CLIMB_VY_3 = 0.78;
    /** 水平前冲速度（让玩家贴墙上升）。 */
    public static final double CLIMB_FORWARD_V = 0.12;
    /** 爬墙冷却（tick）。防止贴墙狂按无限登高。 */
    public static final int CLIMB_COOLDOWN = 12;

    // ================================================================
    //                          弹反
    // ================================================================

    /** 弹反的最大距离（格）。 */
    public static final double PARRY_RANGE = 1.5;
    /** 前方锥点积阈值。0.3 ≈ 前方 ±72.5° 都算。 */
    public static final double PARRY_CONE_DOT = 0.3;
    /** 迎面判定阈值。弹射物速度与"弹射物→玩家"方向的最小点积。 */
    public static final double PARRY_INCOMING_DOT = 0.3;
    /** 弹射物必须达到的最小飞行速度（格/tick）。低于此视为静止 / 插地。 */
    public static final double PARRY_MIN_TARGET_SPEED = 0.05;
    /** 弹反速度相对原速的倍率。1.5 = 反弹后快 50%。 */
    public static final double PARRY_REFLECT_MULTIPLIER = 1.5;
    /** 弹反后的最低速度（格/tick）。保证慢箭弹回也有威力。 */
    public static final double PARRY_MIN_REFLECT_SPEED = 0.5;
    /** 弹反冷却（tick）。 */
    public static final int PARRY_COOLDOWN = 10;

    // ================================================================
    //                          通用
    // ================================================================

    public static final double VEC_EPSILON_SQ = 1e-6;
    public static final double AIM_EPSILON_SQ = 0.0001;

    // ================================================================
    //                          挥砍
    // ================================================================

    /** 挥砍基础伤害。会被一击必杀 Mixin 放大，实际值不重要，但必须 > 0。 */
    public static final float SWORD_BASE_DAMAGE = 10.0f;

    /** 搜索范围（AABB inflate）。 */
    public static final double SWORD_SEARCH_RADIUS = 4.0;

    // 视线坐标系范围（前 / 侧 / 上）
    /** 后方容差，-1.0 意味着背后 1 格也能打到。 */
    public static final double SWORD_RANGE_FORWARD_MIN = -1.0;
    /** 前方攻击距离。 */
    public static final double SWORD_RANGE_FORWARD_MAX = 2.5;
    /** 左右容差。 */
    public static final double SWORD_RANGE_SIDE = 1.5;
    /** 下方容差（低头或铲地）。 */
    public static final double SWORD_RANGE_VERT_MIN = -0.6;
    /** 上方容差（抬头）。 */
    public static final double SWORD_RANGE_VERT_MAX = 0.6;

    // ================================================================
    //                          盾牌
    // ================================================================

    /** 被盾牌弹开的水平速度。 */
    public static final double SHIELD_KNOCKBACK = 0.55;
    /** 被盾牌弹开的向上速度。 */
    public static final double SHIELD_KNOCKBACK_UP = 0.30;
}