package io.github.pinchan4273.reacademycraft.api;

/**
 * 技能の発動の種類（公開API、実験的）。クライアントの技能のキーの送り方と、サーバーのディスパッチが、この種類で決まる。
 * <ul>
 * <li>{@link #INSTANT}: キーを押すたびに1回の発動（CAST）を送る。技能がその押下で何をするか（1回の効果、モードの開始と終了など）を決める。</li>
 * <li>{@link #HELD}: キーを押している間、ハートビート（HELD）を送り、離すとRELEASE、中断するとCANCELを送る。</li>
 * <li>{@link #TOGGLE}: 1回押すとON、次に押すとOFF。新しい押下でだけCASTを送り、キーリピートでは送らない。ONの間は、自身のキーで終わる。</li>
 * <li>{@link #PASSIVE}: キーに割り当てられない（プリセットに置けない）。</li>
 * </ul>
 * 技能の登録（{@code SkillDefinition#kind()}）が持つ値は、その技能の既定の発動方式。1.5.2（公開APIバージョン3）から、本体の一部の技能は
 * プレイヤーが能力設定でパッシブ（能力使用モードで自動で起動）に切り替えられるので、プレイヤーごとの今の方式は
 * {@code api.mc.AbilityStatus#activationKind}で問い合わせる。
 */
public enum ActivationKind { INSTANT, HELD, TOGGLE, PASSIVE }
