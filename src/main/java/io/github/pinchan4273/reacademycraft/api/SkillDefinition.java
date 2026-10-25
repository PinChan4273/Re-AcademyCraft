package io.github.pinchan4273.reacademycraft.api;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * 技能の定義（公開API、実験的）。
 * <ul>
 * <li>id: 技能の名前。</li>
 * <li>category: 属するカテゴリ（nullなら、どのカテゴリにも出る汎用の技能。本体の汎用コースがこれ）。</li>
 * <li>level: 習得に要るレベル（1〜5）。</li>
 * <li>prerequisites: 習得の前提。</li>
 * <li>kind: 発動の種類。</li>
 * <li>presentation: 表示の情報。</li>
 * <li>tree: 開発機の技能の木の中の場所（1.4.0のS4。無ければnullで、木に出ず、開発機では学べない。アドオンが自分の条件で与える）。
 * 本体の技能の場所は、本体が別に持つ。</li>
 * </ul>
 */
public record SkillDefinition(AbilityId id, @Nullable AbilityId category, int level, List<SkillRequirement> prerequisites,
                              ActivationKind kind, Presentation presentation, @Nullable TreePosition tree) {
    public SkillDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(presentation, "presentation");
        if (level < 1 || level > 5) throw new IllegalArgumentException("Invalid level " + level + " for " + id);
        prerequisites = List.copyOf(prerequisites);
        if (tree != null && tree.parent() != null && prerequisites.stream().noneMatch(r -> r.skill().equals(tree.parent())))
            throw new IllegalArgumentException("The tree line of " + id + " comes from " + tree.parent() + ", which it does not require");
    }
    /** 技能の木の場所の無い定義（1.4.0のS2の形）。 */
    public SkillDefinition(AbilityId id, @Nullable AbilityId category, int level, List<SkillRequirement> prerequisites,
                           ActivationKind kind, Presentation presentation) {
        this(id, category, level, prerequisites, kind, presentation, null);
    }
}
