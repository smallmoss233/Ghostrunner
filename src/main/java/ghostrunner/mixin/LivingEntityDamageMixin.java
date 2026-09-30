package ghostrunner.mixin;

import ghostrunner.api.GhostrunnerState;
import ghostrunner.handler.BlockHandler;
import ghostrunner.handler.BulletTimeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void ghostrunner$handleDamage(ServerLevel level, DamageSource source, float damage,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (damage <= 0) return;

        LivingEntity self = (LivingEntity) (Object) this;

        // ---- 幽灵行者被攻击 ----
        if (self instanceof Player victim && GhostrunnerState.isGhostrunner(victim)) {

            // ★ 子弹时间中 / 刚释放子弹时间 → 免疫摔落
            if (source.is(DamageTypeTags.IS_FALL)
                    && BulletTimeManager.shouldImmuneFall(victim)) {
                cir.setReturnValue(false);
                return;
            }

            // 格挡判定
            if (BlockHandler.tryBlock(victim, source)) {
                cir.setReturnValue(false);
                return;
            }

            // 一击必杀
            victim.setHealth(0);
            victim.die(source);
            cir.setReturnValue(true);
            return;
        }

        // ---- 幽灵行者攻击别人 ----
        if (source.getEntity() instanceof Player attacker
                && GhostrunnerState.isGhostrunner(attacker)
                && self != attacker) {
            self.setHealth(0);
            self.die(source);
            cir.setReturnValue(true);
        }
    }
}