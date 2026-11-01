package io.github.pinchan4273.reacademycraft.api.client;

/**
 * 外部の演出の光線の見た目（公開API、実験的。演出API版2）。値は、Re:AcademyCraftの描画の部品と寿命の組を選ぶだけで、
 * 威力・射程・判定とは関係しない。後の版で見た目を足すことがある。この版が持たない見た目は{@link ExternalPresentation#supports}がfalse。
 */
public enum BeamStyle {
    /** Meltdownerの光線（緑の芯と鞘、光の帯）。寿命は自然能力のMeltdownerと同じ50tick（伸びる・保つ・細くなって消える）。最大96ブロック。 */
    MELTDOWNER_BEAM,
    /**
     * エレクトロン・ミサイルなどの細い光線（模様{@code mdray_small}）。演出API版2から。寿命は14tick、太さは内0.03・外0.045で、
     * 自然能力と同じく光線に沿って1tickに1つmdの粒子を出す。始点は術者の手元へずらさない（自然能力の細い光線と同じ）。
     */
    MELTDOWNER_RAY_SMALL,
    /** レールガンの光線と、光線に沿って散る小さな電弧。演出API版2から。寿命は50tick。始点は術者の手元へずらす。 */
    RAILGUN,
    /** レイ・バラージの狙いの光線。演出API版2から。寿命は30tick。始点は術者の手元へずらす。 */
    BARRAGE_AIM,
    /** レイ・バラージの扇形の副光線（25〜29本。{@code from}から、{@code to}の方向の周りに散る）。演出API版2から。寿命は50tick。 */
    BARRAGE_SCATTER
}
