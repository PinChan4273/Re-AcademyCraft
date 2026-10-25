package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;

/**
 * 外部の主体の資源の方針（公開API、実験的。1.4.0のS5）。外部の主体は自然能力者のCP・過負荷を持たないので、発動の時に
 * アドオンが方針を渡す。方針を渡さない発動は断る（{@code REFUSED_RESOURCE}）。費用を取らないなら、{@link #FREE}を明示して選ぶ。
 * 費用には、その技能の設定の倍率（CPと過負荷の消費の速さ）を掛けてから渡す。
 */
@FunctionalInterface
public interface ResourcePolicy {
    /** その費用を払う（不可分の取引。払えなければ何も変えずにfalse）。サーバーのスレッドで呼ぶ。 */
    boolean pay(AbilityActor actor, AbilityId skill, float cp, float overload);

    /** 費用を取らない方針（アドオンが明示して選ぶときだけ使う）。 */
    ResourcePolicy FREE = (actor, skill, cp, overload) -> true;
}
