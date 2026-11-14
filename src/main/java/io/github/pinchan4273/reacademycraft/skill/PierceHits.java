package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * 1.5.2: 貫通の打撃（{@code #academy:hit/pierce}）を、他の Mod の守りに止めさせない仕組み。
 *
 * <p><b>追跡の範囲。</b> 貫通の打撃は、必ず{@link #hurt}を通して当てる。ここで「被害者・ダメージの元」の組を積み、{@code Entity.hurt}が返るまでの
 * 間だけ追跡する。見分けは被害者とダメージの元の同一性（{@code ==}）なので、入れ子の被弾（反射の打ち返しなど）、同じ tick の別の打撃、
 * 貫通でない打撃とは混ざらない。追跡していない打撃には、何もしない。</p>
 *
 * <p><b>貫通の段（1.5.2）。</b> 段は打撃ごとに{@link SkillDamageSource#pierceTier}で決まる。
 * 段 2 の打撃は追跡しないので、下の一覧の4つ（3つの最後の段と、死亡の取り消しを外す所）のどれでも外さない。段 3 の打撃は追跡し、攻撃・計算・被ダメージの
 * 最後の段で外すが、死亡の取り消しは外さない。段 4 の打撃では、4つとも外す。Re 自身の防御の扱いは、段で変わらない。</p>
 *
 * <p><b>他の Mod の守りを外す所（どれも追跡中の貫通の打撃だけ）。</b> 下の3つは、全 MOD の読み込みの後に、各イベントの最低優先度の最後へ登録する
 * （{@link DeathOrder}と同じ作り。取り消されたイベントも受ける）。</p>
 * <ul>
 * <li>{@link LivingAttackEvent}: 取り消しを外す（Re 自身の防御が取り消したときは、取り消したまま）。</li>
 * <li>{@link LivingHurtEvent}: 取り消しを外し、量を「Re の防御だけを通した量」より小さくさせない（他の Mod が減らした分を戻す。増やした分は残す）。</li>
 * <li>{@link LivingDamageEvent}: 同じく取り消しと減らした量を戻し、他の Mod が下げた被ダメージ後の待ち（{@code invulnerableTime}）も戻す。</li>
 * <li>{@link LivingDeathEvent}: {@link DeathOrder}の最初に{@link #death}を呼び、他の Mod の死の取り消しを外して体力を0にする。Re 自身の死の判断
 * （未元物質人間の人体生成）は、その後に今までどおり行う。</li>
 * </ul>
 *
 * <p><b>Re 自身の防御を残す作り。</b> Re の防御・補正のリスナー（ベクトル反射・偏向、ライト・シールド、ジェットの前面、翼の防御（1.5.2 で未元装甲は廃止）、
 * 未元物質人間、能力の干渉、天界発現の補正、直接効果の耐性、放射のマーカー）は、{@link #attack}・{@link #hurtStage}・{@link #damageStage}を通して動く。
 * 追跡中の打撃では、他の Mod が取り消していても一時的に外して本体を動かし、本体には「Re の防御だけを通した量」（他の Mod の軽減の前の量）を見せる。
 * 本体が取り消したら「Re が取り消した」と覚え、最後の段もそれを外さない。本体が量を変えたら、その量を Re の量として持ち、最後の段はそれより下を戻すだけなので、
 * Re の防御が減らした分は残る。追跡していない打撃では、取り消されていれば本体を動かさない（以前の{@code receiveCanceled = false}と同じ）。</p>
 *
 * <p><b>他の Mod の貫通にも Re の防御を残す（1.5.2）。</b> 追跡していない打撃（他の Mod や本体のダメージ）では、
 * Re の防御・補正のリスナーの本体が攻撃を取り消したり量を減らしたりしたら、そのイベントのインスタンスごとに「Re が決めた結果」（取り消し、Re の最後のリスナーの後の量）を覚え、
 * 上の最後の段で、それが崩されていたら（取り消しが外された、量が Re の後の量より大きくされた）かけ直す。計算の段で Re が減らしたときは、
 * 計算の段の最後の量を被害者とダメージの元の組で覚え、被ダメージの段（別のイベント）でもそれより大きくさせない。他の Mod が、Re の貫通と同じ作り（最後に取り消しを外す・
 * 量を戻す）で Re の防御の結果を消しても、Re の防御の分は残る。白翼刺突（Re の防御も通る）では、Re の防御の本体が何もしないので、かけ直す物も無い。
 * Re の防御が働いた後に、他の Mod が後の段で量を増やした分は、Re の後の量までに抑えられる（区別できないため）。
 * 同じく「最後に登録する」作りの他の Mod とは、登録の順が決まらないので、完全には保証できない。</p>
 *
 * <p><b>保証の外。</b> 守りを外せるのは Forge のイベントを通る物だけ。mixin・{@code hurt}などの上書き・体力の直接の操作で守る物、
 * これより後に登録されたリスナーは外せない。土地の保護・PvP 禁止の Mod の取り消しも外れる。他の Mod は名指ししない。</p>
 */
public final class PierceHits {
    /** 追跡中の1回の被弾。 */
    private static final class Hit {
        final LivingEntity victim; final DamageSource source; final float requested;
        /** 貫通の段（1.5.2）。段 3 か段 4（段 2 の打撃はスタックに積まない）。 */
        final SkillDamageSource.PierceTier tier;
        /** 攻撃のイベントの量（変えられない）。計算の段の Re の量の始まり。 */
        float attack = Float.NaN;
        /** Re 自身の防御が取り消した。 */
        boolean reCanceled;
        /** 計算の段の、Re の防御だけを通した量。 */
        float hurt = Float.NaN;
        /** 被ダメージの段の、Re の防御だけを通した量。 */
        float damage = Float.NaN;
        /** 計算の段の最後に見た被ダメージ後の待ち。 */
        int invulnerable = -1;
        Hit(LivingEntity victim, DamageSource source, float requested, SkillDamageSource.PierceTier tier) {
            this.victim = victim; this.source = source; this.requested = requested; this.tier = tier;
        }
        float hurtBase() {
            if (Float.isNaN(hurt)) hurt = Float.isNaN(attack) ? requested : attack;
            return hurt;
        }
    }
    /** 追跡中の被弾（サーバーの主スレッドだけで使う）。先頭が一番内側。 */
    private static final ArrayDeque<Hit> STACK = new ArrayDeque<>();
    /** 追跡していない打撃で、Re の防御が決めた結果（イベントのインスタンスごと。最後の段で取り除く。途中で失われたイベントは弱い参照で消える）。 */
    private static final class Decision {
        boolean canceled, defended;
        float last = Float.NaN;
    }
    private static final Map<Event, Decision> DECISIONS = new WeakHashMap<>();
    private static Decision decision(Event event) { return DECISIONS.computeIfAbsent(event, e -> new Decision()); }
    /**
     * 追跡していない打撃で、計算の段で Re の防御が量を減らしたときの、計算の段の最後の量（被害者とダメージの元の組ごと）。被ダメージの段は別のイベントなので、
     * 他の Mod が被ダメージの段で量を攻撃のときの量へ戻しても、この量より大きくさせない（防具・吸収は減らすだけなので、ふつうの量はこれ以下）。
     * 攻撃の段の最後で、同じ組の前の値を消す（同じダメージの元を使い回すバニラの環境のダメージでも、前の被弾と混ざらない）。
     */
    private static final Map<LivingEntity, Map<DamageSource, Float>> CARRY = new WeakHashMap<>();
    private static final Consumer<LivingAttackEvent> ATTACK_LAST = PierceHits::attackLast;
    private static final Consumer<LivingHurtEvent> HURT_LAST = PierceHits::hurtLast;
    private static final Consumer<LivingDamageEvent> DAMAGE_LAST = PierceHits::damageLast;
    private static boolean registered;

    private PierceHits() { }

    /** 最低優先度の最後へ登録する（1度だけ）。{@link DeathOrder#register}から、全 MOD の読み込みの後に呼ぶ。 */
    static synchronized void register() {
        if (registered) return;
        registered = true;
        add();
    }
    /** テスト用の入口: 登録し直して最後へ回す（テストの中で後から登録した「他の Mod の守り」のモックより後にする）。 */
    static synchronized void moveLastForTest() {
        MinecraftForge.EVENT_BUS.unregister(ATTACK_LAST);
        MinecraftForge.EVENT_BUS.unregister(HURT_LAST);
        MinecraftForge.EVENT_BUS.unregister(DAMAGE_LAST);
        add();
        registered = true;
    }
    private static void add() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingAttackEvent.class, ATTACK_LAST);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingHurtEvent.class, HURT_LAST);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDamageEvent.class, DAMAGE_LAST);
    }

    /**
     * 貫通の打撃を当てる入口。被害者が生き物で、ダメージの元が貫通なら、この1回の{@code hurt}の間だけ追跡する。吸収（衝撃吸収）もこの1回の間だけ0にして
     * 体へ通す（後で戻す。1.5.1 の白翼刺突と同じ）。クリエイティブなどの本当の無敵と{@code isInvulnerableTo}（無敵の設定の生き物など）には当てない。
     * 攻撃できるか（PvP・チーム・設定）は、呼ぶ側が先に確かめる（{@link SkillCombat#mayAttack}など）。
     * <p>1.5.2: 段 2 の打撃（{@link SkillDamageSource#pierceTier}）は、吸収を0にするだけで追跡しない。ほかの MOD の取り消し・軽減・死亡の取り消しは
     * 貫通でない打撃と同じく残り、Re の防御も追跡していない打撃と同じ作りで動く。</p>
     */
    public static boolean hurt(Entity target, DamageSource source, float amount) {
        var victim = victimOf(target);
        if (victim == null || victim.level().isClientSide || !SkillDamageSource.isPierce(source)) return target.hurt(source, amount);
        if (target.isInvulnerableTo(source) || victim.isInvulnerableTo(source) || victim instanceof Player p && p.getAbilities().invulnerable) return false;
        float absorption = victim.getAbsorptionAmount();
        if (absorption > 0) victim.setAbsorptionAmount(0);
        var tier = SkillDamageSource.pierceTier(source);
        var hit = tier.overridesModGuards() ? new Hit(victim, source, amount, tier) : null;
        if (hit != null) STACK.push(hit);
        try {
            return target.hurt(source, amount);
        } finally {
            if (hit != null) STACK.remove(hit);
            if (absorption > 0 && victim.isAlive()) victim.setAbsorptionAmount(absorption);
        }
    }
    /** ダメージを受け止める生き物（部位のエンティティは、その持ち主）。 */
    private static @Nullable LivingEntity victimOf(Entity target) {
        if (target instanceof LivingEntity living) return living;
        if (target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent) return parent;
        return null;
    }
    /** この被害者とダメージの元の組を、今追跡しているか（テストからも呼ぶ）。 */
    public static boolean tracking(LivingEntity victim, DamageSource source) { return find(victim, source) != null; }
    private static @Nullable Hit find(LivingEntity victim, DamageSource source) {
        if (STACK.isEmpty()) return null;
        for (var hit : STACK) if (hit.victim == victim && hit.source == source) return hit;
        return null;
    }

    // ---- Re 自身の防御・補正のリスナーの入口 ----

    /** Re の攻撃のイベントのリスナーの本体を動かす（リスナーは{@code receiveCanceled = true}で登録する）。 */
    public static void attack(LivingAttackEvent event, Consumer<LivingAttackEvent> body) {
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null) {
            if (event.isCanceled()) return;
            body.accept(event);
            if (event.isCanceled()) decision(event).canceled = true;
            return;
        }
        if (hit.reCanceled) return;
        boolean other = event.isCanceled();
        event.setCanceled(false);
        body.accept(event);
        if (event.isCanceled()) hit.reCanceled = true;
        else if (other) event.setCanceled(true);
    }
    /** Re の計算のイベントのリスナーの本体を動かす。本体には、Re の防御だけを通した量を見せる。 */
    public static void hurtStage(LivingHurtEvent event, Consumer<LivingHurtEvent> body) {
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null) { untracked(event, event::getAmount, body); return; }
        if (hit.reCanceled) return;
        boolean other = event.isCanceled();
        float otherAmount = event.getAmount();
        float before = hit.hurtBase();
        event.setCanceled(false);
        event.setAmount(before);
        body.accept(event);
        if (event.isCanceled()) { hit.reCanceled = true; return; }
        hit.hurt = event.getAmount();
        // 他の Mod の後のリスナーには、Re が掛けた割合を他の Mod の量へ掛けて見せる（最後の段で Re の量より下は戻す）。
        event.setAmount(before > 0 ? otherAmount * (hit.hurt / before) : hit.hurt);
        if (other) event.setCanceled(true);
    }
    /** Re の被ダメージのイベントのリスナーの本体を動かす。本体には、Re の防御だけを通した量を見せる。 */
    public static void damageStage(LivingDamageEvent event, Consumer<LivingDamageEvent> body) {
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null) { untracked(event, event::getAmount, body); return; }
        if (hit.reCanceled) return;
        boolean other = event.isCanceled();
        float otherAmount = event.getAmount();
        if (Float.isNaN(hit.damage)) hit.damage = Float.isNaN(hit.hurt) ? hit.hurtBase() : hit.hurt;
        float before = hit.damage;
        event.setCanceled(false);
        event.setAmount(before);
        body.accept(event);
        if (event.isCanceled()) { hit.reCanceled = true; return; }
        hit.damage = event.getAmount();
        event.setAmount(before > 0 ? otherAmount * (hit.damage / before) : hit.damage);
        if (other) event.setCanceled(true);
    }

    /**
     * 追跡していない打撃での Re の本体: 取り消されていれば動かさない（以前と同じ）。動かした後、取り消したか、量を減らしたら、Re の決めた結果として覚える。
     * 覚えた後は、Re の後のリスナー（増やす物を含む）の後の量を、Re の後の量として持ち直す。
     */
    private static <E extends Event> void untracked(E event, java.util.function.Supplier<Float> amount, Consumer<E> body) {
        if (event.isCanceled()) return;
        float before = amount.get();
        body.accept(event);
        if (event.isCanceled()) { decision(event).canceled = true; return; }
        float after = amount.get();
        var decision = after < before ? decision(event) : DECISIONS.get(event);
        if (decision == null) return;
        if (after < before) decision.defended = true;
        decision.last = after;
    }

    // ---- 最後の段（貫通では他の Mod の守りを外し、追跡していない打撃では Re の防御の結果をかけ直す） ----

    private static void attackLast(LivingAttackEvent event) {
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null) {
            var carried = CARRY.get(event.getEntity());
            if (carried != null && carried.remove(event.getSource()) != null && carried.isEmpty()) CARRY.remove(event.getEntity());
            var decision = DECISIONS.remove(event);
            if (decision != null && decision.canceled) event.setCanceled(true);
            return;
        }
        hit.attack = event.getAmount();
        event.setCanceled(hit.reCanceled);
    }
    private static void hurtLast(LivingHurtEvent event) {
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null) {
            var decision = reapply(event, event.getAmount(), event::setAmount);
            if (decision != null && decision.defended && !event.isCanceled())
                CARRY.computeIfAbsent(event.getEntity(), e -> new java.util.IdentityHashMap<>()).put(event.getSource(), event.getAmount());
            return;
        }
        if (hit.reCanceled) { event.setCanceled(true); return; }
        event.setCanceled(false);
        float re = hit.hurtBase();
        if (!(event.getAmount() >= re)) event.setAmount(re);
        hit.hurt = event.getAmount();
        hit.invulnerable = event.getEntity().invulnerableTime;
    }
    private static void damageLast(LivingDamageEvent event) {
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null) {
            var carried = CARRY.get(event.getEntity());
            Float cap = carried == null ? null : carried.remove(event.getSource());
            if (carried != null && carried.isEmpty()) CARRY.remove(event.getEntity());
            if (cap != null && !(event.getAmount() <= cap)) event.setAmount(cap);
            reapply(event, event.getAmount(), event::setAmount);
            return;
        }
        if (hit.reCanceled) { event.setCanceled(true); return; }
        event.setCanceled(false);
        float re = Float.isNaN(hit.damage) ? (Float.isNaN(hit.hurt) ? hit.hurtBase() : hit.hurt) : hit.damage;
        if (!(event.getAmount() >= re)) event.setAmount(re);
        var victim = event.getEntity();
        if (hit.invulnerable > victim.invulnerableTime) victim.invulnerableTime = hit.invulnerable;
    }
    /** 追跡していない打撃: Re の防御が決めた結果が崩されていたら、かけ直す（取り消し、Re の後の量より大きい量）。 */
    private static @Nullable Decision reapply(Event event, float amount, java.util.function.Consumer<Float> set) {
        var decision = DECISIONS.remove(event);
        if (decision == null) return null;
        if (decision.canceled) { event.setCanceled(true); return decision; }
        if (decision.defended && !(amount <= decision.last)) set.accept(decision.last);
        return decision;
    }
    /** テスト用: 覚えている Re の防御の結果の数（最後の段で取り除かれているか）。 */
    public static int decisionsForTest() { return DECISIONS.size(); }
    /**
     * 死の判断の最初（{@link DeathOrder}が呼ぶ）: 追跡中の貫通の打撃で、他の Mod が死を取り消していたら、取り消しを外して体力を0にする。
     * バニラの不死のトーテムは、この前（{@code LivingEntity.hurt}の中）に本体が使うので、ここへは来ない。
     * 1.5.2: 外すのは段 4 の打撃だけ。段 3 の打撃では、ほかの MOD の死亡の取り消しを残す。
     */
    static void death(LivingDeathEvent event) {
        if (!event.isCanceled()) return;
        var hit = find(event.getEntity(), event.getSource());
        if (hit == null || !hit.tier.overridesModDeathCancel()) return;
        event.setCanceled(false);
        event.getEntity().setHealth(0);
    }
}
