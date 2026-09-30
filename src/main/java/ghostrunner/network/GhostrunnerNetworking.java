package ghostrunner.network;

import ghostrunner.Ghostrunner;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class GhostrunnerNetworking {
    private GhostrunnerNetworking() {}

    // ============ C2S 数据包定义 ============

    // 1. 跑墙跳出 / 爬墙
    public record JumpOffWallPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("jump_off_wall");
        public static final CustomPacketPayload.Type<JumpOffWallPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, JumpOffWallPayload> CODEC =
                StreamCodec.unit(new JumpOffWallPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 2. 短按冲刺
    public record DashPayload(boolean forward, boolean back, boolean left, boolean right) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("dash");
        public static final CustomPacketPayload.Type<DashPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DashPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, DashPayload::forward,
                        ByteBufCodecs.BOOL, DashPayload::back,
                        ByteBufCodecs.BOOL, DashPayload::left,
                        ByteBufCodecs.BOOL, DashPayload::right,
                        DashPayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 3. 蓄力开始
    public record DashChargeStartPayload(boolean forward, boolean back, boolean left, boolean right) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("dash_charge_start");
        public static final CustomPacketPayload.Type<DashChargeStartPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DashChargeStartPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, DashChargeStartPayload::forward,
                        ByteBufCodecs.BOOL, DashChargeStartPayload::back,
                        ByteBufCodecs.BOOL, DashChargeStartPayload::left,
                        ByteBufCodecs.BOOL, DashChargeStartPayload::right,
                        DashChargeStartPayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 4. 蓄力方向更新
    public record DashChargeAimPayload(boolean forward, boolean back, boolean left, boolean right) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("dash_charge_aim");
        public static final CustomPacketPayload.Type<DashChargeAimPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DashChargeAimPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, DashChargeAimPayload::forward,
                        ByteBufCodecs.BOOL, DashChargeAimPayload::back,
                        ByteBufCodecs.BOOL, DashChargeAimPayload::left,
                        ByteBufCodecs.BOOL, DashChargeAimPayload::right,
                        DashChargeAimPayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 5. 蓄力释放
    public record DashChargeReleasePayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("dash_charge_release");
        public static final CustomPacketPayload.Type<DashChargeReleasePayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DashChargeReleasePayload> CODEC =
                StreamCodec.unit(new DashChargeReleasePayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 6. 挥砍 / 弹反
    public record AttackPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("attack");
        public static final CustomPacketPayload.Type<AttackPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AttackPayload> CODEC =
                StreamCodec.unit(new AttackPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 7. 格挡开始
    public record BlockStartPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("block_start");
        public static final CustomPacketPayload.Type<BlockStartPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, BlockStartPayload> CODEC =
                StreamCodec.unit(new BlockStartPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 8. 格挡结束
    public record BlockStopPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("block_stop");
        public static final CustomPacketPayload.Type<BlockStopPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, BlockStopPayload> CODEC =
                StreamCodec.unit(new BlockStopPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ============ S2C 数据包定义 ============

    // 9. 跑墙状态
    public record WallRunStatePayload(boolean running, int wallSideOrdinal) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("wall_run_state");
        public static final CustomPacketPayload.Type<WallRunStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, WallRunStatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, WallRunStatePayload::running,
                        ByteBufCodecs.VAR_INT, WallRunStatePayload::wallSideOrdinal,
                        WallRunStatePayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 10. 幽灵行者标记
    public record AscendedStatePayload(boolean ascended) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("ascended_state");
        public static final CustomPacketPayload.Type<AscendedStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AscendedStatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, AscendedStatePayload::ascended,
                        AscendedStatePayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 11. 子弹时间状态
    public record BulletTimeStatePayload(boolean inBulletTime) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("bullet_time_state");
        public static final CustomPacketPayload.Type<BulletTimeStatePayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, BulletTimeStatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, BulletTimeStatePayload::inBulletTime,
                        BulletTimeStatePayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 12. 冲刺成功
    public record DashSuccessPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("dash_success");
        public static final CustomPacketPayload.Type<DashSuccessPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DashSuccessPayload> CODEC =
                StreamCodec.unit(new DashSuccessPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 13. 耐力同步
    public record StaminaPayload(float stamina) implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("stamina");
        public static final CustomPacketPayload.Type<StaminaPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, StaminaPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.FLOAT, StaminaPayload::stamina,
                        StaminaPayload::new
                );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 14. 完美格挡成功
    public record ParrySuccessPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("parry_success");
        public static final CustomPacketPayload.Type<ParrySuccessPayload> TYPE = new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ParrySuccessPayload> CODEC =
                StreamCodec.unit(new ParrySuccessPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ============ 注册方法 ============

    /**
     * 在 {@code Ghostrunner#onInitialize} 中调用，注册所有数据包类型。
     */
    public static void registerPayloads() {
        // C2S
        PayloadTypeRegistry.serverboundPlay().register(JumpOffWallPayload.TYPE, JumpOffWallPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DashPayload.TYPE, DashPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DashChargeStartPayload.TYPE, DashChargeStartPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DashChargeAimPayload.TYPE, DashChargeAimPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DashChargeReleasePayload.TYPE, DashChargeReleasePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AttackPayload.TYPE, AttackPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BlockStartPayload.TYPE, BlockStartPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BlockStopPayload.TYPE, BlockStopPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BulletTimeExitRequestPayload.TYPE, BulletTimeExitRequestPayload.CODEC);

        // S2C
        PayloadTypeRegistry.clientboundPlay().register(WallRunStatePayload.TYPE, WallRunStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AscendedStatePayload.TYPE, AscendedStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BulletTimeStatePayload.TYPE, BulletTimeStatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DashSuccessPayload.TYPE, DashSuccessPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StaminaPayload.TYPE, StaminaPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ParrySuccessPayload.TYPE, ParrySuccessPayload.CODEC);
    }
    // ============ C2S 数据包定义 ============

    // 15. 子弹时间提前退出请求
    public record BulletTimeExitRequestPayload() implements CustomPacketPayload {
        public static final Identifier ID = Ghostrunner.id("bullet_time_exit_request");
        public static final CustomPacketPayload.Type<BulletTimeExitRequestPayload> TYPE =
                new CustomPacketPayload.Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, BulletTimeExitRequestPayload> CODEC =
                StreamCodec.unit(new BulletTimeExitRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}