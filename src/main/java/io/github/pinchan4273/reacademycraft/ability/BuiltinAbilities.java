package io.github.pinchan4273.reacademycraft.ability;

import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.SkillRequirement;
import java.util.List;

/**
 * 本体の4カテゴリと41技能の定義（1.4.0のS2）と、v1.5.0で後ろに足した未元物質（1カテゴリ・9技能、{@link #registerDarkMatter}）。値と並びは公開1.3.0のSkillCatalog・AbilityCategory・GenericSkills・AbilityActionと同じで、
 * CatalogGoldenGameTestsが、1.3.0のソースから取り出した正解の表と照らす。原作の由来の説明は、移す前のSkillCatalog・AbilityCategoryの
 * 注記（git履歴の03f1f238）にある。IDは技能のクラスの定数でなく文字列で書く（登録簿を作る時に技能のクラスを読み込まない）。
 * 発動の種類: 受動はPASSIVE、AbilityActionの押し続けの集合はHELD、切り替えの集合はTOGGLE、ほか（モードを持つ技能を含む）はINSTANT。
 */
final class BuiltinAbilities {
    private BuiltinAbilities() { }

    static AbilityRegistry register(AbilityRegistry registry) {
        var r = registry.registrar(AbilityRegistry.ACADEMY);
        r.category(new CategoryDefinition(AbilityId.parse("academy:electromaster"), AbilityId.parse("academy:factor_electromaster"), List.of(2, 2, 2, 2, 1),
                new Presentation("academy.ability.electromaster", null, null)));
        r.category(new CategoryDefinition(AbilityId.parse("academy:meltdowner"), AbilityId.parse("academy:factor_meltdowner"), List.of(1, 2, 2, 3, 2),
                new Presentation("academy.ability.meltdowner", null, null)));
        r.category(new CategoryDefinition(AbilityId.parse("academy:teleporter"), AbilityId.parse("academy:factor_teleporter"), List.of(1, 2, 2, 1, 1),
                new Presentation("academy.ability.teleporter", null, null)));
        r.category(new CategoryDefinition(AbilityId.parse("academy:vecmanip"), AbilityId.parse("academy:factor_vecmanip"), List.of(2, 2, 2, 2, 1),
                new Presentation("academy.ability.vecmanip", null, null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:arc_gen"), AbilityId.parse("academy:electromaster"), 1, List.of(),
                ActivationKind.INSTANT, new Presentation("academy.skill.arc_gen", "academy.developer.desc.arc_gen", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:charging"), AbilityId.parse("academy:electromaster"), 1, List.of(new SkillRequirement(AbilityId.parse("academy:arc_gen"), 0.3f)),
                ActivationKind.HELD, new Presentation("academy.skill.charging", "academy.developer.desc.charging", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mag_movement"), AbilityId.parse("academy:electromaster"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:arc_gen"), 0f), new SkillRequirement(AbilityId.parse("academy:charging"), 0.7f)),
                ActivationKind.HELD, new Presentation("academy.skill.mag_movement", "academy.developer.desc.mag_movement", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mag_manip"), AbilityId.parse("academy:electromaster"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:mag_movement"), 0.5f)),
                ActivationKind.HELD, new Presentation("academy.skill.mag_manip", "academy.developer.desc.mag_manip", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:body_intensify"), AbilityId.parse("academy:electromaster"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:arc_gen"), 1f), new SkillRequirement(AbilityId.parse("academy:charging"), 1f)),
                ActivationKind.TOGGLE, new Presentation("academy.skill.body_intensify", "academy.developer.desc.body_intensify", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mine_detect"), AbilityId.parse("academy:electromaster"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:mag_manip"), 1f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.mine_detect", "academy.developer.desc.mine_detect", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:thunder_bolt"), AbilityId.parse("academy:electromaster"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:arc_gen"), 0f), new SkillRequirement(AbilityId.parse("academy:charging"), 0.7f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.thunder_bolt", "academy.developer.desc.thunder_bolt", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:railgun"), AbilityId.parse("academy:electromaster"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:thunder_bolt"), 0.3f), new SkillRequirement(AbilityId.parse("academy:mag_manip"), 1f)),
                ActivationKind.HELD, new Presentation("academy.skill.railgun", "academy.developer.desc.railgun", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:thunder_clap"), AbilityId.parse("academy:electromaster"), 5, List.of(new SkillRequirement(AbilityId.parse("academy:thunder_bolt"), 1f)),
                ActivationKind.HELD, new Presentation("academy.skill.thunder_clap", "academy.developer.desc.thunder_clap", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:electron_bomb"), AbilityId.parse("academy:meltdowner"), 1, List.of(),
                ActivationKind.INSTANT, new Presentation("academy.skill.electron_bomb", "academy.developer.desc.electron_bomb", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:rad_intensify"), AbilityId.parse("academy:meltdowner"), 1, List.of(new SkillRequirement(AbilityId.parse("academy:electron_bomb"), 0.5f)),
                ActivationKind.PASSIVE, new Presentation("academy.skill.rad_intensify", "academy.developer.desc.rad_intensify", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:scatter_bomb"), AbilityId.parse("academy:meltdowner"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:electron_bomb"), 0.8f)),
                ActivationKind.HELD, new Presentation("academy.skill.scatter_bomb", "academy.developer.desc.scatter_bomb", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:light_shield"), AbilityId.parse("academy:meltdowner"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:electron_bomb"), 1f)),
                ActivationKind.TOGGLE, new Presentation("academy.skill.light_shield", "academy.developer.desc.light_shield", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:meltdowner"), AbilityId.parse("academy:meltdowner"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:scatter_bomb"), 0.8f), new SkillRequirement(AbilityId.parse("academy:light_shield"), 0.8f)),
                ActivationKind.HELD, new Presentation("academy.skill.meltdowner", "academy.developer.desc.meltdowner", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mine_ray_basic"), AbilityId.parse("academy:meltdowner"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:meltdowner"), 0.3f)),
                ActivationKind.HELD, new Presentation("academy.skill.mine_ray_basic", "academy.developer.desc.mine_ray_basic", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mine_ray_expert"), AbilityId.parse("academy:meltdowner"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:mine_ray_basic"), 0.8f)),
                ActivationKind.HELD, new Presentation("academy.skill.mine_ray_expert", "academy.developer.desc.mine_ray_expert", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mine_ray_luck"), AbilityId.parse("academy:meltdowner"), 5, List.of(new SkillRequirement(AbilityId.parse("academy:mine_ray_expert"), 1f)),
                ActivationKind.HELD, new Presentation("academy.skill.mine_ray_luck", "academy.developer.desc.mine_ray_luck", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:jet_engine"), AbilityId.parse("academy:meltdowner"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:meltdowner"), 1f)),
                ActivationKind.TOGGLE, new Presentation("academy.skill.jet_engine", "academy.developer.desc.jet_engine", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:ray_barrage"), AbilityId.parse("academy:meltdowner"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:meltdowner"), 0.5f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.ray_barrage", "academy.developer.desc.ray_barrage", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:electron_missile"), AbilityId.parse("academy:meltdowner"), 5, List.of(new SkillRequirement(AbilityId.parse("academy:jet_engine"), 0.3f)),
                ActivationKind.TOGGLE, new Presentation("academy.skill.electron_missile", "academy.developer.desc.electron_missile", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:threatening_teleport"), AbilityId.parse("academy:teleporter"), 1, List.of(),
                ActivationKind.HELD, new Presentation("academy.skill.threatening_teleport", "academy.developer.desc.threatening_teleport", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:dim_folding_theorem"), AbilityId.parse("academy:teleporter"), 1, List.of(new SkillRequirement(AbilityId.parse("academy:threatening_teleport"), 0.2f)),
                ActivationKind.PASSIVE, new Presentation("academy.skill.dim_folding_theorem", "academy.developer.desc.dim_folding_theorem", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:space_fluct"), AbilityId.parse("academy:teleporter"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:shift_tp"), 0f)),
                ActivationKind.PASSIVE, new Presentation("academy.skill.space_fluct", "academy.developer.desc.space_fluct", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:penetrate_teleport"), AbilityId.parse("academy:teleporter"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:threatening_teleport"), 0.5f)),
                ActivationKind.HELD, new Presentation("academy.skill.penetrate_teleport", "academy.developer.desc.penetrate_teleport", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mark_teleport"), AbilityId.parse("academy:teleporter"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:threatening_teleport"), 0.4f)),
                ActivationKind.HELD, new Presentation("academy.skill.mark_teleport", "academy.developer.desc.mark_teleport", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:flesh_ripping"), AbilityId.parse("academy:teleporter"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:mark_teleport"), 0.5f), new SkillRequirement(AbilityId.parse("academy:penetrate_teleport"), 0.5f)),
                ActivationKind.HELD, new Presentation("academy.skill.flesh_ripping", "academy.developer.desc.flesh_ripping", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:location_teleport"), AbilityId.parse("academy:teleporter"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:penetrate_teleport"), 0.8f), new SkillRequirement(AbilityId.parse("academy:mark_teleport"), 0.8f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.location_teleport", "academy.developer.desc.location_teleport", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:shift_tp"), AbilityId.parse("academy:teleporter"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:location_teleport"), 0.5f)),
                ActivationKind.HELD, new Presentation("academy.skill.shift_tp", "academy.developer.desc.shift_tp", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:flashing"), AbilityId.parse("academy:teleporter"), 5, List.of(new SkillRequirement(AbilityId.parse("academy:shift_tp"), 0.8f)),
                ActivationKind.TOGGLE, new Presentation("academy.skill.flashing", "academy.developer.desc.flashing", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:dir_shock"), AbilityId.parse("academy:vecmanip"), 1, List.of(),
                ActivationKind.HELD, new Presentation("academy.skill.dir_shock", "academy.developer.desc.dir_shock", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:ground_shock"), AbilityId.parse("academy:vecmanip"), 1, List.of(new SkillRequirement(AbilityId.parse("academy:dir_shock"), 0f)),
                ActivationKind.HELD, new Presentation("academy.skill.ground_shock", "academy.developer.desc.ground_shock", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:vec_accel"), AbilityId.parse("academy:vecmanip"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:dir_shock"), 0f)),
                ActivationKind.HELD, new Presentation("academy.skill.vec_accel", "academy.developer.desc.vec_accel", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:vec_deviation"), AbilityId.parse("academy:vecmanip"), 2, List.of(new SkillRequirement(AbilityId.parse("academy:vec_accel"), 0f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.vec_deviation", "academy.developer.desc.vec_deviation", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:dir_blast"), AbilityId.parse("academy:vecmanip"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:ground_shock"), 0f)),
                ActivationKind.HELD, new Presentation("academy.skill.dir_blast", "academy.developer.desc.dir_blast", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:storm_wing"), AbilityId.parse("academy:vecmanip"), 3, List.of(new SkillRequirement(AbilityId.parse("academy:vec_accel"), 0f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.storm_wing", "academy.developer.desc.storm_wing", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:blood_retro"), AbilityId.parse("academy:vecmanip"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:dir_blast"), 0f)),
                ActivationKind.HELD, new Presentation("academy.skill.blood_retro", "academy.developer.desc.blood_retro", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:vec_reflection"), AbilityId.parse("academy:vecmanip"), 4, List.of(new SkillRequirement(AbilityId.parse("academy:vec_deviation"), 0f)),
                ActivationKind.INSTANT, new Presentation("academy.skill.vec_reflection", "academy.developer.desc.vec_reflection", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:plasma_cannon"), AbilityId.parse("academy:vecmanip"), 5, List.of(new SkillRequirement(AbilityId.parse("academy:storm_wing"), 0f)),
                ActivationKind.HELD, new Presentation("academy.skill.plasma_cannon", "academy.developer.desc.plasma_cannon", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:brain_course"), null, 3, List.of(),
                ActivationKind.PASSIVE, new Presentation("academy.skill.brain_course", "academy.developer.desc.brain_course", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:brain_course_advanced"), null, 4, List.of(new SkillRequirement(AbilityId.parse("academy:brain_course"), 0f)),
                ActivationKind.PASSIVE, new Presentation("academy.skill.brain_course_advanced", "academy.developer.desc.brain_course_advanced", null)));
        r.skill(new SkillDefinition(AbilityId.parse("academy:mind_course"), null, 5, List.of(new SkillRequirement(AbilityId.parse("academy:brain_course_advanced"), 0f)),
                ActivationKind.PASSIVE, new Presentation("academy.skill.mind_course", "academy.developer.desc.mind_course", null)));
        registerDarkMatter(r);
        return registry;
    }

    /**
     * v1.5.0の未元物質（採用仕様・版41の§1）。既存の41技能・4カテゴリの並びと条件を変えないよう、すべての後ろに登録する。
     * 能動技能の数はレベル1〜5で2・2・2・1・1（§2）。人体生成は受動（Lv5）。前提の熟練度は§1の表のとおり。
     * 習得できるのは実装済みの技能だけ（{@link io.github.pinchan4273.reacademycraft.skill.DarkMatter#learnable}）。
     */
    private static void registerDarkMatter(io.github.pinchan4273.reacademycraft.api.AbilityRegistrar r) {
        var dm = AbilityId.parse("academy:dark_matter");
        r.category(new CategoryDefinition(dm, AbilityId.parse("academy:factor_dark_matter"), List.of(2, 2, 2, 1, 1),
                new Presentation("academy.ability.dark_matter", null, null)));
        dmSkill(r, dm, "white_wing_stab", 1, List.of(), ActivationKind.HELD);
        // 白翼飛行は受動（2026-11-03のユーザーの決定。翼の展開中、設定に応じたジャンプ操作（既定は長押し、2回押しも選べる）で離陸する）。キーに割り当てない。
        dmSkill(r, dm, "white_wing_flight", 1, List.of(), ActivationKind.PASSIVE);
        dmSkill(r, dm, "dark_matter_ray", 2, List.of(new SkillRequirement(AbilityId.parse("academy:white_wing_stab"), 0.25f)), ActivationKind.TOGGLE);
        dmSkill(r, dm, "white_wing_guard", 2, List.of(new SkillRequirement(AbilityId.parse("academy:white_wing_flight"), 0.25f)), ActivationKind.TOGGLE);
        dmSkill(r, dm, "dark_matter_air_cannon", 3, List.of(new SkillRequirement(AbilityId.parse("academy:dark_matter_ray"), 0.25f)), ActivationKind.INSTANT);
        // 未元装甲は能力使用モードの間だけ働く受動技能（2026-11-03のユーザーの決定。以前はトグル）。
        dmSkill(r, dm, "dark_matter_armor", 3, List.of(new SkillRequirement(AbilityId.parse("academy:white_wing_guard"), 0.5f)), ActivationKind.PASSIVE);
        // v1.5.1: 押し続け型（短く押して離すと生成、長押しで全個体の回収。DarkMatterBeetles）。
        dmSkill(r, dm, "dark_matter_beetle", 4, List.of(new SkillRequirement(AbilityId.parse("academy:dark_matter_air_cannon"), 0.25f)), ActivationKind.HELD);
        dmSkill(r, dm, "heaven_manifest", 5, List.of(new SkillRequirement(AbilityId.parse("academy:dark_matter_beetle"), 0.25f)), ActivationKind.TOGGLE);
        dmSkill(r, dm, "dark_matter_body", 5, List.of(new SkillRequirement(AbilityId.parse("academy:dark_matter_beetle"), 0.5f)), ActivationKind.PASSIVE);
    }
    private static void dmSkill(io.github.pinchan4273.reacademycraft.api.AbilityRegistrar r, AbilityId category, String path, int level, List<SkillRequirement> requirements, ActivationKind kind) {
        r.skill(new SkillDefinition(AbilityId.parse("academy:" + path), category, level, requirements, kind,
                new Presentation("academy.skill." + path, "academy.developer.desc." + path, null)));
    }
}
