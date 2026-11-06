package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import javax.annotation.Nullable;

/**
 * 外部の主体（装備・機械・AI）からの発動（公開API、実験的。1.4.0のS5。サーバーのスレッドでだけ呼ぶ）。
 * <p>
 * 外部の主体が発動できるのは、外部のアドオンが登録した受動でない技能だけ（本体の41技能は発動できない）。
 * 自然能力者（{@code ActorKind.NATURAL}）は、この入口ではなく技能のキーで発動する。
 * <p>
 * 発動の手順（この順）: 主体の確かめ（使える・位置が読み込まれている）→ 資格（登録・設定で有効・能力の妨害の外・クールダウン・
 * 同じ技能の重複）→ 権限（本体の技能でない）→ 資源の方針（無ければ拒否）→ 効果（振る舞い）→ 結果。
 * 攻撃・地形の変更・空間移動の判定は、振る舞いが{@link AcademySafety}を通して行う（通さない処理はRe:AcademyCraftには止められない）。
 * <p>
 * セッション: 切り替え型と押し続け型の技能は、{@code STARTED}を返すとセッションになる。押し続け型は、続ける間{@link #hold}を
 * tickごとに呼ぶ（途切れると止める）。どちらも{@link #stop}で終える。主体が使えなくなった・妨害の範囲に入ったときも止める。
 */
public final class AbilityActivation {
    private AbilityActivation() { }

    /** 発動する。policyがnullなら{@code REFUSED_RESOURCE}。 */
    public static ActivationOutcome activate(AbilityActor actor, AbilityId skill, @Nullable ResourcePolicy policy) {
        return io.github.pinchan4273.reacademycraft.ability.ExternalSessions.activate(actor, skill, policy);
    }
    /** 押し続け型のセッションの心拍。セッションがあればtrue。 */
    public static boolean hold(AbilityActor actor, AbilityId skill) {
        return io.github.pinchan4273.reacademycraft.ability.ExternalSessions.hold(actor, skill);
    }
    /** セッションを終える（理由は{@code REQUESTED}）。セッションがあればtrue。 */
    public static boolean stop(AbilityActor actor, AbilityId skill) {
        return io.github.pinchan4273.reacademycraft.ability.ExternalSessions.stop(actor, skill);
    }
    /** その主体の、その技能のセッションが動作中か。 */
    public static boolean active(AbilityActor actor, AbilityId skill) {
        return io.github.pinchan4273.reacademycraft.ability.ExternalSessions.active(actor, skill);
    }
}
