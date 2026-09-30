package ghostrunner.network;

import mosslib.api.PayloadRegistrar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class GhostrunnerNetworking {

    private GhostrunnerNetworking() {}

    private static final PayloadRegistrar REGISTRAR = new PayloadRegistrar();

    // ================================================================
    //                      C2S 数据包
    // ================================================================

    /** 简单动作包。6 个无参数 C2S 请求合并。 */
    public record ActionPayload(Action action) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC =
                StreamCodec.composite(
                        Action.STREAM_CODEC, ActionPayload::action,
                        ActionPayload::new);

        public static final Type<ActionPayload> TYPE =
                REGISTRAR.c2s("action", ActionPayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public enum Action {
            JUMP_OFF_WALL,
            DASH_CHARGE_RELEASE,
            ATTACK,
            BLOCK_START,
            BLOCK_STOP,
            BULLET_TIME_EXIT_REQUEST;

            private static final Action[] VALUES = values();

            public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC =
                    StreamCodec.of(
                            (buf, action) -> buf.writeByte(action.ordinal()),
                            buf -> VALUES[buf.readByte()]);
        }
    }

    public record MovePayload(Context context,
                              boolean forward, boolean back,
                              boolean left, boolean right) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, MovePayload> CODEC =
                StreamCodec.composite(
                        Context.STREAM_CODEC, MovePayload::context,
                        ByteBufCodecs.BOOL, MovePayload::forward,
                        ByteBufCodecs.BOOL, MovePayload::back,
                        ByteBufCodecs.BOOL, MovePayload::left,
                        ByteBufCodecs.BOOL, MovePayload::right,
                        MovePayload::new);

        public static final Type<MovePayload> TYPE =
                REGISTRAR.c2s("move", MovePayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public enum Context {
            DASH,
            CHARGE_START,
            CHARGE_AIM;

            private static final Context[] VALUES = values();

            public static final StreamCodec<RegistryFriendlyByteBuf, Context> STREAM_CODEC =
                    StreamCodec.of(
                            (buf, ctx) -> buf.writeByte(ctx.ordinal()),
                            buf -> VALUES[buf.readByte()]);
        }
    }

    // ================================================================
    //                      S2C 数据包
    // ================================================================

    public record NoticePayload(Notice notice) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, NoticePayload> CODEC =
                StreamCodec.composite(
                        Notice.STREAM_CODEC, NoticePayload::notice,
                        NoticePayload::new);

        public static final Type<NoticePayload> TYPE =
                REGISTRAR.s2c("notice", NoticePayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public enum Notice {
            DASH_SUCCESS,
            PARRY_SUCCESS;

            private static final Notice[] VALUES = values();

            public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC =
                    StreamCodec.of(
                            (buf, notice) -> buf.writeByte(notice.ordinal()),
                            buf -> VALUES[buf.readByte()]);
        }
    }

    public record WallRunStatePayload(boolean running, int wallSideOrdinal) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, WallRunStatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, WallRunStatePayload::running,
                        ByteBufCodecs.VAR_INT, WallRunStatePayload::wallSideOrdinal,
                        WallRunStatePayload::new);

        public static final Type<WallRunStatePayload> TYPE =
                REGISTRAR.s2c("wall_run_state", WallRunStatePayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AscendedStatePayload(boolean ascended) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, AscendedStatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, AscendedStatePayload::ascended,
                        AscendedStatePayload::new);

        public static final Type<AscendedStatePayload> TYPE =
                REGISTRAR.s2c("ascended_state", AscendedStatePayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BulletTimeStatePayload(boolean active, float stamina) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, BulletTimeStatePayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, BulletTimeStatePayload::active,
                        ByteBufCodecs.FLOAT, BulletTimeStatePayload::stamina,
                        BulletTimeStatePayload::new);

        public static final Type<BulletTimeStatePayload> TYPE =
                REGISTRAR.s2c("bullet_time_state", BulletTimeStatePayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record StaminaPayload(float stamina) implements CustomPacketPayload {

        public static final StreamCodec<RegistryFriendlyByteBuf, StaminaPayload> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.FLOAT, StaminaPayload::stamina,
                        StaminaPayload::new);

        public static final Type<StaminaPayload> TYPE =
                REGISTRAR.s2c("stamina", StaminaPayload.CODEC);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ================================================================
    //                      注册
    // ================================================================

    @SuppressWarnings("unused")
    private static final Object[] TOUCH_PAYLOADS = {
            ActionPayload.TYPE,
            MovePayload.TYPE,
            NoticePayload.TYPE,
            WallRunStatePayload.TYPE,
            AscendedStatePayload.TYPE,
            BulletTimeStatePayload.TYPE,
            StaminaPayload.TYPE,
    };

    public static void registerPayloads() {
        REGISTRAR.commit();
    }
}