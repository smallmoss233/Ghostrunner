package ghostrunner.handler;

import ghostrunner.api.GhostrunnerStamina;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

public final class BlockHandler {

    private BlockHandler() {}

    /** 普通格挡每次消耗的耐力 */
    public static final float STAMINA_PER_BLOCK = 30.0f;

    /** 完美格挡远程时的耐力消耗 */
    public static final float STAMINA_PERFECT_PROJECTILE = 10.0f;

    public static final int PERFECT_PREPARE_TICKS = 6;
    public static final int PERFECT_WINDOW_TICKS = 10;
    public static final float PARRY_REFLECT_DAMAGE = 1.0f;

    public static boolean tryBlock(Player player, DamageSource source) {
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;

        if (!stamina.ghostrunner$isBlocking()) return false;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ItemTags.SWORDS)) {
            stamina.ghostrunner$setBlocking(false);
            return false;
        }

        int ticks = stamina.ghostrunner$getBlockTicks();

        if (ticks < PERFECT_PREPARE_TICKS) {
            return false;
        }

        boolean perfect = ticks <= PERFECT_PREPARE_TICKS + PERFECT_WINDOW_TICKS;
        boolean projectile = source.getDirectEntity() instanceof AbstractArrow;

        if (perfect) {
            if (projectile) {
                if (stamina.ghostrunner$getStamina() < STAMINA_PERFECT_PROJECTILE) {
                    stamina.ghostrunner$setBlocking(false);
                    return false;
                }
                stamina.ghostrunner$consumeStamina(STAMINA_PERFECT_PROJECTILE);
                playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                return true;
            } else {
                Entity attacker = source.getEntity();
                if (attacker instanceof LivingEntity livingAttacker
                        && player.level() instanceof ServerLevel serverLevel) {
                    livingAttacker.hurtServer(
                            serverLevel,
                            player.damageSources().playerAttack(player),
                            PARRY_REFLECT_DAMAGE);
                }
                playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                return true;
            }
        }

        // ============ 普通格挡 ============
        if (stamina.ghostrunner$getStamina() < STAMINA_PER_BLOCK) {
            stamina.ghostrunner$setBlocking(false);
            return false;
        }
        stamina.ghostrunner$consumeStamina(STAMINA_PER_BLOCK);
        playSound(player, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.2f);
        return true;
    }

    private static void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void forceCancel(Player player) {
        GhostrunnerStamina stamina = (GhostrunnerStamina) player;
        stamina.ghostrunner$setBlocking(false);
    }
}