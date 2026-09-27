package ghostrunner.item;

import ghostrunner.api.GhostrunnerState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class GhostrunnerTagItem extends Item {

    /** "飞升"伤害。20 = 10 颗心。 */
    public static final float ASCENSION_DAMAGE = 19.0F;

    public GhostrunnerTagItem(Settings settings) {
        super(settings.maxCount(1));
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

        // 打上标记
        ((GhostrunnerState.GhostrunnerStateAccessor) player).ghostrunner$setAscended(true);

        // 施加"飞升"伤害，但不真的杀死
        player.setHealth(Math.max(1.0F, player.getHealth() - ASCENSION_DAMAGE));
        player.getWorld().playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 1.0F, 0.8F);

        player.sendMessage(Text.translatable("message.ghostrunner.ascended")
                .formatted(Formatting.AQUA), false);

        // 消耗物品（除非创造模式）
        if (!player.isCreative()) {
            stack.decrement(1);
        }

        return TypedActionResult.success(stack);
    }
}