package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.skill.HeavenManifest.Session;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Frame;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Frequency;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Phenomenon;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings.Target;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * 天界発現の怪現象（§11）。領域全体につき1回の抽選で1対象・1現象で、範囲を広げても抽選の回数は増えない。
 * <p>
 * 抽選: 対象のいない現象とOFFの現象を外し、残った枠の重みを合計1へ直す。枠ごとの頻度fは間隔の倍率なので、枠iの発生の頻度は w_i／(間隔×f_i)。これを1回の抽選で満たすために、抽選の間隔を
 * 標準の間隔×(残った枠のfの最小)とし、枠iを w_i×(fの最小)／f_i の確率で選ぶ（残りの確率は何も起きない）。全枠が標準なら間隔どおりに必ず1つ起きて§11の平均の式に一致する。
 * 状態異常の枠が当たったら、適用可能でONの種類から等確率で選ぶ（種類ごとの頻度は枠の中の重み。低は1/2倍、高は2倍）。
 * <p>
 * 予兆は発生の直前の0.5秒（{@link #TELEGRAPH}）。追加費用は抽選の時と発生の直前に確かめ、足りない・セーフティなら消費せず見送る（領域は解除しない）。回復待ちを作らない。
 * <p>
 * 状態異常と発光はポーションに登録しない独自の状態で、メモリの中だけに置く（領域は再起動・再ログインで残らないので保存は要らない）。時間切れ、領域の解除、範囲外、設定のOFFで消える。
 * 同種の再付与は強度を重ねず残り時間を更新する。鈍足と弱体化は常時効果と強い方だけが効く（{@link HeavenEffects}）。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class HeavenPhenomena {
    /** 予兆の長さ（tick）。 */
    public static final int TELEGRAPH = 10;
    /** 領域の状態異常の損傷。通常攻撃の倍率（{@link HeavenEffects#dealer}）を掛けないために区別する。 */
    public static final class StatusSource extends DamageSource {
        StatusSource(Holder<DamageType> type, Entity causing) { super(type, causing); }
        /** この損傷を起こした技能（天界発現）。公開APIの{@code AcademySafety.skillOf}が答える。 */
        public net.minecraft.resources.Identifier skill() { return HeavenManifest.ID; }
    }
    /** 試験用の入口: 状態異常の損傷の源を作る（公開APIの技能の識別を確かめるため）。 */
    public static DamageSource statusSourceForTest(LivingEntity entity, Entity causing, ResourceKey<DamageType> type) {
        return new StatusSource(entity.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), causing);
    }
    /** 対象に付いている1つの状態。 */
    private static final class Status {
        final float value; final Session by; int left;
        Status(float value, int left, Session by) { this.value = value; this.left = left; this.by = by; }
    }
    /** 予兆中の現象。 */
    static final class Pending {
        final LivingEntity target; final Phenomenon phenomenon; int left = TELEGRAPH;
        Pending(LivingEntity target, Phenomenon phenomenon) { this.target = target; this.phenomenon = phenomenon; }
    }
    private static final Map<LivingEntity, EnumMap<Phenomenon, Status>> STATUSES = new HashMap<>();

    private HeavenPhenomena() { }

    // ---- 値 ----

    /** 標準の抽選の間隔（tick）。 */
    public static int interval(float p) { return Math.round(ArcGen.lerp(200, 100, p)); }
    public static int statusTicks(float p) { return Math.round(ArcGen.lerp(80, 160, p)); }
    public static int glowTicks(float p) { return Math.round(ArcGen.lerp(120, 280, p)); }
    /** 現象の発生の費用（CP）。 */
    public static float cost(Phenomenon phenomenon, float p) {
        return switch (phenomenon.frame()) {
            case STATUS -> ArcGen.lerp(40, 20, p);
            case GLOW -> ArcGen.lerp(20, 10, p);
            case LIGHTNING -> ArcGen.lerp(120, 60, p);
            case MUTATION -> ArcGen.lerp(200, 100, p);
        };
    }
    /**
     * 枠の選ばれる確率（順は{@link Frame}。残らなかった枠は0）。minの枠の頻度の倍率は{@link #drawScale}。合計が1未満なら残りは何も起きない。
     */
    public static Map<Frame, Double> chances(Map<Frame, Frequency> available) {
        var out = new EnumMap<Frame, Double>(Frame.class);
        if (available.isEmpty()) return out;
        double total = 0;
        for (var frame : available.keySet()) total += frame.weight();
        double fastest = drawScale(available);
        for (var entry : available.entrySet()) out.put(entry.getKey(), entry.getKey().weight() / total * fastest / entry.getValue().interval());
        return out;
    }
    /** 抽選の間隔の倍率（残った枠の頻度の倍率の最小）。 */
    public static float drawScale(Map<Frame, Frequency> available) {
        float fastest = Float.MAX_VALUE;
        for (var frequency : available.values()) fastest = Math.min(fastest, frequency.interval());
        return available.isEmpty() ? 1 : fastest;
    }

    // ---- 読み取り ----

    /** この対象の状態異常の強さ（無ければ0）。 */
    public static float value(LivingEntity entity, Phenomenon phenomenon) {
        var map = STATUSES.get(entity);
        var status = map == null ? null : map.get(phenomenon);
        return status == null ? 0 : status.value;
    }
    public static boolean has(LivingEntity entity, Phenomenon phenomenon) { var map = STATUSES.get(entity); return map != null && map.containsKey(phenomenon); }
    public static int left(LivingEntity entity, Phenomenon phenomenon) {
        var map = STATUSES.get(entity);
        var status = map == null ? null : map.get(phenomenon);
        return status == null ? 0 : status.left;
    }
    static boolean busy() { return !STATUSES.isEmpty(); }

    // ---- 抽選 ----

    /** この現象を、この対象へ今起こせるか（ON・対象区分・保護・種類ごとの適用可否）。 */
    public static boolean applicable(Session session, Phenomenon phenomenon, LivingEntity entity) {
        var target = HeavenManifest.classify(session.player(), session.settings(), entity);
        if (target == null || !session.settings().enabled(phenomenon, target)) return false;
        if (phenomenon.harmful() && !HeavenManifest.mayHarm(session, entity)) return false;
        return switch (phenomenon) {
            case BURN -> !entity.fireImmune() && !entity.hasEffect(MobEffects.FIRE_RESISTANCE);
            case POISON -> entity.canBeAffected(new MobEffectInstance(MobEffects.POISON));
            case WITHER -> entity.canBeAffected(new MobEffectInstance(MobEffects.WITHER));
            case DARK -> entity instanceof Player;
            case MUTATION -> HeavenMutation.eligible(entity);
            default -> true;
        };
    }
    /** 今の範囲の中で、現象ごとの候補を作る。 */
    private static Map<Phenomenon, List<LivingEntity>> candidates(Session session) {
        var out = new EnumMap<Phenomenon, List<LivingEntity>>(Phenomenon.class);
        for (var entity : session.inside) {
            if (!entity.isAlive() || !session.area.contains(entity)) continue;
            for (var phenomenon : Phenomenon.values())
                if (applicable(session, phenomenon, entity)) out.computeIfAbsent(phenomenon, p -> new ArrayList<>()).add(entity);
        }
        return out;
    }
    /** 1回の抽選。選ばれたら予兆を始める。 */
    static void draw(Session session) {
        var settings = session.settings();
        var random = session.player().getRandom();
        var candidates = candidates(session);
        var available = new EnumMap<Frame, Frequency>(Frame.class);
        for (var phenomenon : candidates.keySet()) available.put(phenomenon.frame(), settings.frameFrequency(phenomenon.frame()));
        session.drawIn = Math.max(1, Math.round(interval(session.proficiency()) * drawScale(available)));
        if (available.isEmpty()) return;
        double roll = random.nextDouble();
        Frame chosen = null;
        for (var entry : chances(available).entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) { chosen = entry.getKey(); break; }
        }
        if (chosen == null) return;
        var phenomenon = pick(settings, candidates, chosen, random.nextDouble());
        if (phenomenon == null) return;
        var list = candidates.get(phenomenon);
        var target = list.get(random.nextInt(list.size()));
        if (!session.canPay(cost(phenomenon, session.proficiency()))) return; // 見送り。領域は解除しない。
        session.pending = new Pending(target, phenomenon);
        HeavenVisuals.telegraph(session, target);
    }
    /** 枠の中の現象を選ぶ。状態異常は等確率（種類の頻度を枠の中の重みとする）。 */
    static @Nullable Phenomenon pick(HeavenSettings settings, Map<Phenomenon, List<LivingEntity>> candidates, Frame frame, double roll) {
        double total = 0;
        for (var phenomenon : candidates.keySet()) if (phenomenon.frame() == frame) total += 1 / settings.frequency(phenomenon).interval();
        double at = roll * total;
        Phenomenon last = null;
        for (var phenomenon : candidates.keySet()) {
            if (phenomenon.frame() != frame) continue;
            last = phenomenon;
            at -= 1 / settings.frequency(phenomenon).interval();
            if (at < 0) return phenomenon;
        }
        return last;
    }
    /** 領域ごとの毎tick: 予兆の時間と、次の抽選までの時間を進める。 */
    static void tick(Session session) {
        if (session.pending != null && --session.pending.left <= 0) {
            var pending = session.pending;
            session.pending = null;
            fire(session, pending.target, pending.phenomenon);
        }
        if (--session.drawIn <= 0) draw(session);
    }
    /**
     * 現象を発生させる（予兆の後。試験からも呼ぶ）。対象がまだ有効で、費用を払えたら起こす。起きたらtrue。
     */
    public static boolean fire(Session session, LivingEntity target, Phenomenon phenomenon) {
        if (!target.isAlive() || !session.alive() || !session.area.contains(target) || !applicable(session, phenomenon, target)) return false;
        float p = session.proficiency();
        float cost = cost(phenomenon, p);
        if (!session.canPay(cost)) return false;
        boolean done = switch (phenomenon) {
            case GLOW -> { apply(target, phenomenon, 1, glowTicks(p), session); yield true; }
            case LIGHTNING -> {
                float damage = phenomenon.value(p);
                yield HeavenEffects.pulseDamage(target, () -> SkillCombat.attackContact(session.player(), target, HeavenManifest.ID, damage));
            }
            case MUTATION -> HeavenMutation.mutate(target, session);
            default -> { apply(target, phenomenon, phenomenon.value(p), statusTicks(p), session); yield true; }
        };
        if (!done) return false;
        session.tryPay(cost);
        if (target != session.player()) session.useful = true;
        HeavenVisuals.occurred(session, target, phenomenon);
        return true;
    }

    // ---- 状態異常 ----

    private static void apply(LivingEntity target, Phenomenon phenomenon, float value, int ticks, Session session) {
        STATUSES.computeIfAbsent(target, e -> new EnumMap<>(Phenomenon.class)).put(phenomenon, new Status(value, ticks, session));
        if (phenomenon == Phenomenon.DARK) {
            // 視界の変更は既存の技能由来の盲目の同期を使う（強度は共通。ポーションではない）。
            DirectEffects.remove(target, HeavenManifest.ID);
            DirectEffects.add(target, DirectEffects.Kind.BLINDNESS, 0, ticks, HeavenManifest.ID, session.player());
        }
        if (phenomenon == Phenomenon.SLOW) HeavenEffects.refresh(target);
    }
    /** 全対象の状態を1tick進める。 */
    static void tickStatuses() {
        if (STATUSES.isEmpty()) return;
        for (var entry : List.copyOf(STATUSES.entrySet())) {
            var entity = entry.getKey();
            var map = entry.getValue();
            if (!entity.isAlive()) { STATUSES.remove(entity); continue; }
            for (var phenomenon : List.copyOf(map.keySet())) {
                var status = map.get(phenomenon);
                if (--status.left <= 0 || !stillValid(entity, phenomenon, status)) { remove(entity, map, phenomenon); continue; }
                act(entity, phenomenon, status);
                if (!entity.isAlive()) break;
            }
            if (map.isEmpty()) STATUSES.remove(entity);
        }
    }
    private static boolean stillValid(LivingEntity entity, Phenomenon phenomenon, Status status) {
        var session = status.by;
        if (!session.alive() || !session.area.contains(entity)) return false;
        var target = HeavenManifest.classify(session.player(), session.settings(), entity);
        return target != null && session.settings().enabled(phenomenon, target) && (!phenomenon.harmful() || !HeavenManifest.harmImmune.test(entity));
    }
    private static void remove(LivingEntity entity, Map<Phenomenon, Status> map, Phenomenon phenomenon) {
        map.remove(phenomenon);
        if (phenomenon == Phenomenon.DARK) DirectEffects.remove(entity, HeavenManifest.ID);
        if (phenomenon == Phenomenon.SLOW) HeavenEffects.refresh(entity);
        if (phenomenon == Phenomenon.GLOW) HeavenVisuals.glowEnded(entity);
    }
    private static void act(LivingEntity entity, Phenomenon phenomenon, Status status) {
        switch (phenomenon) {
            case FLOAT -> {
                // 移動はこのtickの速度で行われ、重力はその後に引かれるので、毎tick置けばちょうどこの速さで上がる。
                Vec3 motion = entity.getDeltaMovement();
                entity.setDeltaMovement(motion.x, status.value / 20, motion.z);
                entity.fallDistance = 0;
                if (entity instanceof ServerPlayer) entity.hurtMarked = true;
            }
            case BURN -> { if (status.left % 20 == 0) burn(entity, status); }
            case POISON -> { if (status.left % 20 == 0) poison(entity, status); }
            case WITHER -> { if (status.left % 20 == 0) wither(entity, status); }
            case GLOW -> { if (status.left % 20 == 0) HeavenVisuals.glowRefresh(entity, status.by.player()); }
            default -> { }
        }
    }
    private static DamageSource source(Status status, LivingEntity entity, ResourceKey<DamageType> type) {
        return new StatusSource(entity.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), status.by.player());
    }
    /** 炎のダメージ。炎耐性を尊重し（火炎の種類なのでバニラの判定も効く）、地形へ延焼しない（実際の火は付けない）。 */
    private static void burn(LivingEntity entity, Status status) {
        if (entity.fireImmune() || entity.hasEffect(MobEffects.FIRE_RESISTANCE)) return;
        HeavenEffects.pulseDamage(entity, () -> entity.hurtServer((net.minecraft.server.level.ServerLevel) entity.level(), source(status, entity, DamageTypes.ON_FIRE), status.value));
    }
    /** 毒は通常の生物の1HPで止まる。 */
    private static void poison(LivingEntity entity, Status status) {
        float amount = Math.min(status.value, entity.getHealth() - 1);
        if (amount <= 0) return;
        HeavenEffects.pulseDamage(entity, () -> entity.hurtServer((net.minecraft.server.level.ServerLevel) entity.level(), source(status, entity, DamageTypes.MAGIC), amount));
    }
    /** 衰弱は致死可能。 */
    private static void wither(LivingEntity entity, Status status) {
        HeavenEffects.pulseDamage(entity, () -> entity.hurtServer((net.minecraft.server.level.ServerLevel) entity.level(), source(status, entity, DamageTypes.WITHER), status.value));
    }
    /** 試験用の入口: 状態の周期の損傷を今すぐ1回与える。 */
    public static void actForTest(LivingEntity entity, Phenomenon phenomenon) {
        var map = STATUSES.get(entity);
        var status = map == null ? null : map.get(phenomenon);
        if (status == null) return;
        switch (phenomenon) {
            case BURN -> burn(entity, status);
            case POISON -> poison(entity, status);
            case WITHER -> wither(entity, status);
            default -> act(entity, phenomenon, status);
        }
    }
    /** 試験用の入口: 状態を今すぐ1tick進める。 */
    public static void tickStatusesForTest() { tickStatuses(); }
    /** 試験用の入口: 今の範囲で1回抽選する。 */
    public static void drawForTest(Session session) { draw(session); }
    /** 試験用の入口: 予兆中の現象（無ければnull）。 */
    public static @Nullable Phenomenon pendingForTest(Session session) { return session.pending == null ? null : session.pending.phenomenon; }
    /** 試験用の入口: 次の抽選までのtick。 */
    public static int drawInForTest(Session session) { return session.drawIn; }

    /** 変異の前後で、同じ個体の状態を移す。 */
    static void move(LivingEntity from, LivingEntity to) {
        var map = STATUSES.remove(from);
        if (map == null) return;
        STATUSES.put(to, map);
        HeavenEffects.refresh(to);
    }
    static void clear() { STATUSES.clear(); }
    /** 再接続の時に、前の接続の領域の暗闇を残さない（時間制の直接効果は保存されるので）。 */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { DirectEffects.remove(e.getEntity(), HeavenManifest.ID); }
}
