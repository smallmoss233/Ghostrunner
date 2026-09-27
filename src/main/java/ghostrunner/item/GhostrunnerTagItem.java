package ghostrunner.item;

import ghostrunner.api.GhostrunnerState;
import mosslib.util.tooltip.ShiftTooltipInvoker;
import mosslib.util.tooltip.TooltipHelper;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GhostrunnerTagItem extends Item {

    /** "飞升"伤害。20 = 10 颗心。 */
    public static final float ASCENSION_DAMAGE = 19.0F;

    public GhostrunnerTagItem(Settings settings) {
        super(settings.maxCount(1));
    }

    // ================== 物品描述渲染 ==================
    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        // 1. 常驻描述（利用 * 自动换行）
        Text longDescription = Text.translatable("item.ghostrunner.ghostrunner_tag.tip");
        TooltipHelper.addWrappedTooltip(tooltip, longDescription);

        // 2. 按住 Shift 展开的详情（同样支持 * 换行）
        ShiftTooltipInvoker.addShiftTooltip(tooltip,
                Text.translatable("item.ghostrunner.ghostrunner_tag.detail")
        );
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()) return TypedActionResult.success(stack);

        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.pass(stack);
        }

        if (GhostrunnerState.isGhostrunner(player)) {
            player.sendMessage(Text.translatable("message.ghostrunner.already_ascended")
                    .formatted(Formatting.YELLOW), true);
            return TypedActionResult.fail(stack);
        }

        // ================== 核心改造逻辑 ==================
        // 1. 打上标记
        ((GhostrunnerState.GhostrunnerStateAccessor) player).ghostrunner$setAscended(true);

        // 2. 施加"飞升"伤害，但不真的杀死
        player.setHealth(Math.max(1.0F, player.getHealth() - ASCENSION_DAMAGE));

        // 3. 播放改造特效（音效、粒子、状态效果）
        playAscensionEffects(player);

        // 4. 发送消息
        player.sendMessage(Text.translatable("message.ghostrunner.ascended")
                .formatted(Formatting.AQUA), false);

        // 5. 消耗物品（除非创造模式）
        if (!player.isCreative()) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack);
    }

    private void playAscensionEffects(ServerPlayerEntity player) {
        ServerWorld serverWorld = player.getServerWorld();
        double x = player.getX();
        double y = player.getY() + 1.0;
        double z = player.getZ();

        // ---- 1. 多层次音效 ----
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0F, 0.5F);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0F, 0.8F);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 0.5F);
        serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 1.0F, 0.5F);

        // ---- 2. 粒子效果 ----
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 80, 0.6, 1.2, 0.6, 0.15);
        serverWorld.spawnParticles(ParticleTypes.SCULK_SOUL, x, y, z, 50, 0.5, 1.0, 0.5, 0.1);
        serverWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 120, 0.8, 1.5, 0.8, 0.3);
        serverWorld.spawnParticles(ParticleTypes.END_ROD, x, y, z, 150, 1.0, 2.0, 1.0, 0.25);
        serverWorld.spawnParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        // ---- 3. 状态效果（修复报错：1.20.1 版本直接传入 StatusEffects.XXX） ----
        // 短暂失明和缓慢，模拟改造过程中的“感官剥夺”
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 4, false, false, false));

        // 改造完成后，获得短暂的高速与跳跃提升
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 2, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 200, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4, false, false, false));
    }
}