package io.github.pinchan4273.reacademycraft.api;

/** ベクトル反射の状態（公開API、実験的。API版2）。{@code api.mc.AcademySafety.reflection}が返す。 */
public enum ReflectionState {
    /** 相手がプレイヤーで、ベクトル反射のモードが今オン（Re:AcademyCraftの反射の処理が働く条件と同じ）。 */
    REFLECTING,
    /** プレイヤーでモードがオフ、またはプレイヤー以外の生き物（ベクトル反射を使えるのはプレイヤーだけ）。 */
    NOT_REFLECTING,
    /** 判断できない: サーバーのスレッドの外、クライアントの実体、nullなど。守る側として扱う。 */
    UNKNOWN
}
