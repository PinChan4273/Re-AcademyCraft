package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;

/**
 * 原作の汎用コース（WeAthFolD）。受動技能で、スロットのアクションにはならない。
 * 上級コースのコードは、原作の英語の説明が1000と言うのに反して1500CPを与える。
 */
public final class GenericSkills {
    public static final ResourceLocation BRAIN = ResourceLocation.fromNamespaceAndPath("academy", "brain_course");
    public static final ResourceLocation ADVANCED_BRAIN = ResourceLocation.fromNamespaceAndPath("academy", "brain_course_advanced");
    public static final ResourceLocation MIND = ResourceLocation.fromNamespaceAndPath("academy", "mind_course");
    private GenericSkills() { }
    /** Meltdowner自身の受動技能は汎用コースではないが、装備はできない。 */
    public static boolean isPassive(ResourceLocation id) { return BRAIN.equals(id) || ADVANCED_BRAIN.equals(id) || MIND.equals(id)
            || RadiationIntensify.ID.equals(id) || DimFoldingTheorem.ID.equals(id) || SpaceFluctuation.ID.equals(id); }
    public static float cpBonus(PlayerAbilityData d) { return (d.hasLearned(BRAIN) ? 1000 : 0) + (d.hasLearned(ADVANCED_BRAIN) ? 1500 : 0); }
    public static float overloadBonus(PlayerAbilityData d) { return d.hasLearned(ADVANCED_BRAIN) ? 100 : 0; }
    public static float recoveryMultiplier(PlayerAbilityData d) { return d.hasLearned(MIND) ? 1.2f : 1; }
}
