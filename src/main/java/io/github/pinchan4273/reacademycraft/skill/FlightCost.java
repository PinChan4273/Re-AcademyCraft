package io.github.pinchan4273.reacademycraft.skill;

/**
 * 1.5.2 飛行の部類の値段。手本は白翼飛行で、白翼飛行・ストームウィング・ジェット・エンジンが使う。
 * 数値は熟練度0% → 100%で、その間は直線で補間する。「G0」は、その人の待ちの無いときの1tickの回復（{@link PlayerAbilityData#g0PerTick}）。
 * <ul>
 * <li>離陸: 1回ごとに CP lerp(40, 20) と過負荷 lerp(10, 0) を払う（技能の発動の支払いなので、ほかの技能と同じく
 *     CPの回復待ちが始まる）。ストームウィングの溜め（無料）は、技能の特徴として残す。</li>
 * <li>状態ごとの総額（1.5.1 と同じ値）: 浮いているだけは1tickに G0 × lerp(1.0, 50%, 熟練度)。
 *     動いている間は G0 の全額 ＋ 毎秒の純減 / 20 で、純減は白翼飛行の通常の移動が lerp(40, 20)、高速とジェット・エンジン（いつも推進、
 *     前面の防御を含む）が lerp(80, 40)、ストームウィングの推進は設定 thrust_cp_per_second（既定 lerp(120, 40)）。
 *     自然回復は全部飛行に回るので、動いている間の CP は純減の分だけ減る（レベル5・熟練度100%・最大CP 24000 で、高速は10分、通常は20分）。
 *     浮いているだけなら減らない。</li>
 * <li>G0 は待ちの無いときの回復なので、回復待ちの間も同じ額を払う（その tick の回復の有無に依らない）。</li>
 * <li>共通: オンの間に過負荷を下げない作り（滞空の過負荷の回復の置き換え）と、CPの回復待ちをやり直す作りは使わない
 *     （維持費と推進の支払いは回復待ちを作らない）。飛行の許可（mayfly）の貸し方・戻し方も共通。</li>
 * </ul>
 */
public final class FlightCost {
    private FlightCost() { }

    /** 離陸のCP（熟練度0% → 100%）。 */
    public static final float TAKEOFF_CP_FROM = 40, TAKEOFF_CP_TO = 20;
    /** 離陸の過負荷（熟練度0% → 100%）。 */
    public static final float TAKEOFF_OVERLOAD_FROM = 10, TAKEOFF_OVERLOAD_TO = 0;
    /** 熟練度0%の、G0 に対する割合（どの状態も100%）。 */
    public static final float SHARE_FROM = 1f;
    /** 熟練度100%の、G0 に対する割合（1.5.1 の値）: 滞空は50%、通常の移動・高速と推進は全額。 */
    public static final float HOVER_SHARE_TO = .5f, MOVE_SHARE_TO = 1f, FAST_SHARE_TO = 1f, PROPEL_SHARE_TO = 1f;
    /** 毎秒の純減（熟練度0% → 100%。1.5.1 の値）: 通常の移動・高速・ジェット・エンジン（ジェットは高速にそろえた）。 */
    public static final float MOVE_EXTRA_FROM = 40, MOVE_EXTRA_TO = 20, FAST_EXTRA_FROM = 80, FAST_EXTRA_TO = 40,
            JET_EXTRA_FROM = 80, JET_EXTRA_TO = 40;

    private static float clamp(float p) { return Float.isNaN(p) ? 0 : Math.max(0, Math.min(1, p)); }

    /** 離陸1回のCP。 */
    public static float takeoffCp(float p) { return ArcGen.lerp(TAKEOFF_CP_FROM, TAKEOFF_CP_TO, clamp(p)); }
    /** 離陸1回の過負荷。 */
    public static float takeoffOverload(float p) { return ArcGen.lerp(TAKEOFF_OVERLOAD_FROM, TAKEOFF_OVERLOAD_TO, clamp(p)); }
    /** G0 に対する割合 lerp(1.0, to, 熟練度)。 */
    public static float share(float p, float to) { return ArcGen.lerp(SHARE_FROM, to, clamp(p)); }
    /** 浮いているだけのときの、G0 に対する割合（100% → 50%）。 */
    public static float hoverShare(float p) { return share(p, HOVER_SHARE_TO); }
    /** 通常の移動の毎秒の純減（40 → 20）。 */
    public static float moveCpPerSecond(float p) { return ArcGen.lerp(MOVE_EXTRA_FROM, MOVE_EXTRA_TO, clamp(p)); }
    /** 高速の毎秒の純減（80 → 40）。 */
    public static float fastCpPerSecond(float p) { return ArcGen.lerp(FAST_EXTRA_FROM, FAST_EXTRA_TO, clamp(p)); }
    /** ジェット・エンジンの毎秒の純減（80 → 40）。 */
    public static float jetCpPerSecond(float p) { return ArcGen.lerp(JET_EXTRA_FROM, JET_EXTRA_TO, clamp(p)); }

    private static float g(float g0) { return Float.isFinite(g0) ? Math.max(0, g0) : 0; }
    /** 浮いているだけの1tickの支払い（技能の倍率の前）: G0 × lerp(1.0, 50%)。 */
    public static float hoverDue(float p, float g0) { return g(g0) * hoverShare(p); }
    /** 白翼飛行の通常の移動の1tick（技能の倍率の前）: G0 ＋ lerp(40, 20) / 20。 */
    public static float moveDue(float p, float g0) { return g(g0) * share(p, MOVE_SHARE_TO) + moveCpPerSecond(p) / 20f; }
    /** 白翼飛行の高速の1tick（技能の倍率の前）: G0 ＋ lerp(80, 40) / 20。 */
    public static float fastDue(float p, float g0) { return g(g0) * share(p, FAST_SHARE_TO) + fastCpPerSecond(p) / 20f; }
    /** ジェット・エンジンの1tick（技能の倍率の前。前面の防御を含む総額）: G0 ＋ lerp(80, 40) / 20。 */
    public static float jetDue(float p, float g0) { return g(g0) * share(p, PROPEL_SHARE_TO) + jetCpPerSecond(p) / 20f; }
    /**
     * ストームウィングの推進の tick に、滞空の維持（{@link #hoverDue}）へ足して技能が払う額（技能の倍率の前）:
     * G0 × (1 − lerp(1.0, 50%)) ＋ 設定の推進の純減（1tickの額）。合計は推進の総額 G0 ＋ 純減。
     */
    public static float stormPropelExtra(float p, float g0, float thrustPerTick) {
        return g(g0) * (share(p, PROPEL_SHARE_TO) - hoverShare(p)) + Math.max(0, thrustPerTick);
    }
}
