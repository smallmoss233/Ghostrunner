package ghostrunner.api;

import net.minecraft.world.phys.Vec3;

public interface BulletTimeState {

    boolean ghostrunner$isInBulletTime();

    /** 尝试进入子弹时间。失败返回 false。 */
    boolean ghostrunner$tryEnterBulletTime();

    /** 退出子弹时间并执行最终冲刺。 */
    void ghostrunner$exitBulletTimeAndDash();

    /** 更新瞄准方向（由客户端 WASD 决定）。 */
    void ghostrunner$updateBulletTimeAim(Vec3 direction);

    /** 当前瞄准方向。 */
    Vec3 ghostrunner$getBulletTimeAim();
}