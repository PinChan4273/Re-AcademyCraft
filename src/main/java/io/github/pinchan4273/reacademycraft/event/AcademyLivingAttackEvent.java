package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Forge 1.20.1版のLivingAttackEventに当たる、このmodのイベント。被弾の処理の最初（{@code LivingEntity.hurt}の最初、プレイヤーは
 * {@code Player.hurt}の最初）に、無敵・クリエイティブ・死亡・火炎耐性・盾・無敵時間のどの判定より前に出る。NeoForge 1.21.1の
 * LivingIncomingDamageEventはそれらの判定の後なので、Forge版と同じ所でmixinが出す（{@link DamagePhaseHooks}）。
 * 取り消すと被弾そのものが無くなる（{@code hurt}がfalseを返す。Forge版と同じ）。
 */
public final class AcademyLivingAttackEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private final float amount;
    public AcademyLivingAttackEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source; this.amount = amount;
    }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
}
