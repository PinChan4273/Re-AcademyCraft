package io.github.pinchan4273.reacademycraft.mixin;

import io.github.pinchan4273.reacademycraft.event.DamagePhaseHooks;
import java.util.Stack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 1.20.1版がLivingEntityに当てたパッチと同じ所で、このmodの攻撃・被弾のイベントを出す（{@link DamagePhaseHooks}）。
 * プレイヤーの{@code hurt}と{@code actuallyHurt}は{@link PlayerDamageMixin}が扱う（Forge版のonLivingAttackもプレイヤーを飛ばした）。
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityDamageMixin {
    @Shadow protected Stack<DamageContainer> damageContainers;

    /** Forge版: {@code hurt}の最初の{@code if (!ForgeHooks.onLivingAttack(...)) return false;}。 */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void academy$attack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player) return;
        if (DamagePhaseHooks.attackCancelled((LivingEntity) (Object) this, source, amount)) cir.setReturnValue(false);
    }

    /** Forge版: {@code actuallyHurt}の無敵の判定の直後の{@code onLivingHurt}（NeoForgeのcontainerを最初に読む所）。 */
    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Ljava/util/Stack;peek()Ljava/lang/Object;", ordinal = 0), cancellable = true)
    private void academy$hurt(DamageSource source, float amount, CallbackInfo ci) {
        if (DamagePhaseHooks.hurtCancelled((LivingEntity) (Object) this, source, damageContainers.peek())) ci.cancel();
    }
}
