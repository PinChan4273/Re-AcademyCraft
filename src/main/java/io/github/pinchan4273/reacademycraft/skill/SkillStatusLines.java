package io.github.pinchan4273.reacademycraft.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/**
 * F4のデバッグ表示（原作DebugConsoleのSHOW_EXP）の技能の行。表示だけで、データを何も書き換えない。
 * client専用のクラスから分けてあるのは、サーバー側のGameTestから同じ行を確かめるため。参加中のサーバーの規則
 * （SyncedAcademyRules）はここでは読まず、clientのDebugConsoleが渡す（サーバー側のコードがclientの値を読まないための検査に従う）。
 *
 * <p>2026-10-16の追加指示（パッシブ技能のF4表示）:
 * <ul>
 *   <li>能力カテゴリの技能は従来どおり熟練度を出す。Radiation Intensifyだけは、保存された熟練度ではなく実際に効く
 *       実効熟練度（最大CP ÷ 参加中のサーバーのLv5の初期CP）と、その倍率を出す。</li>
 *   <li>汎用コース（ブレイン、アドバンスド、マインド）は、熟練度ではなく習得状態と強化内容を出す。CPの回復量と回復待ちは
 *       サーバーの設定でサーバーから送られてこないので、標準設定の値として印（*）を付ける。最大過負荷+100は設定に無い固定の値。</li>
 * </ul>
 */
public final class SkillStatusLines {
    /** 標準設定の値（AcademyConfigの既定値）。サーバーの実際の値ではない。 */
    public static final int STANDARD_CP_PER_COURSE = 50;
    public static final String STANDARD_NOTE = "* standard settings; the server's may differ";
    /** 参加中のサーバーの規則がまだ届いていないときのRadiation Intensifyの行（QAの所見FQ-01）。標準の値をサーバーの値のように出さない。 */
    public static final String RADIATION_WAITING = "learned; waiting for the server's rules";
    private static final List<ResourceLocation> COURSES = List.of(GenericSkills.BRAIN, GenericSkills.ADVANCED_BRAIN, GenericSkills.MIND);
    private SkillStatusLines() { }

    /**
     * "Skill status"の見出しより後の行。能力が無ければ何も無い。level5BaseCpは参加中のサーバーが送ったLv5の初期CPで、
     * まだ届いていなければnull（そのときRadiation Intensifyは数値を出さず、届くのを待っていると書く）。
     */
    public static List<String> of(PlayerAbilityData data, Float level5BaseCp) {
        var texts = new ArrayList<String>();
        if (!data.hasAbility()) return texts;
        for (var skill : SkillCatalog.IMPLEMENTED) {
            if (!data.getAbility().equals(skill.category())) continue;
            var sb = name(skill.id());
            if (!data.hasLearned(skill.id())) sb.append("[not learned]");
            else if (RadiationIntensify.ID.equals(skill.id()))
                sb.append(level5BaseCp == null ? RADIATION_WAITING : radiation(data.getMaxCp(), level5BaseCp));
            else sb.append(String.format(Locale.ROOT, "%.1f", data.getProficiency(skill.id()) * 100)).append('%');
            texts.add(sb.toString());
        }
        texts.add("Generic courses");
        for (var course : COURSES) {
            var sb = name(course);
            if (!data.hasLearned(course)) sb.append("[not learned]");
            else {
                sb.append("learned: CP regen +").append(STANDARD_CP_PER_COURSE).append("/s*");
                if (GenericSkills.ADVANCED_BRAIN.equals(course)) sb.append(", max overload +100");
            }
            texts.add(sb.toString());
        }
        // 回復量は習得したコースの数で足し合わせ、回復待ちは習得した最上位のコースだけで決まる（GenericSkillsと同じ数え方）。
        int courses = GenericSkills.courses(data), top = GenericSkills.topCourse(data);
        if (courses > 0) {
            texts.add(String.format(Locale.ROOT, "Courses: %d -> CP regen +%d/s*, wait tier %s", courses, courses * STANDARD_CP_PER_COURSE,
                    COURSES.get(top - 1).getPath()));
            texts.add(String.format(Locale.ROOT, "Wait*: CP %dt, overload %dt (no course: %dt, %dt)",
                    io.github.pinchan4273.reacademycraft.config.AcademyConfig.DEFAULT_CP_RECOVER_WAIT.get(top),
                    io.github.pinchan4273.reacademycraft.config.AcademyConfig.DEFAULT_OVERLOAD_RECOVER_WAIT.get(top),
                    io.github.pinchan4273.reacademycraft.config.AcademyConfig.DEFAULT_CP_RECOVER_WAIT.get(0),
                    io.github.pinchan4273.reacademycraft.config.AcademyConfig.DEFAULT_OVERLOAD_RECOVER_WAIT.get(0)));
            texts.add(STANDARD_NOTE);
        }
        return texts;
    }

    /** Radiation Intensifyの行の値: 実効熟練度と倍率。referenceは参加中のサーバーのLv5の初期CP。 */
    public static String radiation(float maxCp, float reference) {
        float proficiency = RadiationIntensify.derivedProficiency(maxCp, reference);
        return String.format(Locale.ROOT, "%.1f%% / Damage x%.2f", proficiency * 100, RadiationIntensify.rate(proficiency));
    }

    /** 原作の書き方: 技能名のあとを30文字まで空白で埋める。 */
    private static StringBuilder name(ResourceLocation id) {
        var sb = new StringBuilder(id.getPath());
        for (int i = 0; i < 30 - id.getPath().length(); ++i) sb.append(' ');
        return sb;
    }
}
