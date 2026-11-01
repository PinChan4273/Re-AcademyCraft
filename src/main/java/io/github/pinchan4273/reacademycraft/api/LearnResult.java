package io.github.pinchan4273.reacademycraft.api;

/**
 * 外部の技能を与えた結果（公開API、実験的）。{@code REFUSED_*}は何も変えていない。
 * <ul>
 * <li>{@link #LEARNED}: 新しく習得した。</li>
 * <li>{@link #ALREADY_LEARNED}: 前から習得していた（何も変えていない）。</li>
 * <li>{@link #REFUSED_UNKNOWN}: 登録されていない技能。</li>
 * <li>{@link #REFUSED_BUILT_IN}: 本体の技能（本体の技能は開発機などの本体の経路だけで学ぶ）。</li>
 * <li>{@link #REFUSED_DISABLED}: サーバーの設定で無効。</li>
 * <li>{@link #REFUSED_CATEGORY}: 能力が無い、またはそのカテゴリが提供しない技能。</li>
 * <li>{@link #REFUSED_READ_ONLY}: データが読み取り専用（より新しい版や、解析できないカテゴリのデータ）。</li>
 * <li>{@link #REFUSED_FULL}: 習得できる技能の数の上限。</li>
 * </ul>
 */
public enum LearnResult { LEARNED, ALREADY_LEARNED, REFUSED_UNKNOWN, REFUSED_BUILT_IN, REFUSED_DISABLED, REFUSED_CATEGORY, REFUSED_READ_ONLY, REFUSED_FULL }
