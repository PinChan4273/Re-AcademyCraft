package io.github.pinchan4273.reacademycraft.api.client;

/**
 * {@link ExternalPresentation#beam}の結果（公開API、実験的。演出API版1）。{@link #ACCEPTED}以外では、何も足さない。
 * 演出が受け付けられなくても、サーバーで既に成立した攻撃や費用は変わらない（呼び出し側は、自分の演出に戻ってよい）。
 */
public enum BeamResult {
    /** 足した。寿命まで描く。 */
    ACCEPTED,
    /** 同じ持ち主・同じ効果IDの光線を、まだ描いているか、終わってから200tick以内（再送への対策）。 */
    DUPLICATE,
    /** 持ち主の本数が上限（{@link ExternalPresentation#limit}）に達している。古い光線は押し出さない。 */
    FULL,
    /** この版に、その見た目が無い。 */
    UNSUPPORTED,
    /**
     * 要求が正しくない: 持ち主が読み込まれたMOD IDでない、または{@code academy}、効果IDが0以下、座標が有限でない・範囲の外、
     * 長さが0以下か{@link ExternalPresentation#maxLength}を超える、次元が今のクライアントの次元と違う、見た目がnull。
     */
    INVALID,
    /** 世界が無い、または資源の読み直し（オーバーレイ）の最中。 */
    NOT_READY
}
