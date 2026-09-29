package ghostrunner.handler;

import ghostrunner.api.GhostrunnerStamina;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public final class BlockHandler {

    private BlockHandler() {}

    /** 每次格挡消耗的耐力 */
    public static final float STAMINA_PER_BLOCK = 30.0f;

    /**
     * 处理"玩家受到伤害时的格挡判定"。
     * <p>返回 true 表示伤害被完全格挡。
     */
    public static boolean tryBlock(PlayerEntity player) {
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;

        // 不在格挡状态 → 放行
        if (!stamina.ghostrunner$isBlocking()) return false;

        // 手持剑类武器才能格挡
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isIn(ItemTags.SWORDS)) {
            stamina.ghostrunner$setBlocking(false);
            return false;
        }

        // 耐力不足 → 格挡失败（破防）
        if (stamina.ghostrunner$getStamina() < STAMINA_PER_BLOCK) {
            stamina.ghostrunner$setBlocking(false);
            return false;
        }

        // 消耗耐力
        stamina.ghostrunner$consumeStamina(STAMINA_PER_BLOCK);

        // 音效
        player.getWorld().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_SHIELD_BLOCK,
                SoundCategory.PLAYERS, 0.8f, 1.2f);

        return true;
    }

    /** 强制取消格挡（死亡 / 切换物品 / 断开连接）。 */
    public static void forceCancel(PlayerEntity player) {
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;
        stamina.ghostrunner$setBlocking(false);
    }
}