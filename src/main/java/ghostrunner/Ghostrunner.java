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

    /** 客户端 → 服务端：请求跳出跑墙 */
    public static final Identifier JUMP_OFF_WALL_PACKET =
            new Identifier(MOD_ID, "jump_off_wall");

    /** 服务端 → 客户端：同步跑墙状态（用于相机倾斜） */
    public static final Identifier WALL_RUN_STATE_PACKET =
            new Identifier(MOD_ID, "wall_run_state");

    @Override
    public void onInitialize() {
        LOGGER.info("[Ghostrunner] Wall-run module online.");

        ServerPlayNetworking.registerGlobalReceiver(JUMP_OFF_WALL_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        if (player instanceof WallRunState state
                                && state.ghostrunner$isWallRunning()) {
                            state.ghostrunner$jumpOffWall();
                        }
                    });
                });
    }
}