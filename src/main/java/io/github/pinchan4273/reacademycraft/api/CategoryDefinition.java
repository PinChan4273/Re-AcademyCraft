package io.github.pinchan4273.reacademycraft.api;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * 能力のカテゴリ（系統）の定義（公開API、実験的）。
 * <ul>
 * <li>id: カテゴリの名前。</li>
 * <li>factorItem: このカテゴリを与える因子のアイテムの名前（無ければnull。開発機で取得しない系統）。</li>
 * <li>controllablePerLevel: レベル1〜5のそれぞれで、このカテゴリが持つ操作可能な技能の数（レベルアップの条件の計算に使う。5つ）。</li>
 * <li>presentation: 表示の情報。</li>
 * </ul>
 */
public record CategoryDefinition(AbilityId id, @Nullable AbilityId factorItem, List<Integer> controllablePerLevel, Presentation presentation) {
    public CategoryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(presentation, "presentation");
        controllablePerLevel = List.copyOf(controllablePerLevel);
        if (controllablePerLevel.size() != 5 || controllablePerLevel.stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("A category needs five non-negative controllable counts: " + id);
    }
}
