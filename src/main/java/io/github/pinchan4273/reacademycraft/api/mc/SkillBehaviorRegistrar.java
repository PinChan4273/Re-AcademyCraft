package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;

/**
 * 技能の振る舞いを登録するレジストラ（公開API、実験的）。能力を登録するレジストラと同じ時機にだけ使える。
 * 自分が登録した技能にだけ、1つずつ登録できる。パッシブの技能には登録できない。パッシブでない外部の技能に振る舞いが無ければ、
 * 凍結の時に読み込みを止める（どの技能かを示す）。
 */
public interface SkillBehaviorRegistrar {
    String owner();
    void behavior(AbilityId skill, SkillBehavior behavior);
}
