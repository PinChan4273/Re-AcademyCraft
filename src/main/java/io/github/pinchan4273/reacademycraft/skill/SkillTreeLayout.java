package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * 原作の開発機の技能の木が各技能を置く場所: CategoryのコンストラクタのsetPosition(guiX, guiY)（257x139の木の領域内）、線を引く親
 * （setParentのみ。addSkillDepは線を引かない）、Skill.getHintIcon（textures/abilities/<category>/skills/<name>.png）。
 * 汎用コース（VanillaCategories.addGenericSkills）はすべてのカテゴリの木に入る。
 */
public final class SkillTreeLayout {
    public record Node(ResourceLocation skill, float x, float y, @Nullable ResourceLocation parent, ResourceLocation icon) { }

    private static Node node(ResourceLocation skill, String category, float x, float y, @Nullable ResourceLocation parent) {
        return new Node(skill, x, y, parent, ResourceLocation.fromNamespaceAndPath("academy",
                "textures/abilities/" + category + "/skills/" + skill.getPath() + ".png"));
    }
    private static final List<Node> GENERIC = List.of(
            node(GenericSkills.BRAIN, "generic", 30, 110, null),
            node(GenericSkills.ADVANCED_BRAIN, "generic", 115, 110, GenericSkills.BRAIN),
            node(GenericSkills.MIND, "generic", 205, 110, GenericSkills.ADVANCED_BRAIN));
    private static final Map<ResourceLocation, List<Node>> TREES = Map.of(
            ArcGen.CATEGORY, List.of(
                    node(ArcGen.ID, "electromaster", 24, 46, null),
                    node(CurrentCharging.ID, "electromaster", 55, 18, ArcGen.ID),
                    node(BodyIntensify.ID, "electromaster", 97, 15, ArcGen.ID),
                    node(MineDetect.ID, "electromaster", 225, 12, MagneticManipulation.ID),
                    node(MagneticMovement.ID, "electromaster", 137, 35, ArcGen.ID),
                    node(ThunderBolt.ID, "electromaster", 86, 67, ArcGen.ID),
                    node(Railgun.ID, "electromaster", 164, 59, ThunderBolt.ID),
                    node(ThunderClap.ID, "electromaster", 204, 80, ThunderBolt.ID),
                    node(MagneticManipulation.ID, "electromaster", 204, 33, MagneticMovement.ID)),
            AbilityCategory.MELTDOWNER.id(), List.of(
                    node(ElectronBomb.ID, "meltdowner", 15, 45, null),
                    node(RadiationIntensify.ID, "meltdowner", 35, 75, ElectronBomb.ID),
                    node(ScatterBomb.ID, "meltdowner", 70, 50, ElectronBomb.ID),
                    node(LightShield.ID, "meltdowner", 55, 15, ElectronBomb.ID),
                    node(Meltdowner.ID, "meltdowner", 115, 40, ScatterBomb.ID),
                    node(MineRay.BASIC.id(), "meltdowner", 140, 70, Meltdowner.ID),
                    node(RayBarrage.ID, "meltdowner", 140, 10, Meltdowner.ID),
                    node(JetEngine.ID, "meltdowner", 170, 32, Meltdowner.ID),
                    node(MineRay.EXPERT.id(), "meltdowner", 172, 70, MineRay.BASIC.id()),
                    node(MineRay.LUCK.id(), "meltdowner", 205, 82, MineRay.EXPERT.id()),
                    node(ElectronMissile.ID, "meltdowner", 210, 35, JetEngine.ID)),
            AbilityCategory.TELEPORTER.id(), List.of(
                    node(ThreateningTeleport.ID, "teleporter", 14, 42, null),
                    node(DimFoldingTheorem.ID, "teleporter", 50, 75, ThreateningTeleport.ID),
                    node(PenetrateTeleport.ID, "teleporter", 60, 46, ThreateningTeleport.ID),
                    node(MarkTeleport.ID, "teleporter", 70, 16, ThreateningTeleport.ID),
                    node(FleshRipping.ID, "teleporter", 130, 12, MarkTeleport.ID),
                    node(LocationTeleport.ID, "teleporter", 118, 50, PenetrateTeleport.ID),
                    node(ShiftTeleport.ID, "teleporter", 175, 47, LocationTeleport.ID),
                    node(SpaceFluctuation.ID, "teleporter", 160, 80, ShiftTeleport.ID),
                    node(Flashing.ID, "teleporter", 220, 20, ShiftTeleport.ID)),
            AbilityCategory.VECMANIP.id(), List.of(
                    node(DirectedShock.ID, "vecmanip", 16, 45, null),
                    node(Groundshock.ID, "vecmanip", 64, 85, DirectedShock.ID),
                    node(VecAccel.ID, "vecmanip", 76, 40, DirectedShock.ID),
                    node(VecDeviation.ID, "vecmanip", 145, 53, VecAccel.ID),
                    node(DirectedBlastwave.ID, "vecmanip", 136, 80, Groundshock.ID),
                    node(StormWing.ID, "vecmanip", 130, 20, VecAccel.ID),
                    node(BloodRetrograde.ID, "vecmanip", 204, 83, DirectedBlastwave.ID),
                    node(VecReflection.ID, "vecmanip", 210, 50, VecDeviation.ID),
                    node(PlasmaCannon.ID, "vecmanip", 175, 14, StormWing.ID)));

    private SkillTreeLayout() { }

    /** カテゴリの木と、その後の汎用コース。原作のaddSkillの順。無ければ空。 */
    public static List<Node> tree(@Nullable ResourceLocation category) {
        var own = category == null ? null : TREES.get(category);
        if (own == null) return List.of();
        return java.util.stream.Stream.concat(own.stream(), GENERIC.stream()).toList();
    }
    @Nullable public static Node node(ResourceLocation skill) {
        for (var tree : TREES.values()) for (var n : tree) if (n.skill().equals(skill)) return n;
        for (var n : GENERIC) if (n.skill().equals(skill)) return n;
        return null;
    }
    /** いずれかの木に場所を持つすべての技能。 */
    public static Map<ResourceLocation, Node> all() {
        return java.util.stream.Stream.concat(TREES.values().stream().flatMap(List::stream), GENERIC.stream())
                .collect(Collectors.toMap(Node::skill, n -> n));
    }
    /** 原作Category.getDeveloperIcon: textures/guis/icons/icon_<category>.png。 */
    public static ResourceLocation categoryIcon(@Nullable ResourceLocation category) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/icons/icon_"
                + (category == null ? "nocategory" : category.getPath()) + ".png");
    }
}
