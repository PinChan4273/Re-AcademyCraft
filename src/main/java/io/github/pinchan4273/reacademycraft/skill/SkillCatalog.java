package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** 技能の一覧。レベル・前提条件・カテゴリは原作の各カテゴリ（Cat*.java）の登録のもの。 */
public final class SkillCatalog {
    public record Requirement(ResourceLocation skill, float proficiency) { }
    /** カテゴリがnullなら原作の汎用コースで、1つのカテゴリに属さずすべてのカテゴリで提供される。 */
    public record Definition(ResourceLocation id, ResourceLocation category, int level, List<Requirement> prerequisites) {
        public Definition { prerequisites = List.copyOf(prerequisites); }
        public String translation() { return "academy.skill." + id.getPath(); }
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
    private static Definition electromaster(ResourceLocation id, int level, List<Requirement> prerequisites) {
        return new Definition(id, ArcGen.CATEGORY, level, prerequisites);
    }
    private static Definition meltdowner(ResourceLocation id, int level, List<Requirement> prerequisites) {
        return new Definition(id, AbilityCategory.MELTDOWNER.id(), level, prerequisites);
    }
    private static Definition teleporter(ResourceLocation id, int level, List<Requirement> prerequisites) {
        return new Definition(id, AbilityCategory.TELEPORTER.id(), level, prerequisites);
    }
    private static Definition vecmanip(ResourceLocation id, int level, List<Requirement> prerequisites) {
        return new Definition(id, AbilityCategory.VECMANIP.id(), level, prerequisites);
    }
    private static Definition generic(ResourceLocation id, int level, List<Requirement> prerequisites) {
        return new Definition(id, null, level, prerequisites);
    }
    public static final List<Definition> IMPLEMENTED = List.of(electromaster(ArcGen.ID, 1, List.of()),
            electromaster(CurrentCharging.ID, 1, List.of(new Requirement(ArcGen.ID, .3f))),
            electromaster(MagneticMovement.ID, 2, List.of(new Requirement(ArcGen.ID, 0), new Requirement(CurrentCharging.ID, .7f))),
            electromaster(MagneticManipulation.ID, 2, List.of(new Requirement(MagneticMovement.ID, .5f))),
            electromaster(BodyIntensify.ID, 3, List.of(new Requirement(ArcGen.ID, 1), new Requirement(CurrentCharging.ID, 1))),
            electromaster(MineDetect.ID, 3, List.of(new Requirement(MagneticManipulation.ID, 1))),
            electromaster(ThunderBolt.ID, 4, List.of(new Requirement(ArcGen.ID, 0), new Requirement(CurrentCharging.ID, .7f))),
            electromaster(Railgun.ID, 4, List.of(new Requirement(ThunderBolt.ID, .3f), new Requirement(MagneticManipulation.ID, 1))),
            electromaster(ThunderClap.ID, 5, List.of(new Requirement(ThunderBolt.ID, 1))),
            meltdowner(ElectronBomb.ID, 1, List.of()),
            meltdowner(RadiationIntensify.ID, 1, List.of(new Requirement(ElectronBomb.ID, .5f))),
            meltdowner(ScatterBomb.ID, 2, List.of(new Requirement(ElectronBomb.ID, .8f))),
            meltdowner(LightShield.ID, 2, List.of(new Requirement(ElectronBomb.ID, 1))),
            // 原作CatMeltdowner: meltdowner.setParent(scatterBomb, .8f); addSkillDep(lightShield, .8f)。
            meltdowner(Meltdowner.ID, 3, List.of(new Requirement(ScatterBomb.ID, .8f), new Requirement(LightShield.ID, .8f))),
            meltdowner(MineRay.BASIC.id(), 3, List.of(new Requirement(Meltdowner.ID, .3f))),
            meltdowner(MineRay.EXPERT.id(), 4, List.of(new Requirement(MineRay.BASIC.id(), .8f))),
            meltdowner(MineRay.LUCK.id(), 5, List.of(new Requirement(MineRay.EXPERT.id(), 1))),
            meltdowner(JetEngine.ID, 4, List.of(new Requirement(Meltdowner.ID, 1))),
            meltdowner(RayBarrage.ID, 4, List.of(new Requirement(Meltdowner.ID, .5f))),
            meltdowner(ElectronMissile.ID, 5, List.of(new Requirement(JetEngine.ID, .3f))),
            teleporter(ThreateningTeleport.ID, 1, List.of()),
            teleporter(DimFoldingTheorem.ID, 1, List.of(new Requirement(ThreateningTeleport.ID, .2f))),
            // 原作spaceFluct.setParent(shiftTP, 0)。原作のaddSkillExpと同じく、クリティカルでも習得する。
            teleporter(SpaceFluctuation.ID, 4, List.of(new Requirement(SpaceFluctuation.PARENT, 0))),
            teleporter(PenetrateTeleport.ID, 2, List.of(new Requirement(ThreateningTeleport.ID, .5f))),
            teleporter(MarkTeleport.ID, 2, List.of(new Requirement(ThreateningTeleport.ID, .4f))),
            teleporter(FleshRipping.ID, 3, List.of(new Requirement(MarkTeleport.ID, .5f), new Requirement(PenetrateTeleport.ID, .5f))),
            teleporter(LocationTeleport.ID, 3, List.of(new Requirement(PenetrateTeleport.ID, .8f), new Requirement(MarkTeleport.ID, .8f))),
            teleporter(ShiftTeleport.ID, 4, List.of(new Requirement(LocationTeleport.ID, .5f))),
            teleporter(Flashing.ID, 5, List.of(new Requirement(ShiftTeleport.ID, .8f))),
            // 原作CatVecManip: そこのsetParentはどれも熟練度をまったく求めない。
            vecmanip(DirectedShock.ID, 1, List.of()),
            vecmanip(Groundshock.ID, 1, List.of(new Requirement(DirectedShock.ID, 0))),
            vecmanip(VecAccel.ID, 2, List.of(new Requirement(DirectedShock.ID, 0))),
            vecmanip(VecDeviation.ID, 2, List.of(new Requirement(VecAccel.ID, 0))),
            vecmanip(DirectedBlastwave.ID, 3, List.of(new Requirement(Groundshock.ID, 0))),
            vecmanip(StormWing.ID, 3, List.of(new Requirement(VecAccel.ID, 0))),
            vecmanip(BloodRetrograde.ID, 4, List.of(new Requirement(DirectedBlastwave.ID, 0))),
            vecmanip(VecReflection.ID, 4, List.of(new Requirement(VecDeviation.ID, 0))),
            vecmanip(PlasmaCannon.ID, 5, List.of(new Requirement(StormWing.ID, 0))),
            generic(GenericSkills.BRAIN, 3, List.of()),
            generic(GenericSkills.ADVANCED_BRAIN, 4, List.of(new Requirement(GenericSkills.BRAIN, 0))),
            generic(GenericSkills.MIND, 5, List.of(new Requirement(GenericSkills.ADVANCED_BRAIN, 0))));
    public static final List<Definition> CONTROLLABLE = IMPLEMENTED.stream().filter(s -> !s.passive()).toList();
    private SkillCatalog() {}
    public static Definition find(ResourceLocation id) {
        return IMPLEMENTED.stream().filter(skill -> skill.id().equals(id)).findFirst().orElse(null);
    }
}
