package io.github.pinchan4273.reacademycraft.api;

/**
 * 発動の結果（公開API、実験的）。{@code REFUSED_*}は断った理由で、断ったときは何も起きていない（資源も払っていない）ことを、振る舞いの側が守る。
 * <ul>
 * <li>{@link #PERFORMED}: 1回の効果を終えた（セッションを残さない）。</li>
 * <li>{@link #STARTED}: 押し続け型・切り替え型のセッションを始めた。一回きり（INSTANT）の技能が返したときは、PERFORMEDとして扱う。</li>
 * <li>{@link #REFUSED_UNLEARNED}: 能力が無い・カテゴリが合わない・習得していない・データが読み取り専用。</li>
 * <li>{@link #REFUSED_DISABLED}: サーバーの設定で、その技能が無効。</li>
 * <li>{@link #REFUSED_INACTIVE}: 能力がオフ。</li>
 * <li>{@link #REFUSED_OVERLOAD}: 過負荷の錠がかかっている。</li>
 * <li>{@link #REFUSED_INTERFERED}: 能力の妨害の中。</li>
 * <li>{@link #REFUSED_COOLDOWN}: クールダウンの中。</li>
 * <li>{@link #REFUSED_RESOURCE}: 資源（自然能力者ならCP）が足りない。振る舞いが返す。</li>
 * <li>{@link #REFUSED_PERMISSION}: 権限・保護・PvPの規則が許さない。振る舞いや安全のサービスが返す。</li>
 * <li>{@link #REFUSED_INVALID}: そのほかの不正な状態（生きていない、画面を開いている、同じ技能が動作中、振る舞いが例外を出した、など）。</li>
 * </ul>
 */
public enum ActivationOutcome {
    PERFORMED, STARTED, REFUSED_UNLEARNED, REFUSED_DISABLED, REFUSED_INACTIVE, REFUSED_OVERLOAD, REFUSED_INTERFERED, REFUSED_COOLDOWN,
    REFUSED_RESOURCE, REFUSED_PERMISSION, REFUSED_INVALID;

    /** 受け付けたか（PERFORMEDかSTARTED）。 */
    public boolean accepted() { return this == PERFORMED || this == STARTED; }
}
