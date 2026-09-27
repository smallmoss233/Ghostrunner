package ghostrunner.api;

public interface WallRunState {
    boolean ghostrunner$isWallRunning();
    void ghostrunner$jumpOffWall();
    void ghostrunner$clearWallRunCooldown();

    /** 开启"冲刺贴墙窗口"，窗口内碰到墙必定触发跑墙。 */
    void ghostrunner$startDashWindow(int ticks);
}