package ghostrunner.api;

/**
 * 耐力系统接口，由 {@code PlayerEntityMixin} 实现。
 * <p>用于让 DashHandler、ClimbHandler 等外部类访问玩家的耐力状态。
 */
public interface GhostrunnerStamina {

    /** 当前耐力值（0.0 ~ MAX） */
    float ghostrunner$getStamina();

    /** 直接设置耐力（会被 clamp 到 0~MAX） */
    void ghostrunner$setStamina(float value);

    /** 扣除耐力 */
    void ghostrunner$consumeStamina(float amount);

    /** 本 tick 是否已经空中冲刺过 */
    boolean ghostrunner$isAirDashUsed();

    /** 设置空中冲刺标记 */
    void ghostrunner$setAirDashUsed(boolean used);
}