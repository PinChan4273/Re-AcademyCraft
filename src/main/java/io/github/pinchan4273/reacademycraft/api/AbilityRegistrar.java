package io.github.pinchan4273.reacademycraft.api;

/**
 * 登録の窓（公開API、実験的）。本体が決めた時機にだけ使え、窓が閉じた後に呼ぶと例外になる。
 * 同じIDの2回目の登録、名前空間{@code academy}の登録（本体だけが使う）、知らないカテゴリへの技能の登録は、例外で断る
 * （黙って上書きしない）。
 */
public interface AbilityRegistrar {
    /** 登録する側の名前（modのID）。例外のメッセージに出る。 */
    String owner();
    void category(CategoryDefinition definition);
    void skill(SkillDefinition definition);
}
