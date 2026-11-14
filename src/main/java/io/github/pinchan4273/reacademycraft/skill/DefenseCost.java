package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * 1.5.2 防御の部類の値段。
 * <ul>
 * <li>基本の維持: 0。技能が動いている間の CP はかからない（1.5.1 と同じく、オン・待機は無料）。
 *     維持の枠（{@link #startUpkeep}）は、開始の過負荷の確保のためだけに残す。Jet Engine の前面は Jet の総額に含む。</li>
 * <li>防御が実際に働いた攻撃1回ごとに、CP ＝ 回数の分(p) ＋ 防いだダメージ × 係数(p) と、過負荷(p) を払う。
 *     偏向・白翼自動防御・ライト・シールド・反射の CP は 1.5.1 の白翼自動防御と同じ「防いだ1HPにつき0.25」
 *     （回数の分は0）。</li>
 * <li>支払いは動いているモードの経路（{@link PlayerAbilityData#consumeRunning}）: CPの回復待ちはやり直さず、技能の倍率
 *     （cp_consume_speed・overload_consume_speed）を掛け、払った分だけ最大CPが伸びる。過負荷は増えたときだけ、
 *     熟練度で短くなる過負荷の回復待ちを作る。</li>
 * <li>CPが足りない、またはCPセーフティに掛かるときは払わず、その1回は防がない（セーフティは作動する。今までの防御と同じ）。</li>
 * <li>偏向・白翼自動防御・ライト・シールド・反射の1回の払いは「稼働」の払い（{@link PlayerAbilityData#payAction}）。
 *     防いでから過負荷の回復待ちが終わるまでは稼働中で、過負荷は回復せず、同時に稼働している技能の数にも入る。1回の値は上のとおり
 *     （同時稼働の倍率だけ掛かる）。ジェット・エンジンの前面は飛行の一部なので、今までどおりの払い。</li>
 * <li>おまけの効果（反射の打ち返しと向け直し、ライト・シールドの接触、偏向の飛び道具止め）は、起きたときに別に払う。</li>
 * </ul>
 */
public final class DefenseCost {
    private DefenseCost() { }

    /** 防御の技能ごとの値（熟練度0% → 100%。CP は 1.5.1 の1HPにつき0.25）。 */
    public enum Kind {
        /** ジェット・エンジンの前からの攻撃の軽減（10% → 20%）。 */
        JET_FRONT(6, 3, 2, 1, 0, 0, 0, 0),
        /** ベクトル偏向（40% → 90% × 解析度）。 */
        DEVIATION(0, 0, CP_PER_HP, CP_PER_HP, 3, 1, 30, 14),
        /** 白翼自動防御（40% → 100%）。CP は 1.5.1 のまま。 */
        GUARD(0, 0, CP_PER_HP, CP_PER_HP, 4, 1.5f, 40, 20),
        /** ライト・シールド（一般30%、原子崩しの通常ビームは100%）。 */
        LIGHT_SHIELD(0, 0, CP_PER_HP, CP_PER_HP, 4, 1.5f, 40, 20),
        /** ベクトル反射（0.6 → 1.2 × 解析度）。 */
        REFLECTION(0, 0, CP_PER_HP, CP_PER_HP, 6, 2, 80, 40);
        private final float countFrom, countTo, rateFrom, rateTo, overloadFrom, overloadTo, reserveFrom, reserveTo;
        /** 1回の過負荷は熟練度100%でも0にしない。開始の過負荷（確保）は 1.5.1 の開始の値。 */
        Kind(float countFrom, float countTo, float rateFrom, float rateTo, float overloadFrom, float overloadTo, float reserveFrom, float reserveTo) {
            this.countFrom = countFrom; this.countTo = countTo; this.rateFrom = rateFrom; this.rateTo = rateTo;
            this.overloadFrom = overloadFrom; this.overloadTo = overloadTo; this.reserveFrom = reserveFrom; this.reserveTo = reserveTo;
        }
        /** 開始で払い、オンの間確保する過負荷（技能の倍率の前）。 */
        public float reserve(float p) { return ArcGen.lerp(reserveFrom, reserveTo, clamp(p)); }
        /** 回数の分（CP）。 */
        public float count(float p) { return ArcGen.lerp(countFrom, countTo, clamp(p)); }
        /** 係数（防いだダメージ1あたりのCP）。 */
        public float rate(float p) { return ArcGen.lerp(rateFrom, rateTo, clamp(p)); }
        /** 1回の過負荷。 */
        public float overload(float p) { return ArcGen.lerp(overloadFrom, overloadTo, clamp(p)); }
        /** 1回のCP（技能の倍率の前）: 回数の分 ＋ 防いだダメージ × 係数。 */
        public float cp(float p, float blocked) { return count(p) + Math.max(0, blocked) * rate(p); }
    }

    private static float clamp(float p) { return Math.max(0, Math.min(1, p)); }

    /** 防いだダメージ1あたりの CP（1.5.1 の白翼自動防御の値。熟練度に依らない）。 */
    public static final float CP_PER_HP = .25f;
    /** 基本の維持の、G0に対する割合（熟練度0% → 100%）。値は0。 */
    public static final float UPKEEP_SHARE_AT_ZERO = 0, UPKEEP_SHARE_AT_FULL = 0;
    /** 基本の維持の、G0に対する割合（技能の倍率の前）。 */
    public static float upkeepShare(float p) { return ArcGen.lerp(UPKEEP_SHARE_AT_ZERO, UPKEEP_SHARE_AT_FULL, clamp(p)); }
    /**
     * 防御の技能の基本の維持を始める。技能は、動いている間これを保ち、外れたら（{@link PlayerAbilityData#upkeepHeld}が false）止まる。
     * 維持できない所（読み取り専用、能力無し、未習得）では null。免除（クリエイティブ）は何も払わない。
     */
    public static PlayerAbilityData.@Nullable Upkeep startUpkeep(PlayerAbilityData data, ResourceLocation skill, BooleanSupplier exempt) {
        var upkeep = data.startUpkeep(skill, exempt);
        if (upkeep == null) return null;
        upkeep.g0Share(UPKEEP_SHARE_AT_ZERO, UPKEEP_SHARE_AT_FULL);
        // 開始の過負荷を払い、オンの間それを確保する。払えない（ロック中など）なら始めない。
        var kind = kindOf(skill);
        if (kind != null && !data.reserveOverload(upkeep, kind.reserve(data.getProficiency(skill)), exempt.getAsBoolean())) {
            data.endUpkeep(upkeep);
            return null;
        }
        return upkeep;
    }
    /** 防御の技能の種類（確保の値を引くため）。防御の部類でなければ null。 */
    public static @Nullable Kind kindOf(ResourceLocation skill) {
        if (DarkMatter.GUARD.equals(skill)) return Kind.GUARD;
        if (LightShield.ID.equals(skill)) return Kind.LIGHT_SHIELD;
        if (VecDeviation.ID.equals(skill)) return Kind.DEVIATION;
        if (VecReflection.ID.equals(skill)) return Kind.REFLECTION;
        return null;
    }

    /**
     * 値段に使う「防いだダメージ」（ふつうの攻撃）。防いだ量をそのまま使い、上限は無い（量で値段が飛ぶ所を作らない）。
     * 有限でない量は値段も有限でなくなり、払えないので防がない。
     */
    public static float priced(LivingEntity victim, float blocked) { return priced(victim, null, blocked); }

    /**
     * 値段に使う「防いだダメージ」。無敵を貫くダメージ（{@code /kill}・奈落。タグ{@code bypasses_invulnerability}）だけは、防いだ量と
     * 受ける側の体力＋衝撃吸収の小さい方で数える（失うはずの命の分。{@code /kill}の{@code Float.MAX_VALUE}も払える値段になる）。
     * それ以外は{@link #priced(LivingEntity, float)}と同じ（量で分けない）。
     * 有限でない量（無限大）は、無敵を貫く物でも値段を有限にしない（払えないので、無効にも割合の軽減にもならない）。
     */
    public static float priced(LivingEntity victim, @Nullable DamageSource source, float blocked) {
        if (!(blocked > 0)) return 0;
        if (!Float.isFinite(blocked)) return blocked;
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return Math.min(blocked, Math.max(0, victim.getHealth() + victim.getAbsorptionAmount()));
        return blocked;
    }

    /**
     * 防御が働いた1回の支払い。払えたらtrue（そのときだけ防ぐ）。値段が有限でなければ払えない。クリエイティブは払わずに防ぐ。
     */
    public static boolean pay(PlayerAbilityData data, ResourceLocation skill, Kind kind, float p, float blocked, boolean creative) {
        float cp = kind.cp(p, blocked);
        float overload = kind.overload(p);
        if (!Float.isFinite(cp) || !Float.isFinite(overload) || cp < 0 || overload < 0) return false;
        if (kind == Kind.JET_FRONT) return data.consumeRunning(skill, cp, overload, creative);
        // 防いだ1回は稼働の払い（拒否の条件は consumeRunning と同じ: CP不足とセーフティだけ）。
        // ロック中は稼働しないので防がない（payAction が断る。ロックからの戻し方）。
        return data.payAction(skill, cp, overload, creative, false, true, false, 0);
    }

    /** おまけの効果の支払い（防御と同じ経路。回復待ちはやり直さない）。払えたらtrue（そのときだけ効果を出す）。 */
    public static boolean payAddOn(PlayerAbilityData data, ResourceLocation skill, float cp, float overload, boolean creative) {
        if (!Float.isFinite(cp) || !Float.isFinite(overload) || cp < 0 || overload < 0) return false;
        // 1.5.2（ロックからの戻し方）: ロック中は防御が働かないので、おまけの効果も出さない。
        if (data.isOverloadLocked()) return false;
        return data.consumeRunning(skill, cp, overload, creative);
    }

    // ---- おまけの効果の値 ----
    /** 反射の近接の打ち返し: ダメージ × lerp(8, 4) CP。 */
    public static float reflectionStrikeBack(float p, float damage) { return Math.max(0, damage) * ArcGen.lerp(8, 4, clamp(p)); }
    /** 反射の飛び道具の向け直し: 難しさ × lerp(60, 30) CP。 */
    public static float reflectionRedirect(float p, float difficulty) { return Math.max(0, difficulty) * ArcGen.lerp(60, 30, clamp(p)); }
    /** 偏向の飛び道具止め: 1つ lerp(12, 6) CP。 */
    public static float deviationStop(float p) { return ArcGen.lerp(12, 6, clamp(p)); }
    /** ライト・シールドの触れた相手への攻撃: 1体ごと（8tickごと）CP lerp(30, 15)。 */
    public static float lightShieldContactCp(float p) { return ArcGen.lerp(30, 15, clamp(p)); }
    /** ライト・シールドの触れた相手への攻撃の過負荷: lerp(2, 0)。 */
    public static float lightShieldContactOverload(float p) { return ArcGen.lerp(2, 0, clamp(p)); }
}
