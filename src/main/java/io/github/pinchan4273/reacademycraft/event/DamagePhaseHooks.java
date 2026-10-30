package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

/**
 * Forge 1.20.1版の{@code ForgeHooks.onLivingAttack}・{@code onPlayerAttack}・{@code onLivingHurt}に当たる。mixin
 * （{@code mixin/LivingEntityDamageMixin}・{@code PlayerDamageMixin}）が、Forge版のパッチと同じ所から呼ぶ。
 * NeoForgeのダメージの量は{@link DamageContainer}が正なので、被弾のイベントの結果はそこへ書き戻す。
 */
public final class DamagePhaseHooks {
    private DamagePhaseHooks() { }

    /** Forge版onLivingAttack・onPlayerAttack。取り消されたらtrue（呼び出し元の{@code hurt}はfalseを返す）。 */
    public static boolean attackCancelled(LivingEntity entity, DamageSource source, float amount) {
        return NeoForge.EVENT_BUS.post(new AcademyLivingAttackEvent(entity, source, amount)).isCanceled();
    }

    /**
     * Forge版onLivingHurtと、その直後の{@code if (amount <= 0) return;}。残りのダメージをcontainerへ書き、何も残らなければtrue
     * （呼び出し元の{@code actuallyHurt}はそこで終わる）。
     */
    public static boolean hurtCancelled(LivingEntity entity, DamageSource source, DamageContainer container) {
        var event = NeoForge.EVENT_BUS.post(new AcademyLivingHurtEvent(entity, source, container.getNewDamage()));
        float amount = event.isCanceled() ? 0 : event.getAmount();
        container.setNewDamage(Math.max(0, amount));
        return amount <= 0;
    }
}
