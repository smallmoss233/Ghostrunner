package ghostrunner.item;

import ghostrunner.api.GhostrunnerPlayer;
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

/**
 * 幽灵行者改造器。
 * <p>右键：给玩家打上"幽灵行者"标记，付出代价获得一套能力。
 * <p>使用代价：
 * <ul>
 *   <li>施加"飞升"伤害（不致死）</li>
 *   <li>短暂失明 + 缓慢 + 强化</li>
 * </ul>
 */
public class GhostrunnerTagItem extends Item {

    /** "飞升"伤害。19 = 9.5 颗心，把玩家打到 1 血但不杀死。 */
    public static final float ASCENSION_DAMAGE = 19.0F;

    public GhostrunnerTagItem(Properties properties) {
        super(properties);
    }

    // ================================================================
    //                          物品描述
    // ================================================================

    @Override
    public void appendHoverText(ItemStack stack,
                                TooltipContext context,
                                TooltipDisplay displayComponent,
                                Consumer<Component> textConsumer,
                                TooltipFlag flag) {
        // TooltipHelper / ShiftTooltipInvoker 仍接收 List，先收集再统一转交
        List<Component> tooltip = new ArrayList<>();

        TooltipHelper.addWrappedTooltip(tooltip,
                Component.translatable("item.ghostrunner.ghostrunner_tag.tip"));

        ShiftTooltipInvoker.addShiftTooltip(tooltip,
                Component.translatable("item.ghostrunner.ghostrunner_tag.detail"));

        tooltip.forEach(textConsumer);
    }

    // ================================================================
    //                          使用
    // ================================================================

    @Override
    public InteractionResult use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (!(user instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        if (GhostrunnerPlayer.isGhostrunner(player)) {
            player.sendOverlayMessage(
                    Component.translatable("message.ghostrunner.already_ascended")
                            .withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        ascend(player);
        playAscensionEffects(player);

        player.sendSystemMessage(
                Component.translatable("message.ghostrunner.ascended")
                        .withStyle(ChatFormatting.AQUA));

        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** 打标记 + 施加"飞升"伤害（不致死）。 */
    private static void ascend(ServerPlayer player) {
        GhostrunnerPlayer.of(player).ghostrunner$setAscended(true);
        player.setHealth(Math.max(1.0F, player.getHealth() - ASCENSION_DAMAGE));
    }

    // ================================================================
    //                          改造特效
    // ================================================================

    private void playAscensionEffects(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        // ---- 音效 ----
        level.playSound(null, player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.5F);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.5F);

        // ---- 粒子 ----
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 80, 0.6, 1.2, 0.6, 0.15);
        level.sendParticles(ParticleTypes.SCULK_SOUL,       x, y, z, 50, 0.5, 1.0, 0.5, 0.1);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,   x, y, z, 120, 0.8, 1.5, 0.8, 0.3);
        level.sendParticles(ParticleTypes.END_ROD,          x, y, z, 150, 1.0, 2.0, 1.0, 0.25);
        // FLASH 是 ColorParticleOption 类型，用 SimpleParticleType 的 END_ROD 替代
        level.sendParticles(ParticleTypes.END_ROD,          x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        // ---- 状态效果 ----
        // 短暂失明 + 缓慢：营造"义体改造"的短暂失能
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4, false, false, false));

        // 改造后的强化：奖励与"飞升"代价对冲
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 2, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 4, false, false, false));
    }
}