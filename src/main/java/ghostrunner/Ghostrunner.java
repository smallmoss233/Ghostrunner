package ghostrunner;

import ghostrunner.api.GhostrunnerCommand;
import ghostrunner.api.GhostrunnerState;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.ClimbHandler;
import ghostrunner.handler.DashHandler;
import ghostrunner.item.GRItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ghostrunner implements ModInitializer {
    public static final String MOD_ID = "ghostrunner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier JUMP_OFF_WALL_PACKET =
            new Identifier(MOD_ID, "jump_off_wall");
    public static final Identifier WALL_RUN_STATE_PACKET =
            new Identifier(MOD_ID, "wall_run_state");
    public static final Identifier DASH_PACKET =
            new Identifier(MOD_ID, "dash");
    public static final Identifier ASCENDED_STATE_PACKET =
            new Identifier(MOD_ID, "ascended_state");
    /** 服务端 → 客户端：同步耐力值 */
    public static final Identifier STAMINA_PACKET = new Identifier(MOD_ID, "stamina");

    @Override
    public void onInitialize() {
        LOGGER.info("[Ghostrunner] Online.");

        // 注册物品
        GRItems.register();

        // 物品栏
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.add(GRItems.GHOSTRUNNER_TAG));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GhostrunnerCommand.register(dispatcher));

        // 跑墙跳出 / 爬墙
        ServerPlayNetworking.registerGlobalReceiver(JUMP_OFF_WALL_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        if (!GhostrunnerState.isGhostrunner(player)) return;

                        if (player instanceof WallRunState state
                                && state.ghostrunner$isWallRunning()) {
                            state.ghostrunner$jumpOffWall();
                            return;
                        }
                        ClimbHandler.tryClimb(player);
                    });
                });

        // 冲刺
        ServerPlayNetworking.registerGlobalReceiver(DASH_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    boolean forward = buf.readBoolean();
                    boolean back    = buf.readBoolean();
                    boolean left    = buf.readBoolean();
                    boolean right   = buf.readBoolean();

                    server.execute(() -> {
                        if (GhostrunnerState.isGhostrunner(player)) {
                            DashHandler.tryDash(player, forward, back, left, right);
                        }
                    });
                });

        // 全局递减冲刺冷却
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DashHandler.tickCooldowns();
            ClimbHandler.tickCooldowns();
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;

            if (player instanceof GhostrunnerState.GhostrunnerStateAccessor a) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(a.ghostrunner$isAscended());
                ServerPlayNetworking.send(player, ASCENDED_STATE_PACKET, buf);
            }
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer instanceof GhostrunnerState.GhostrunnerStateAccessor oldA
                    && newPlayer instanceof GhostrunnerState.GhostrunnerStateAccessor newA) {
                newA.ghostrunner$setAscended(oldA.ghostrunner$isAscended());
            }

            // 强制重置跑墙状态（不复制旧的）
            if (newPlayer instanceof WallRunState newWall) {
            }
        });

    }
}