package io.github.pinchan4273.reacademycraft.api.client;

/**
 * 外部の演出の光線の見た目（公開API、実験的。演出API版1）。値は、Re:AcademyCraftの描画の部品と寿命の組を選ぶだけで、
 * 威力・射程・判定とは関係しない。後の版で見た目を足すことがある。この版が持たない見た目は{@link ExternalPresentation#supports}がfalse。
 */
public enum BeamStyle {
    /** Meltdownerの光線（緑の芯と鞘、光の帯）。寿命は自然能力のMeltdownerと同じ50tick（伸びる・保つ・細くなって消える）。最大96ブロック。 */
    MELTDOWNER_BEAM
}
