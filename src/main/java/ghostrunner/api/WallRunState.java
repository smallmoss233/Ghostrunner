package ghostrunner.api;

import net.minecraft.util.math.Vec3d;

public interface WallRunState {
    boolean ghostrunner$isWallRunning();
    void ghostrunner$jumpOffWall();
    void ghostrunner$clearWallRunCooldown();
    void ghostrunner$startDashWindow(int ticks);
    void ghostrunner$startDashDecay(int ticks);

    /** 记录冲刺方向（用于冲刺窗口内判定，不受撞墙影响）。 */
    void ghostrunner$setDashDirection(Vec3d direction);
    Vec3d ghostrunner$getDashDirection();
}