package io.github.pinchan4273.reacademycraft.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Forge 1.20.1版のLivingDamageEventに当たる、このmodのイベント（v1.5.0の未元物質人間・両防御・天界の被ダメージ）。
 * {@code actuallyHurt}の中の、防具・効果・エンチャントの後、<b>衝撃吸収（黄色いハート）を差し引いた後</b>、HPを減らす直前に出る。
 * NeoForge 26.2の{@code LivingDamageEvent.Pre}は衝撃吸収を差し引く前に出るので、Forge版と同じ所でmixinが出す（{@link DamagePhaseHooks}）。
 * 量を変えるとHPから引く量が変わる。取り消すとHPは減らない（Forge版の取消と同じ、量0）。
 */
public final class AcademyLivingDamageEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private float amount;
    public AcademyLivingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source; this.amount = amount;
    }
    public DamageSource getSource() { return source; }
    public float getAmount() { return amount; }
    public void setAmount(float amount) { this.amount = amount; }
}
