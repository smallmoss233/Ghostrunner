package ghostrunner.handler;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class MotionSync {

    private MotionSync() {}

    // ================================================================
    //                      广播
    // ================================================================

    /**
     * 把玩家自己的速度同步给他的客户端。
     * <p>用于本地预测——玩家移动主要由服务端计算，客户端要立即响应。
     */
    public static void syncMotion(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    /**
     * 把任意实体的速度广播给所有追踪它的玩家。
     * <p>用于弹射物、抛掷物等——它们的位置更新本来就发给追踪者。
     */
    public static void syncMotionToTracking(Entity entity) {
        if (entity.level() instanceof ServerLevel sl) {
            sl.getChunkSource().sendToTrackingPlayers(entity,
                    new ClientboundSetEntityMotionPacket(entity));
        }
    }

    // ================================================================
    //                      设速度 + 同步
    // ================================================================

    public static void setAndSync(Player player, double vx, double vy, double vz) {
        player.setDeltaMovement(vx, vy, vz);
        syncMotion(player);
    }

    public static void setAndSync(Player player, Vec3 v) {
        setAndSync(player, v.x, v.y, v.z);
    }

    /**
     * 设实体速度并广播给追踪玩家。
     * <p>与玩家版本分开——玩家走 {@code connection}，普通实体走 chunk tracking。
     * <p><b>注意</b>：会调用 {@code setDeltaMovement}，不会清 inGround 状态。
     * 弹射物弹反请改用 {@code lerpMotion} + {@link #syncMotionToTracking(Entity)}。
     */
    public static void setAndSyncTracking(Entity entity, Vec3 v) {
        entity.setDeltaMovement(v);
        syncMotionToTracking(entity);
    }
}