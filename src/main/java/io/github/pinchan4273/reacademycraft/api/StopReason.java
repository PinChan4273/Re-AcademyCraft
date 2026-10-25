package io.github.pinchan4273.reacademycraft.api;

/**
 * セッションが終わった理由（公開API、実験的）。押し続け型のキーを離したときは、停止ではなく離し（{@code release}）を呼び、そのままセッションを終える。
 * <ul>
 * <li>{@link #CANCELLED}: クライアントの安全な停止（ウィンドウのフォーカスが外れたなど）。</li>
 * <li>{@link #SWITCHED_OFF}: 切り替え型を、自身のキーでもう一度押した。</li>
 * <li>{@link #HEARTBEAT_LOST}: 押し続け型の心拍が途切れた。</li>
 * <li>{@link #ALL_STOPPED}: 能力の側がすべてのセッションを終えた（能力のオフ、プリセットの切り替え、過負荷、カテゴリの変更）。</li>
 * <li>{@link #INELIGIBLE}: 続ける資格を失った（死亡、観戦者、能力の妨害、次元の移動、データが使えなくなった）。</li>
 * <li>{@link #LOGGED_OUT}: 使用者がログアウトした。</li>
 * <li>{@link #FINISHED}: 振る舞いの{@code tick}が、終えると返した。</li>
 * <li>{@link #REQUESTED}: 外部の主体の側が終えた（{@code AbilityActivation.stop}。1.4.0のS5）。</li>
 * </ul>
 */
public enum StopReason { CANCELLED, SWITCHED_OFF, HEARTBEAT_LOST, ALL_STOPPED, INELIGIBLE, LOGGED_OUT, FINISHED, REQUESTED }
