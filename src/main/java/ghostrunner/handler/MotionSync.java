package ghostrunner.handler;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class MotionSync {

    private MotionSync() {}

    /** 把服务端当前速度广播给客户端。26.3 没有 hurtMarked / hasImpulse。 */
    public static void syncMotion(Player player) {
        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        }
    }

    /** 设速度并立刻同步。 */
    public static void setAndSync(Player player, double vx, double vy, double vz) {
        player.setDeltaMovement(vx, vy, vz);
        syncMotion(player);
    }

    public static void setAndSync(Player player, Vec3 v) {
        setAndSync(player, v.x, v.y, v.z);
    }
}