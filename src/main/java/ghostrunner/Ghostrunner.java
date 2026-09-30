package ghostrunner;

import ghostrunner.api.BulletTimeState;
import ghostrunner.api.GhostrunnerCommand;
import ghostrunner.api.GhostrunnerStamina;
import ghostrunner.api.GhostrunnerState;
import ghostrunner.api.WallRunState;
import ghostrunner.handler.BlockHandler;
import ghostrunner.handler.BulletTimeManager;
import ghostrunner.handler.ClimbHandler;
import ghostrunner.handler.DashHandler;
import ghostrunner.handler.ParryHandler;
import ghostrunner.handler.SwordHandler;
import ghostrunner.handler.WallRunHandler;
import ghostrunner.item.GRItems;
import ghostrunner.network.GhostrunnerNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ghostrunner implements ModInitializer {
    public static final String MOD_ID = "ghostrunner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 项目级 ID 工具。 */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("[Ghostrunner] Online.");

        // 网络包类型注册（C2S + S2C）
        GhostrunnerNetworking.registerPayloads();

        // 物品注册
        GRItems.register();

        // 创造模式标签页
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register((FabricCreativeModeTabOutput entries) -> {
                    entries.accept(GRItems.GHOSTRUNNER_TAG);
                });

        // 命令注册
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GhostrunnerCommand.register(dispatcher));

        registerReceivers();
        registerEvents();
    }

    // ================================================================
    //                       网络包接收（C2S）
    // ================================================================

    private void registerReceivers() {

        // ---------- 跑墙跳出 / 爬墙 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.JumpOffWallPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!GhostrunnerState.isGhostrunner(player)) return;

                    WallRunState state = (WallRunState) player;
                    if (state.ghostrunner$isWallRunning()) {
                        state.ghostrunner$jumpOffWall();
                        return;
                    }
                    ClimbHandler.tryClimb(player);
                }));

        // ---------- 短按冲刺 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.DashPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!GhostrunnerState.isGhostrunner(player)) return;
                    DashHandler.tryDash(player,
                            payload.forward(), payload.back(),
                            payload.left(), payload.right());
                }));

        // ---------- 蓄力开始 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.DashChargeStartPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!GhostrunnerState.isGhostrunner(player)) return;

                    BulletTimeState bt = (BulletTimeState) player;
                    boolean inAir = !player.onGround()
                            && !WallRunHandler.hasGroundBelow(player);

                    if (inAir && bt.ghostrunner$tryEnterBulletTime()) {
                        bt.ghostrunner$updateBulletTimeAim(
                                DashHandler.computeAimFromInput(player,
                                        payload.forward(), payload.back(),
                                        payload.left(), payload.right()));
                    } else {
                        DashHandler.tryDash(player,
                                payload.forward(), payload.back(),
                                payload.left(), payload.right());
                    }
                }));

        // ---------- 蓄力方向更新 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.DashChargeAimPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    BulletTimeState bt = (BulletTimeState) player;
                    if (!bt.ghostrunner$isInBulletTime()) return;
                    bt.ghostrunner$updateBulletTimeAim(
                            DashHandler.computeAimFromInput(player,
                                    payload.forward(), payload.back(),
                                    payload.left(), payload.right()));
                }));

        // ---------- 蓄力释放 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.DashChargeReleasePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    BulletTimeState bt = (BulletTimeState) context.player();
                    if (bt.ghostrunner$isInBulletTime()) {
                        bt.ghostrunner$exitBulletTimeAndDash();
                    }
                }));

        // ---------- 挥砍 / 弹反 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.AttackPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!GhostrunnerState.isGhostrunner(player)) return;

                    // 先尝试弹反，成功就跳过挥砍
                    if (ParryHandler.tryParry(player)) return;

                    SwordHandler.performSwing(player);
                }));

        // ---------- 格挡开始 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.BlockStartPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    if (!GhostrunnerState.isGhostrunner(player)) return;
                    if (!player.getMainHandItem().is(ItemTags.SWORDS)) return;

                    GhostrunnerStamina stamina = (GhostrunnerStamina) player;
                    stamina.ghostrunner$setBlocking(true);
                }));

        // ---------- 格挡结束 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.BlockStopPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    GhostrunnerStamina stamina = (GhostrunnerStamina) context.player();
                    stamina.ghostrunner$setBlocking(false);
                }));
        // ---------- 子弹时间提前退出 ----------
        ServerPlayNetworking.registerGlobalReceiver(
                GhostrunnerNetworking.BulletTimeExitRequestPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    BulletTimeState bt = (BulletTimeState) player;
                    if (bt.ghostrunner$isInBulletTime()) {
                        bt.ghostrunner$exitBulletTimeAndDash();
                    }
                }));
    }

    // ================================================================
    //                          全局事件
    // ================================================================

    private void registerEvents() {

        // ---------- 冷却递减 ----------
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            DashHandler.tickCooldowns();
            ClimbHandler.tickCooldowns();
            ParryHandler.tickCooldowns();
        });

        // ---------- 玩家加载 → 同步标记 ----------
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof ServerPlayer player)) return;

            GhostrunnerState.GhostrunnerStateAccessor a =
                    (GhostrunnerState.GhostrunnerStateAccessor) player;
            ServerPlayNetworking.send(player,
                    new GhostrunnerNetworking.AscendedStatePayload(a.ghostrunner$isAscended()));
        });

        // ---------- 玩家重生 / 维度切换 → 复制状态 ----------
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            GhostrunnerState.GhostrunnerStateAccessor oldA =
                    (GhostrunnerState.GhostrunnerStateAccessor) oldPlayer;
            GhostrunnerState.GhostrunnerStateAccessor newA =
                    (GhostrunnerState.GhostrunnerStateAccessor) newPlayer;
            newA.ghostrunner$setAscended(oldA.ghostrunner$isAscended());

            BlockHandler.forceCancel(newPlayer);
        });

        // ---------- 玩家断开 → 清理子弹时间 ----------
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BulletTimeManager.exit(handler.getPlayer()));

        // ---------- 玩家从世界卸载 → 清理 + 取消格挡 ----------
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ServerPlayer sp) {
                BulletTimeManager.exit(sp);
                BlockHandler.forceCancel(sp);
            }
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // 清理可能的残留状态
            server.tickRateManager().setTickRate(BulletTimeManager.NORMAL_RATE);
        });
    }
}