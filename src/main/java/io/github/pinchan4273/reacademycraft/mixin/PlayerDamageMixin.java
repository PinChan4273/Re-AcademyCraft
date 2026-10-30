package io.github.pinchan4273.reacademycraft.mixin;

import io.github.pinchan4273.reacademycraft.event.DamagePhaseHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 1.20.1版がPlayerに当てたパッチと同じ所で、このmodの攻撃・被弾のイベントを出す（{@link DamagePhaseHooks}）。
 * サーバーのプレイヤーでは、{@code ServerPlayer.hurt}の判定（無敵・出現直後の無敵・PvP）の後、クリエイティブの無敵の判定の前になる（Forge版と同じ）。
 */
@Mixin(Player.class)
abstract class PlayerDamageMixin extends LivingEntity {
    private PlayerDamageMixin(EntityType<? extends LivingEntity> type, Level level) { super(type, level); }

    /** Forge版: {@code hurt}の最初の{@code if (!ForgeHooks.onPlayerAttack(...)) return false;}。 */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void academy$attack(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (DamagePhaseHooks.attackCancelled(this, source, amount)) cir.setReturnValue(false);
    }

    /** Forge版: {@code actuallyHurt}の無敵の判定の直後の{@code onLivingHurt}（NeoForgeのcontainerを最初に読む所）。 */
    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Ljava/util/Stack;peek()Ljava/lang/Object;", ordinal = 0), cancellable = true)
    private void academy$hurt(DamageSource source, float amount, CallbackInfo ci) {
        if (DamagePhaseHooks.hurtCancelled(this, source, this.damageContainers.peek())) ci.cancel();
    }
}
