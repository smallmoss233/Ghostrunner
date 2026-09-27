package ghostrunner.mixin;

import ghostrunner.api.GhostrunnerState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    /**
     * 一击必杀：
     *  - 幽灵行者玩家受到任何伤害 → 伤害放大到必死
     *  - 幽灵行者玩家攻击任何生物 → 伤害放大到必死
     */
    @ModifyVariable(
            method = "damage",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private float ghostrunner$amplifyDamage(float amount, DamageSource source) {
        if (amount <= 0) return amount;
        if (amount == Float.MAX_VALUE) return amount; // 已经致命

        LivingEntity self = (LivingEntity) (Object) this;

        // 幽灵行者玩家被攻击
        if (self instanceof PlayerEntity victim && GhostrunnerState.isGhostrunner(victim)) {
            return Float.MAX_VALUE;
        }

        // 幽灵行者玩家攻击别人
        if (source.getAttacker() instanceof PlayerEntity attacker
                && GhostrunnerState.isGhostrunner(attacker)) {
            return Float.MAX_VALUE;
        }

        return amount;
    }
}