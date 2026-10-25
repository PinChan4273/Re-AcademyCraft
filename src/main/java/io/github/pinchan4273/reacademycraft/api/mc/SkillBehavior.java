package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.StopReason;

/**
 * 技能の効果（公開API、実験的。サーバーでだけ呼ぶ）。アドオンが、自分の技能ごとに1つ登録する（{@link SkillBehaviorRegistrar}）。
 * <p>
 * Re:AcademyCraftが受け持つこと: 発動の資格の判定（{@link #start}の前。能力の有無・習得・設定の有効・能力のオン・過負荷・妨害・
 * クールダウン・画面・同じ技能の重複）、セッションの管理（押し続け型の心拍、切り替え型の2回目の押下、能力の側の一括の停止、
 * 死亡・ログアウト・次元の移動・妨害での停止）。振る舞いが受け持つこと: 資源の支払い（{@link ActivationContext#pay}）と効果。
 * 断るときは、何も変えずに{@code REFUSED_*}を返す。
 * <p>
 * 例外を投げた振る舞いは、そのセッションを終えて記録に残す（発動の結果は{@code REFUSED_INVALID}）。サーバーは止めない。
 */
public interface SkillBehavior {
    /**
     * キーの押下（一回きりの技能は効果、押し続け型・切り替え型はセッションの開始）。セッションを始めたら{@code STARTED}を返す。
     */
    ActivationOutcome start(ActivationContext context);

    /** セッションの間、サーバーのtickごとに1回。falseを返すと、Re:AcademyCraftが{@link StopReason#FINISHED}で終える。 */
    default boolean tick(ActivationContext context) { return true; }

    /** 押し続け型のキーを離した。この呼び出しの後、セッションは終わる（{@link #stop}は呼ばない）。 */
    default void release(ActivationContext context) { }

    /** セッションを終える（キーを離した場合を除く）。この呼び出しの後、同じ文脈は渡さない。 */
    default void stop(ActivationContext context, StopReason reason) { }
}
