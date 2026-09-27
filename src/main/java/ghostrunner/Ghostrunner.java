package ghostrunner;

import ghostrunner.api.WallRunState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ghostrunner implements ModInitializer {
    public static final String MOD_ID = "ghostrunner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 客户端按空格 → 服务端跳出跑墙 */
    public static final Identifier JUMP_OFF_WALL_PACKET =
            new Identifier(MOD_ID, "jump_off_wall");

    @Override
    public void onInitialize() {
        LOGGER.info("[Ghostrunner] Wall-run module online.");

        ServerPlayNetworking.registerGlobalReceiver(JUMP_OFF_WALL_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    // 回主线程处理，避免网络线程操作世界
                    server.execute(() -> {
                        if (player instanceof WallRunState state
                                && state.ghostrunner$isWallRunning()) {
                            state.ghostrunner$jumpOffWall();
                        }
                    });
                });
    }
}