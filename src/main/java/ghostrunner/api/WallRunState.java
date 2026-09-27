package ghostrunner.api;

public interface WallRunState {
    boolean ghostrunner$isWallRunning();
    void ghostrunner$jumpOffWall();
    void ghostrunner$clearWallRunCooldown();
    void ghostrunner$startDashWindow(int ticks);

    /** 开启"冲刺衰减"，让玩家冲刺后快速刹停，消除惯性。 */
    void ghostrunner$startDashDecay(int ticks);
}