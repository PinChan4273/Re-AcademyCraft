package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** 技能の一覧。レベル・前提条件・カテゴリは原作の各カテゴリ（Cat*.java）の登録のもの。 */
public final class SkillCatalog {
    public record Requirement(ResourceLocation skill, float proficiency) { }
    /** カテゴリがnullなら原作の汎用コースで、1つのカテゴリに属さずすべてのカテゴリで提供される。 */
    public record Definition(ResourceLocation id, ResourceLocation category, int level, List<Requirement> prerequisites) {
        public Definition { prerequisites = List.copyOf(prerequisites); }
        /** 名前の翻訳キー（1.4.0のS2から登録簿の表示の情報。本体の技能は1.3.0と同じ{@code academy.skill.<path>}）。 */
        public String translation() { return io.github.pinchan4273.reacademycraft.ability.RegistryViews.skillNameKey(id); }
        /** 説明の翻訳キー（開発機の画面。本体の技能は1.3.0と同じ{@code academy.developer.desc.<path>}）。 */
        public String descriptionKey() { return io.github.pinchan4273.reacademycraft.ability.RegistryViews.skillDescriptionKey(id); }
        public boolean passive() { return GenericSkills.isPassive(id); }
        public boolean offeredTo(ResourceLocation ability) {
            return ability != null && (category == null || category.equals(ability));
        }
        public boolean canLearn(PlayerAbilityData data) {
            // 原作default.confのenabled: 無効にした技能は習得できない。
            return !data.isReadOnly() && io.github.pinchan4273.reacademycraft.config.AcademyConfig.skillEnabled(id) && offeredTo(data.getAbility()) && data.getLevel() >= level
                    && !data.hasLearned(id) && data.learnedSkills().size() < PlayerAbilityData.MAX_SKILLS
                    && (!passive() || IMPLEMENTED.stream().anyMatch(s -> s.level() == level && data.hasLearned(s.id())))
                    && prerequisites.stream().allMatch(r -> data.hasLearned(r.skill()) && data.getProficiency(r.skill()) >= r.proficiency());
        }
    }
    /**
     * 技能の一覧（1.4.0のS2から、登録簿{@link io.github.pinchan4273.reacademycraft.ability.AbilityRegistry}の読み取りの窓）。本体の41技能の並び・
     * レベル・前提・カテゴリは1.3.0のまま（{@code ability/BuiltinAbilities}、CatalogGoldenGameTestsが照らす）。外部のアドオンの技能は、
     * 登録の窓で足された順に、本体の後ろに並ぶ。原作の各カテゴリ（Cat*.java）の登録の由来の説明は、移す前のこのクラス（03f1f238）にある。
     */
    public static final List<Definition> IMPLEMENTED = io.github.pinchan4273.reacademycraft.ability.RegistryViews.list(
            io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE::skills, SkillCatalog::definition);
    private static Definition definition(io.github.pinchan4273.reacademycraft.api.SkillDefinition skill) {
        var prerequisites = skill.prerequisites().stream()
                .map(r -> new Requirement(io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(r.skill()), r.proficiency())).toList();
        return new Definition(io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(skill.id()),
                skill.category() == null ? null : io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(skill.category()),
                skill.level(), prerequisites);
    }
    /** 操作できる（受動でない）技能。{@link #IMPLEMENTED}と同じく読み取りの窓。 */
    public static final List<Definition> CONTROLLABLE = io.github.pinchan4273.reacademycraft.ability.RegistryViews.list(
            () -> io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE.skills().stream()
                    .filter(s -> s.kind() != io.github.pinchan4273.reacademycraft.api.ActivationKind.PASSIVE).toList(), SkillCatalog::definition);
    private SkillCatalog() {}
    public static Definition find(ResourceLocation id) {
        return IMPLEMENTED.stream().filter(skill -> skill.id().equals(id)).findFirst().orElse(null);
    }
}
