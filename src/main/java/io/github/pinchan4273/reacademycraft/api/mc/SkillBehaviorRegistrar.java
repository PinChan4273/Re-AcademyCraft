package io.github.pinchan4273.reacademycraft.api.mc;

import io.github.pinchan4273.reacademycraft.api.AbilityId;

/**
 * 技能の振る舞いの登録の窓（公開API、実験的）。能力の登録の窓と同じ時機にだけ使える。
 * 自分が登録した技能にだけ、1つずつ登録できる。受動の技能には登録できない。受動でない外部の技能に振る舞いが無ければ、
 * 凍結の時に読み込みを止める（どの技能かを示す）。
 */
public interface SkillBehaviorRegistrar {
    String owner();
    void behavior(AbilityId skill, SkillBehavior behavior);
}
