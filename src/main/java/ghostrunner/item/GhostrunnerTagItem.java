package ghostrunner.item;

import ghostrunner.api.GhostrunnerState;
import mosslib.util.tooltip.ShiftTooltipInvoker;
import mosslib.util.tooltip.TooltipHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GhostrunnerTagItem extends Item {

    /** "飞升"伤害。19 = 9.5 颗心。 */
    public static final float ASCENSION_DAMAGE = 19.0F;

    public GhostrunnerTagItem(Properties properties) {
        super(properties);
    }

    // ================== 物品描述渲染 ==================
    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                TooltipDisplay displayComponent,
                                Consumer<Component> textConsumer,
                                TooltipFlag flag) {
        // 先收集到 List（因为 TooltipHelper 仍接收 List）
        List<Component> tooltip = new ArrayList<>();

        TooltipHelper.addWrappedTooltip(tooltip,
                Component.translatable("item.ghostrunner.ghostrunner_tag.tip"));

        ShiftTooltipInvoker.addShiftTooltip(tooltip,
                Component.translatable("item.ghostrunner.ghostrunner_tag.detail"));

        // 再统一交给 Consumer
        tooltip.forEach(textConsumer);
    }

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(user instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        if (GhostrunnerState.isGhostrunner(player)) {
            player.sendOverlayMessage(
                    Component.translatable("message.ghostrunner.already_ascended")
                            .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        // ================== 核心改造逻辑 ==================
        // 1. 打上标记
        ((GhostrunnerState.GhostrunnerStateAccessor) player).ghostrunner$setAscended(true);

        // 2. 施加"飞升"伤害，但不真的杀死
        player.setHealth(Math.max(1.0F, player.getHealth() - ASCENSION_DAMAGE));

        // 3. 播放改造特效
        playAscensionEffects(player);

        // 4. 发送消息（聊天栏）
        player.sendSystemMessage(
                Component.translatable("message.ghostrunner.ascended")
                        .withStyle(ChatFormatting.AQUA));

        // 5. 消耗物品（除非创造模式）
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    private void playAscensionEffects(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        // ---- 1. 多层次音效 ----
        level.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.5F);

        // ---- 2. 粒子效果 ----
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 80, 0.6, 1.2, 0.6, 0.15);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 50, 0.5, 1.0, 0.5, 0.1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 120, 0.8, 1.5, 0.8, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 150, 1.0, 2.0, 1.0, 0.25);
        // FLASH 是 ColorParticleOption 类型，用 SimpleParticleType 的 END_ROD 替代做闪光
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        // ---- 3. 状态效果 ----
        // 短暂失明和缓慢
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4, false, false, false));

        // 改造后的短暂强化
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4, false, false, false));
    }
}