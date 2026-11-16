package io.github.pinchan4273.reacademycraft.api;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * 能力のカテゴリ（系統）の定義（公開API、実験的）。
 * <ul>
 * <li>id: カテゴリの名前。</li>
 * <li>factorItem: このカテゴリを与える因子のアイテムの名前（無ければnull。因子で指定して取得・変更できない系統）。</li>
 * <li>controllablePerLevel: レベル1〜5のそれぞれで、このカテゴリが持つ操作可能な技能の数（レベルアップの条件の計算に使う。5つ）。</li>
 * <li>presentation: 表示の情報。</li>
 * <li>randomAcquire: 誘導因子を持たずに、開発機で初回の取得、または磁気コイルで系統の変更をしたときの、ランダムの抽選の対象に入るか
 *     （1.5.2、公開APIバージョン3）。対象の系統は、どれも同じ確率で選ばれる。因子の指定（factorItem）とは別で、因子の無い系統も参加できる。
 *     既定はfalse（作者が参加を明示した系統だけが対象）。本体の5系統はtrue。</li>
 * </ul>
 */
public record CategoryDefinition(AbilityId id, @Nullable AbilityId factorItem, List<Integer> controllablePerLevel, Presentation presentation,
                                 boolean randomAcquire) {
    public CategoryDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(presentation, "presentation");
        controllablePerLevel = List.copyOf(controllablePerLevel);
        if (controllablePerLevel.size() != 5 || controllablePerLevel.stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("A category needs five non-negative controllable counts: " + id);
    }

    /**
     * 1.5.1までの4つの値の形（既存のアドオンとの互換のために残す）。ランダムの取得・変更の抽選には参加しない（randomAcquire = false）。
     */
    public CategoryDefinition(AbilityId id, @Nullable AbilityId factorItem, List<Integer> controllablePerLevel, Presentation presentation) {
        this(id, factorItem, controllablePerLevel, presentation, false);
    }
}
