package ghostrunner.handler;

import ghostrunner.api.GhostrunnerPlayer;
import ghostrunner.config.GhostrunnerConfig;
import ghostrunner.data.GhostrunnerData;
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

/**
 * 格挡与完美格挡。
 * <p><b>设计目标</b>：
 * <ul>
 *   <li>不能靠无脑按住右键当无敌墙（耐力耗尽会自动退出）</li>
 *   <li>不能靠快速反复按右键刷完美窗口（释放后硬直 400ms）</li>
 *   <li>完美格挡需要预判敌人攻击时机，不是"进入就成功"</li>
 * </ul>
 * <p><b>时间驱动</b>：所有判定基于时间戳（毫秒），与 tick rate 无关。
 * <p><b>状态派生</b>：不存 phase 字段，{@link #currentPhase} 每次从时间戳算。
 */
public final class BlockHandler {

    private BlockHandler() {}

    // ================================================================
    //                          阶段
    // ================================================================

    /**
     * 格挡阶段。由时间戳派生，不存字段。
     * <ul>
     *   <li>{@link #IDLE}——未格挡，可随时重新进入</li>
     *   <li>{@link #PREPARE}——前摇，格挡不生效</li>
     *   <li>{@link #PARRY}——完美窗口，反伤 / 弹反</li>
     *   <li>{@link #BLOCKING}——普通格挡，消耗耐力</li>
     *   <li>{@link #RECOVERY}——释放后硬直，不能重新格挡</li>
     * </ul>
     */
    public enum Phase { IDLE, PREPARE, PARRY, BLOCKING, RECOVERY }

    /** 派生当前阶段。 */
    public static Phase currentPhase(GhostrunnerData data, long nowMs) {
        if (data.blockHeld) {
            long elapsed = nowMs - data.blockStartMs;
            if (elapsed < GhostrunnerConfig.BLOCK_PREPARE_MS) {
                return Phase.PREPARE;
            }
            if (elapsed < GhostrunnerConfig.BLOCK_PREPARE_MS + GhostrunnerConfig.BLOCK_PARRY_MS) {
                return Phase.PARRY;
            }
            return Phase.BLOCKING;
        }

        // 未按住：判断是否在硬直期
        if (nowMs - data.blockReleaseMs < GhostrunnerConfig.BLOCK_RECOVERY_MS) {
            return Phase.RECOVERY;
        }
        return Phase.IDLE;
    }

    // ================================================================
    //                          格挡判定
    // ================================================================

    /**
     * 尝试格挡。返回 true 表示伤害被完全挡下。
     */
    public static boolean tryBlock(Player player, DamageSource source) {
        GhostrunnerPlayer gr = GhostrunnerPlayer.of(player);
        GhostrunnerData data = gr.ghostrunner$data();

        // 未持剑 → 立即取消格挡
        if (!player.getMainHandItem().is(ItemTags.SWORDS)) {
            forceCancel(player);
            return false;
        }

        long now = System.currentTimeMillis();

        return switch (currentPhase(data, now)) {
            case IDLE, RECOVERY, PREPARE -> false;
            case PARRY -> handleParry(player, data, source);
            case BLOCKING -> handleNormalBlock(player, data);
        };
    }

    // ================================================================
    //                          完美格挡
    // ================================================================

    private static boolean handleParry(Player player, GhostrunnerData data, DamageSource source) {
        boolean projectile = source.getDirectEntity() instanceof AbstractArrow;

        if (projectile) {
            // 完美格挡远程：耗少量耐力，反弹
            if (data.stamina < GhostrunnerConfig.STAMINA_PERFECT_PROJECTILE) {
                forceCancel(player);
                return false;
            }
            data.stamina = Math.max(0f,
                    data.stamina - GhostrunnerConfig.STAMINA_PERFECT_PROJECTILE);
            playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            return true;
        }

        // 完美格挡近战：不耗耐力，反伤
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker
                && player.level() instanceof ServerLevel serverLevel) {
            livingAttacker.hurtServer(
                    serverLevel,
                    player.damageSources().playerAttack(player),
                    GhostrunnerConfig.PARRY_REFLECT_DAMAGE);
        }
        playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        return true;
    }

    // ================================================================
    //                          普通格挡
    // ================================================================

    private static boolean handleNormalBlock(Player player, GhostrunnerData data) {
        if (data.stamina < GhostrunnerConfig.STAMINA_PER_BLOCK) {
            forceCancel(player);
            return false;
        }
        data.stamina = Math.max(0f, data.stamina - GhostrunnerConfig.STAMINA_PER_BLOCK);
        playSound(player, SoundEvents.SHIELD_BLOCK.value(), 0.8f, 1.2f);
        return true;
    }

    // ================================================================
    //                          工具
    // ================================================================

    /** 强制取消格挡。直接改 data，绕过硬直检查（重生 / 卸载 / 断线用）。 */
    public static void forceCancel(Player player) {
        GhostrunnerData data = GhostrunnerPlayer.of(player).ghostrunner$data();
        if (!data.blockHeld) return;
        data.blockHeld = false;
        data.blockReleaseMs = System.currentTimeMillis();
    }

    private static void playSound(Player player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null,
                player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }
}