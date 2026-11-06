package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.HeavenManifest.Session;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Choice;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Effect;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Pair;
import io.github.pinchan4273.reacademycraft.world.entity.DarkMatterBeetle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.Identifier;
import javax.annotation.Nullable;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.phys.Vec3;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingDamageEvent;
import io.github.pinchan4273.reacademycraft.event.AcademyLivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

/**
 * 天界発現の常時効果の解決と反映（§10、R07）。走査のたびに全領域から「対象ごと・効果ごとの最強の値と、その発生元」を作り直すので、強い領域が外れれば残りの最強へ自動で切り替わり、
 * 解除では外れた領域の分だけが消える。補正はすべて保存されない一時的なものなので、ログアウトや再起動の後に残らない。ポーションとは別の補正なので両方が効く（R03）。
 * <ul>
 * <li>移動: 移動速度と飛行速度の属性の乗算の補正（強化と阻害は別の補正）。</li>
 * <li>攻撃間隔: プレイヤーと属性を持つ実体は攻撃速度の属性（間隔×(1∓率)の逆数）。一般のモブは走っている{@link MeleeAttackGoal}・{@link RangedAttackGoal}・
 * {@link RangedBowAttackGoal}の待ちを進める・戻す（R07の「安全に識別できる通常攻撃」。脳で動くモブと他MODの独自の攻撃は対象外）。甲虫は{@link #scaledInterval}。</li>
 * <li>与ダメージ: 通常攻撃（飛び道具を含む、棘の反撃を除く）と甲虫の3攻撃。能力の攻撃には掛けない（D03）。{@link AcademyLivingHurtEvent}で防具の前に掛ける。</li>
 * <li>被ダメージ: {@link AcademyLivingDamageEvent}の高い優先度（防具・耐性の後で、未元装甲・白翼自動防御より前。§14の共通の順）。防御無視の刺突には軽減を掛けない。</li>
 * <li>再生・継続損傷: 1秒ごと。再生は実回復1HPにつき2CPを回復させた領域の使用者だけが払い、払えない領域は飛ばす（§14の修復の停止）。継続損傷は防御無視なし。</li>
 * </ul>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class HeavenEffects {
    private static final int COUNT = Effect.values().length;
    /** 属性の補正のID（Forge版は固定のUUID。1.21では名前）。 */
    private static final Identifier MOVE_UP_ID = Identifier.fromNamespaceAndPath("academy", "heaven_move_up"), MOVE_DOWN_ID = Identifier.fromNamespaceAndPath("academy", "heaven_move_down"),
            HASTE_ID = Identifier.fromNamespaceAndPath("academy", "heaven_haste"), DELAY_ID = Identifier.fromNamespaceAndPath("academy", "heaven_delay");

    /** 1つの対象に今効いているもの。 */
    private static final class Applied {
        final float[] value = new float[COUNT];
        final Session[] by = new Session[COUNT];
        final List<Session> regen = new ArrayList<>(1);
        @Nullable Vec3 lastPosition;
        float tempoCarry;
        void offer(Effect effect, float amount, Session session) {
            if (effect == Effect.REGEN) regen.add(session);
            if (amount <= value[effect.ordinal()]) return;
            value[effect.ordinal()] = amount; by[effect.ordinal()] = session;
        }
        float get(Effect effect) { return value[effect.ordinal()]; }
        void credit(Effect effect) { var session = by[effect.ordinal()]; if (session != null) session.useful = true; }
    }
    private static Map<LivingEntity, Applied> applied = new HashMap<>();

    private HeavenEffects() { }
    /** 効果を持つ対象がいるか（いなければ毎tickの処理を飛ばす）。 */
    static boolean busy() { return !applied.isEmpty(); }
    static void clear() { applied = new HashMap<>(); }

    // ---- 解決 ----

    /** 全領域から作り直し、補正を今の値へ合わせる。 */
    static void resolve() {
        var previous = applied;
        var next = new HashMap<LivingEntity, Applied>();
        for (var session : HeavenManifest.sessions()) {
            var settings = session.settings();
            float p = session.proficiency();
            for (var entity : session.inside) {
                var target = HeavenManifest.classify(session.player(), settings, entity);
                if (target == null) continue;
                for (var pair : Pair.values()) {
                    var choice = settings.choice(target, pair);
                    if (choice == Choice.OFF) continue;
                    var effect = Effect.of(pair, choice);
                    if (effect.harmful() ? !HeavenManifest.mayHarm(session, entity) : effect == Effect.REGEN && HeavenManifest.harmImmune.test(entity)) continue;
                    next.computeIfAbsent(entity, e -> new Applied()).offer(effect, effect.value(p), session);
                }
            }
        }
        next.forEach((entity, now) -> {
            var old = previous.get(entity);
            if (old != null) { now.lastPosition = old.lastPosition; now.tempoCarry = old.tempoCarry; }
            creditScan(entity, now);
            now.lastPosition = entity.position();
        });
        applied = next;
        var touched = new HashSet<>(previous.keySet());
        touched.addAll(next.keySet());
        for (var entity : touched) refresh(entity);
    }
    /** 走査で分かる「役立った」: 他者への弱体化と損傷が届いていること、味方が移動の強化で実際に動いたこと（§2。放置のONは数えない）。 */
    private static void creditScan(LivingEntity entity, Applied now) {
        for (var effect : Effect.values()) {
            var session = now.by[effect.ordinal()];
            if (session != null && effect.harmful() && entity != session.player()) session.useful = true;
        }
        if (now.by[Effect.MOVE_UP.ordinal()] != null && now.lastPosition != null && now.lastPosition.distanceToSqr(entity.position()) > .01) now.credit(Effect.MOVE_UP);
    }
    private static void modifier(LivingEntity entity, net.minecraft.core.Holder<Attribute> attribute, Identifier id, String name, double amount) {
        var instance = entity.getAttribute(attribute);
        if (instance == null) return;
        var current = instance.getModifier(id);
        if (amount == 0) { if (current != null) instance.removeModifier(id); return; }
        if (current != null && current.amount() == amount) return;
        if (current != null) instance.removeModifier(id);
        // 1.21の演算の名前: Forge版のMULTIPLY_TOTALはADD_MULTIPLIED_TOTAL。名前の文字列は1.21の補正には無い。
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    /** 補正を今の効果（常時効果と状態異常の鈍足）へ合わせる。 */
    static void refresh(LivingEntity entity) {
        var now = entity.isRemoved() ? null : applied.get(entity);
        float up = now == null ? 0 : now.get(Effect.MOVE_UP);
        float down = entity.isRemoved() ? 0 : Math.max(now == null ? 0 : now.get(Effect.MOVE_DOWN), HeavenPhenomena.value(entity, HeavenSettings.Phenomenon.SLOW));
        float haste = now == null ? 0 : now.get(Effect.HASTE), delay = now == null ? 0 : now.get(Effect.DELAY);
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            modifier(entity, attribute, MOVE_UP_ID, "academy heaven move up", up);
            modifier(entity, attribute, MOVE_DOWN_ID, "academy heaven move down", -down);
        }
        // 間隔×(1−率)にするので、速さは逆数。
        modifier(entity, Attributes.ATTACK_SPEED, HASTE_ID, "academy heaven haste", haste <= 0 ? 0 : 1 / (1 - haste) - 1);
        modifier(entity, Attributes.ATTACK_SPEED, DELAY_ID, "academy heaven delay", delay <= 0 ? 0 : 1 / (1 + delay) - 1);
    }

    // ---- 読み取り ----

    /** この対象の今の効果の値（無ければ0）。 */
    public static float value(LivingEntity entity, Effect effect) { var now = applied.get(entity); return now == null ? 0 : now.get(effect); }
    /** この対象の与ダメージの倍率（攻撃弱体は状態異常の弱体化と強い方だけ）。 */
    public static float attackFactor(LivingEntity attacker) {
        float down = Math.max(value(attacker, Effect.ATTACK_DOWN), HeavenPhenomena.value(attacker, HeavenSettings.Phenomenon.WEAK));
        return (1 + value(attacker, Effect.ATTACK_UP)) * (1 - down);
    }
    /** この損傷に掛ける被ダメージの倍率。管理の強制と奈落には掛けず、防御無視の刺突には軽減を掛けない。 */
    public static float takenFactor(LivingEntity victim, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return 1;
        float reduce = SkillDamageSource.ignoresDefenses(source) ? 0 : value(victim, Effect.DEFENSE_UP);
        return (1 - reduce) * (1 + value(victim, Effect.DEFENSE_DOWN));
    }
    /**
     * R07の攻撃間隔の式: 加速は元の間隔×(1−率)、遅延は元の間隔×(1＋率)、小数は切り上げ、最低1tick。甲虫の担当はここを通して間隔を決める（{@code DarkMatterBeetle}の
     * 近接・突進・砲撃の待ちを設定する所。他担当のファイルなのでまだ呼ばれていない）。
     */
    public static int scaledInterval(LivingEntity attacker, int base) {
        float factor = (1 - value(attacker, Effect.HASTE)) * (1 + value(attacker, Effect.DELAY));
        return factor == 1 ? base : Math.max(1, (int) Math.ceil(base * factor - 1e-4f));
    }
    /** 領域の倍率を掛ける攻撃者か: 通常攻撃（飛び道具を含む）と甲虫の攻撃。能力の攻撃・領域自身の損傷・棘は対象外（D03）。 */
    public static @Nullable LivingEntity dealer(DamageSource source) {
        if (source instanceof HeavenPhenomena.StatusSource || source.is(DamageTypes.THORNS)) return null;
        if (source instanceof SkillDamageSource skill)
            return skill.skill().equals(DarkMatter.BEETLE) && skill.getDirectEntity() instanceof DarkMatterBeetle beetle ? beetle : null;
        return source.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ---- 損傷の補正 ----

    @SubscribeEvent(priority = EventPriority.HIGH) public static void attack(AcademyLivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        var attacker = dealer(event.getSource());
        if (attacker == null) return;
        var now = applied.get(attacker);
        // 味方が戦闘で強化を使った（§2）。
        if (now != null) { now.credit(Effect.ATTACK_UP); now.credit(Effect.HASTE); }
        float factor = attackFactor(attacker);
        if (factor != 1) event.setAmount(event.getAmount() * factor);
    }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void damage(AcademyLivingDamageEvent event) {
        var victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        var now = applied.get(victim);
        if (now == null) return;
        float factor = takenFactor(victim, event.getSource());
        if (factor == 1) return;
        event.setAmount(event.getAmount() * factor);
        if (factor < 1) now.credit(Effect.DEFENSE_UP);
    }

    // ---- 毎tickと1秒ごと ----

    static void tick(long time) {
        boolean second = time % 20 == 0;
        for (var entry : List.copyOf(applied.entrySet())) {
            var entity = entry.getKey(); var now = entry.getValue();
            if (!entity.isAlive()) continue;
            if (entity instanceof Mob mob && (now.get(Effect.HASTE) > 0 || now.get(Effect.DELAY) > 0)) tempo(mob, now);
            if (second) pulse(entity, now);
        }
    }
    /** 再生と継続損傷の1秒分（試験からも呼ぶ）。 */
    public static void pulseForTest(LivingEntity entity) { var now = applied.get(entity); if (now != null) pulse(entity, now); }
    private static void pulse(LivingEntity entity, Applied now) {
        regen(entity, now);
        var hurter = now.by[Effect.DOT.ordinal()];
        if (hurter != null && hurter.alive()) {
            float amount = now.get(Effect.DOT);
            if (pulseDamage(entity, () -> SkillCombat.attackContact(hurter.player(), entity, HeavenManifest.ID, amount))) hurter.useful = true;
        }
    }
    /** 強い領域から順に、払える領域だけが回復させる（同じ回復を複数の使用者へ請求しない）。 */
    private static void regen(LivingEntity entity, Applied now) {
        if (now.regen.isEmpty() || entity.getHealth() >= entity.getMaxHealth()) return;
        var order = new ArrayList<>(now.regen);
        order.sort((a, b) -> Float.compare(Effect.REGEN.value(b.proficiency()), Effect.REGEN.value(a.proficiency())));
        for (var session : order) {
            if (!session.alive()) continue;
            float rate = Effect.REGEN.value(session.proficiency()) - beetleRepairRate(entity);
            float room = Math.min(rate, entity.getMaxHealth() - entity.getHealth());
            if (room <= 0) return; // 甲虫は自動修復の方が速い: 速い方の1つだけで、二重に回復・請求しない。
            if (!session.canPay(room * HeavenManifest.RECOVER_CP_PER_HP)) continue;
            float before = entity.getHealth();
            entity.heal(room);
            float healed = entity.getHealth() - before;
            if (healed > 0) { session.tryPay(healed * HeavenManifest.RECOVER_CP_PER_HP); session.useful = true; }
            return;
        }
    }
    /** 自動修復が今この甲虫へ戻している毎秒のHP。再生はこれを超える分だけを足す（§10: 速い方を適用）。 */
    private static float beetleRepairRate(LivingEntity entity) {
        if (!(entity instanceof DarkMatterBeetle beetle) || beetle.ownerId() == null || entity.level().getServer() == null) return 0;
        var owner = entity.level().getServer().getPlayerList().getPlayer(beetle.ownerId());
        var data = owner == null ? null : io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(owner).orElse(null);
        if (data == null || data.getBeetleRepair() != DarkMatterBeetles.Repair.AUTO || !data.canUseAbility()) return 0;
        return DarkMatterBeetles.repairPerSecond(data.getProficiency(DarkMatter.BEETLE));
    }
    /**
     * 領域の周期的な損傷を1回当てる。被ダメージの後の待ちで消えず、他の攻撃の待ちを変えず、吹き飛ばしを残さない。損傷が入ったらtrue。
     */
    static boolean pulseDamage(LivingEntity entity, BooleanSupplier hurt) {
        var motion = entity.getDeltaMovement();
        boolean impulse = entity.needsSync, marked = entity.hurtMarked; // 26.2: 1.21.1 の hasImpulse は needsSync
        int wait = entity.invulnerableTime;
        entity.invulnerableTime = 0;
        boolean done = hurt.getAsBoolean();
        entity.invulnerableTime = wait;
        entity.setDeltaMovement(motion);
        entity.needsSync = impulse; entity.hurtMarked = marked;
        return done;
    }

    // ---- 一般のモブの攻撃間隔（R07） ----

    /**
     * 待ちのフィールド。NeoForge 26.2は本番でもMojangの名前で動くので、Forge版のSRG（f_25548_・f_25760_・f_25786_）に当たるMojangの名前
     * （1.20.1の対応表で引いた）を使う。見つからなければその経路だけ何もしない。
     */
    private static final class Goals {
        static final @Nullable Field MELEE = find(MeleeAttackGoal.class, "ticksUntilNextAttack"), RANGED = find(RangedAttackGoal.class, "attackTime"), BOW = find(RangedBowAttackGoal.class, "attackTime");
        private static @Nullable Field find(Class<?> type, String name) {
            try { return ObfuscationReflectionHelper.findField(type, name); }
            catch (RuntimeException e) { return null; }
        }
    }
    /**
     * 間隔×factorになるよう、走っている攻撃の目標の待ちを進める・戻す。1tickに進むのは1/factorなので、余りを持ち越して整数だけ動かす。
     * 飛び道具の目標は0へ飛ばすと発射の判定を飛ばすので1で止める。
     */
    private static void tempo(Mob mob, Applied now) {
        float factor = (1 - now.get(Effect.HASTE)) * (1 + now.get(Effect.DELAY));
        now.tempoCarry += 1 / factor - 1;
        int whole = (int) now.tempoCarry;
        if (whole == 0) return;
        now.tempoCarry -= whole;
        mob.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).forEach(wrapped -> { // 1.20.1のgetRunningGoals
            var goal = wrapped.getGoal();
            if (goal instanceof MeleeAttackGoal) adjust(Goals.MELEE, goal, whole, 0);
            else if (goal instanceof RangedAttackGoal) adjust(Goals.RANGED, goal, whole, 1);
            else if (goal instanceof RangedBowAttackGoal<?>) adjust(Goals.BOW, goal, whole, 1);
        });
    }
    private static void adjust(@Nullable Field field, Object goal, int whole, int floor) {
        if (field == null) return;
        try {
            int wait = field.getInt(goal);
            if (wait > floor) field.setInt(goal, Math.max(floor, wait - whole));
        } catch (IllegalAccessException | IllegalArgumentException ignored) {
            // 読めなければ間隔を変えない（通常の間隔のまま）。
        }
    }
}
