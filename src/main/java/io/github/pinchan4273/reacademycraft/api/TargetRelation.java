package io.github.pinchan4273.reacademycraft.api;

/**
 * 発動の主体から見た相手の関係（公開API、実験的。1.4.0のS5）。
 * <ul>
 * <li>{@link #SELF}: 主体の実体そのもの、または帰属先のプレイヤー本人。</li>
 * <li>{@link #ALLY}: 帰属先のプレイヤーと同じチーム、またはその飼い動物。</li>
 * <li>{@link #PLAYER}: そのほかのプレイヤー。</li>
 * <li>{@link #HOSTILE}: 敵対の生き物、または主体・帰属先を狙っている生き物。</li>
 * <li>{@link #NEUTRAL}: 中立の生き物（怒らせると攻撃する）。</li>
 * <li>{@link #FRIENDLY}: そのほかの生き物。</li>
 * <li>{@link #NONE}: 生き物でない、生きていない、観戦者、別の世界。</li>
 * </ul>
 */
public enum TargetRelation { SELF, ALLY, PLAYER, HOSTILE, NEUTRAL, FRIENDLY, NONE }
