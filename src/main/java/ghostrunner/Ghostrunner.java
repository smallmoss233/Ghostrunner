package ghostrunner;

import ghostrunner.api.BulletTimeState;
import ghostrunner.api.GhostrunnerCommand;
import ghostrunner.api.GhostrunnerState;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.BulletTimeManager;
import ghostrunner.handler.ClimbHandler;
import ghostrunner.handler.DashHandler;
import ghostrunner.handler.SwordHandler;
import ghostrunner.handler.WallRunHandler;
import ghostrunner.item.GRItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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

    // ============ 网络包 ============
    // C2S
    public static final Identifier JUMP_OFF_WALL_PACKET   = id("jump_off_wall");
    public static final Identifier DASH_PACKET            = id("dash");
    public static final Identifier DASH_CHARGE_START_PACKET   = id("dash_charge_start");
    public static final Identifier DASH_CHARGE_AIM_PACKET     = id("dash_charge_aim");
    public static final Identifier DASH_CHARGE_RELEASE_PACKET = id("dash_charge_release");
    public static final Identifier ATTACK_PACKET          = id("attack");

    // S2C
    public static final Identifier WALL_RUN_STATE_PACKET  = id("wall_run_state");
    public static final Identifier ASCENDED_STATE_PACKET  = id("ascended_state");
    public static final Identifier BULLET_TIME_STATE_PACKET = id("bullet_time_state");
    public static final Identifier DASH_SUCCESS_PACKET    = id("dash_success");
    public static final Identifier STAMINA_PACKET         = id("stamina");

    private static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[Ghostrunner] Online.");

        GRItems.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.add(GRItems.GHOSTRUNNER_TAG));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GhostrunnerCommand.register(dispatcher));

        registerReceivers();
        registerEvents();
    }

    // ================================================================
    //                       网络包接收
    // ================================================================

    private void registerReceivers() {

        // 跑墙跳出 / 爬墙
        ServerPlayNetworking.registerGlobalReceiver(JUMP_OFF_WALL_PACKET,
                (server, player, handler, buf, responseSender) -> server.execute(() -> {
                    if (!GhostrunnerState.isGhostrunner(player)) return;

                    WallRunState state = (WallRunState) player;
                    if (state.ghostrunner$isWallRunning()) {
                        state.ghostrunner$jumpOffWall();
                        return;
                    }
                    ClimbHandler.tryClimb(player);
                }));

        // 短按冲刺（地面 / 短按）
        ServerPlayNetworking.registerGlobalReceiver(DASH_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    boolean f = buf.readBoolean();
                    boolean b = buf.readBoolean();
                    boolean l = buf.readBoolean();
                    boolean r = buf.readBoolean();

                    server.execute(() -> {
                        if (GhostrunnerState.isGhostrunner(player)) {
                            DashHandler.tryDash(player, f, b, l, r);
                        }
                    });
                });

        // 蓄力开始
        ServerPlayNetworking.registerGlobalReceiver(DASH_CHARGE_START_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    boolean f = buf.readBoolean();
                    boolean b = buf.readBoolean();
                    boolean l = buf.readBoolean();
                    boolean r = buf.readBoolean();

                    server.execute(() -> {
                        if (!GhostrunnerState.isGhostrunner(player)) return;

                        BulletTimeState bt = (BulletTimeState) player;
                        boolean inAir = !player.isOnGround() && !WallRunHandler.hasGroundBelow(player);

                        if (inAir && bt.ghostrunner$tryEnterBulletTime()) {
                            bt.ghostrunner$updateBulletTimeAim(
                                    DashHandler.computeAimFromInput(player, f, b, l, r));
                        } else {
                            DashHandler.tryDash(player, f, b, l, r);
                        }
                    });
                });

        // 蓄力方向更新
        ServerPlayNetworking.registerGlobalReceiver(DASH_CHARGE_AIM_PACKET,
                (server, player, handler, buf, responseSender) -> {
                    boolean f = buf.readBoolean();
                    boolean b = buf.readBoolean();
                    boolean l = buf.readBoolean();
                    boolean r = buf.readBoolean();

                    server.execute(() -> {
                        BulletTimeState bt = (BulletTimeState) player;
                        if (!bt.ghostrunner$isInBulletTime()) return;
                        bt.ghostrunner$updateBulletTimeAim(
                                DashHandler.computeAimFromInput(player, f, b, l, r));
                    });
                });

        // 蓄力释放
        ServerPlayNetworking.registerGlobalReceiver(DASH_CHARGE_RELEASE_PACKET,
                (server, player, handler, buf, responseSender) -> server.execute(() -> {
                    BulletTimeState bt = (BulletTimeState) player;
                    if (bt.ghostrunner$isInBulletTime()) {
                        bt.ghostrunner$exitBulletTimeAndDash();
                    }
                }));

        // 挥砍
        ServerPlayNetworking.registerGlobalReceiver(ATTACK_PACKET,
                (server, player, handler, buf, responseSender) -> server.execute(() -> {
                    if (!GhostrunnerState.isGhostrunner(player)) return;
                    SwordHandler.performSwing(player);
                }));
    }

    // ================================================================
    //                          全局事件
    // ================================================================

    private void registerEvents() {

        // 冷却递减
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DashHandler.tickCooldowns();
            ClimbHandler.tickCooldowns();
        });

        // 玩家加载 → 同步标记
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            GhostrunnerState.GhostrunnerStateAccessor a =
                    (GhostrunnerState.GhostrunnerStateAccessor) player;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(a.ghostrunner$isAscended());
            ServerPlayNetworking.send(player, ASCENDED_STATE_PACKET, buf);
        });

        // 玩家重生 / 维度切换 → 复制状态
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            GhostrunnerState.GhostrunnerStateAccessor oldA =
                    (GhostrunnerState.GhostrunnerStateAccessor) oldPlayer;
            GhostrunnerState.GhostrunnerStateAccessor newA =
                    (GhostrunnerState.GhostrunnerStateAccessor) newPlayer;
            newA.ghostrunner$setAscended(oldA.ghostrunner$isAscended());
        });

        // 玩家断开 → 清理子弹时间
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BulletTimeManager.exit(handler.getPlayer()));

        // 玩家从世界卸载 → 清理
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayerEntity sp) {
                BulletTimeManager.exit(sp);
            }
        });
    }
}