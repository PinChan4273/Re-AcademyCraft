package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Forge 1.20.1版のLivingHurtEventに当たる、このmodのイベント。{@code actuallyHurt}の最初（無敵の判定の直後）、つまり盾の防御と
 * 無敵時間の差し引き（無敵時間中は前の被弾との差だけ）の後、防具・エンチャント・効果・衝撃吸収の前に出る。NeoForge 1.21.1には
 * この段階のイベントが無いので、mixinが出す（{@link DamagePhaseHooks}）。量を変えると防具の前のダメージが変わる。
 * 取り消すか量を0以下にすると、ダメージだけが無くなり、ノックバックなど被弾の残りは起きる（Forge版と同じ）。
 */
public final class AcademyLivingHurtEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private float amount;
    public AcademyLivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source; this.amount = amount;
    }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
}
