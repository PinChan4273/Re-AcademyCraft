package io.github.pinchan4273.reacademycraft.api;

import java.util.Objects;

/** 技能を習得する前提（公開API、実験的）: その技能を習得していて、熟練度がproficiency（0〜1）以上。 */
public record SkillRequirement(AbilityId skill, float proficiency) {
    public SkillRequirement {
        Objects.requireNonNull(skill, "skill");
        if (!(proficiency >= 0 && proficiency <= 1)) throw new IllegalArgumentException("Invalid proficiency " + proficiency + " for " + skill);
    }
}
