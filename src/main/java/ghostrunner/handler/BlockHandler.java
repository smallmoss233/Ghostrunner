package ghostrunner.handler;

import ghostrunner.api.GhostrunnerStamina;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public final class BlockHandler {

    private BlockHandler() {}

    /** 普通格挡每次消耗的耐力 */
    public static final float STAMINA_PER_BLOCK = 30.0f;

    /** 完美格挡远程时的耐力消耗 */
    public static final float STAMINA_PERFECT_PROJECTILE = 10.0f;

    /**
     * 格挡前摇（tick）。按下右键后这段时间内**格挡完全不生效**。
     * <p>6 tick = 0.3 秒。攻击会直接命中。
     */
    public static final int PERFECT_PREPARE_TICKS = 6;

    /**
     * 完美格挡窗口长度（tick）。前摇结束后的这段时间内可以完美格挡。
     * <p>10 tick = 0.5 秒。
     */
    public static final int PERFECT_WINDOW_TICKS = 10;

    /** 完美格挡近战反伤值（会被一击必杀 Mixin 放大到致命） */
    public static final float PARRY_REFLECT_DAMAGE = 1.0f;

    /**
     * 尝试格挡。返回 true 表示伤害被完全挡下。
     */
    public static boolean tryBlock(PlayerEntity player, DamageSource source) {
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;

        // 不在格挡状态
        if (!stamina.ghostrunner$isBlocking()) return false;

        // 手持剑类武器才能格挡
        ItemStack mainHand = player.getMainHandStack();
        if (!mainHand.isIn(ItemTags.SWORDS)) {
            stamina.ghostrunner$setBlocking(false);
            return false;
        }

        int ticks = stamina.ghostrunner$getBlockTicks();

        // ★ 前摇期：格挡未生效，攻击直接穿透
        if (ticks < PERFECT_PREPARE_TICKS) {
            return false;
        }

        // 完美格挡窗口判定
        boolean perfect = ticks <= PERFECT_PREPARE_TICKS + PERFECT_WINDOW_TICKS;
        boolean projectile = source.getSource() instanceof PersistentProjectileEntity;

        if (perfect) {
            if (projectile) {
                // 完美格挡远程：耐力消耗降到 10
                if (stamina.ghostrunner$getStamina() < STAMINA_PERFECT_PROJECTILE) {
                    stamina.ghostrunner$setBlocking(false);
                    return false;
                }
                stamina.ghostrunner$consumeStamina(STAMINA_PERFECT_PROJECTILE);
                playSound(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                return true;
            } else {
                // 完美格挡近战：不耗耐力 + 反伤攻击者
                Entity attacker = source.getAttacker();
                if (attacker instanceof LivingEntity livingAttacker) {
                    livingAttacker.damage(
                            player.getDamageSources().playerAttack(player),
                            PARRY_REFLECT_DAMAGE);
                }
                playSound(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                return true;
            }
        }

        // ============ 普通格挡 ============
        if (stamina.ghostrunner$getStamina() < STAMINA_PER_BLOCK) {
            stamina.ghostrunner$setBlocking(false);
            return false;
        }
        stamina.ghostrunner$consumeStamina(STAMINA_PER_BLOCK);
        playSound(player, SoundEvents.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);
        return true;
    }

    private static void playSound(PlayerEntity player,
                                  net.minecraft.sound.SoundEvent sound,
                                  float volume, float pitch) {
        player.getWorld().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                sound, SoundCategory.PLAYERS, volume, pitch);
    }

    /** 强制取消格挡。 */
    public static void forceCancel(PlayerEntity player) {
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;
        stamina.ghostrunner$setBlocking(false);
    }
}