package ghostrunner.mixin;

import ghostrunner.api.GhostrunnerState;
import ghostrunner.handler.BlockHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @ModifyVariable(
            method = "damage",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private float ghostrunner$amplifyDamage(float amount, DamageSource source) {
        if (amount <= 0) return amount;
        if (amount == Float.MAX_VALUE) return amount;

        LivingEntity self = (LivingEntity) (Object) this;

        // 幽灵行者被攻击
        if (self instanceof PlayerEntity victim && GhostrunnerState.isGhostrunner(victim)) {
            // 格挡检查
            if (BlockHandler.tryBlock(victim)) {
                return 0;   // 完全格挡
            }
            return Float.MAX_VALUE;
        }

        // 幽灵行者攻击别人
        if (source.getAttacker() instanceof PlayerEntity attacker
                && GhostrunnerState.isGhostrunner(attacker)) {
            return Float.MAX_VALUE;
        }

        return amount;
    }
}