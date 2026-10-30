package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/**
 * 能力開発機の画面（DeveloperScreen）で、習得した技能の熟練度の行とアイコンの周りのリングに何を出すか（GUI-PASSIVE-PROFICIENCY-001）。
 * 表示だけで、データを何も書き換えない。client専用のクラスから分けてあるのは、サーバー側のGameTestから同じ判断を確かめるため。
 * 参加中のサーバーの規則（SyncedAcademyRules）はここでは読まず、画面が渡す（{@link SkillStatusLines}と同じ決まり）。
 * <ul>
 *   <li>Radiation Intensify: 保存された熟練度（常に0）ではなく、実際に効く実効熟練度（最大CP ÷ 参加中のサーバーのLv5の初期CP）と
 *       ダメージの倍率を出し、リングも実効熟練度にする。計算はF4の表示と同じ{@link RadiationIntensify#derivedProficiency(float, float)}と
 *       {@link RadiationIntensify#rate(float)}で、数の書き方（小数1桁の%、小数2桁の倍率）もF4と同じ。サーバーの規則がまだ届いていなければ、
 *       標準の値をサーバーの値として出さず、待っていると書き、リングは描かない。</li>
 *   <li>汎用コース（ブレイン、アドバンスド、マインド）: 熟練度は育たないので、0%を出さず「習得済み（効果有効）」と書き、リングは
 *       習得済みを示す満の輪にする。CPの回復量・回復待ちは参加中のサーバーから送られてこないので、ここでは数値を出さない。</li>
 *   <li>そのほかの技能: これまでどおり保存された熟練度。</li>
 * </ul>
 */
public final class PassiveSkillDisplay {
    private PassiveSkillDisplay() { }
    private static final List<ResourceLocation> COURSES = List.of(GenericSkills.BRAIN, GenericSkills.ADVANCED_BRAIN, GenericSkills.MIND);

    /** 熟練度の行: 翻訳キーと、その引数（文字列）。 */
    public record Line(String key, List<String> args) { }

    public static boolean course(ResourceLocation skill) { return COURSES.contains(skill); }

    /**
     * 習得した技能のリングの進み（0〜1）。level5BaseCpは参加中のサーバーが送ったLv5の初期CPで、まだ届いていなければnull。
     * 習得していない技能には呼ばない（画面は習得していない技能にリングを描かない）。
     */
    public static float ring(PlayerAbilityData data, ResourceLocation skill, @Nullable Float level5BaseCp) {
        if (RadiationIntensify.ID.equals(skill))
            return level5BaseCp == null ? 0 : RadiationIntensify.derivedProficiency(data.getMaxCp(), level5BaseCp);
        if (course(skill)) return 1;
        return data.getProficiency(skill);
    }

    /** 習得した技能の熟練度の行。nullなら、画面はこれまでどおりの行（「スキル習熟度：」と整数の%）を出す。 */
    public static @Nullable Line line(PlayerAbilityData data, ResourceLocation skill, @Nullable Float level5BaseCp) {
        if (RadiationIntensify.ID.equals(skill)) {
            if (level5BaseCp == null) return new Line("academy.developer.ui.effective_exp_waiting", List.of());
            float proficiency = RadiationIntensify.derivedProficiency(data.getMaxCp(), level5BaseCp);
            return new Line("academy.developer.ui.effective_exp", List.of(
                    String.format(Locale.ROOT, "%.1f%%", proficiency * 100), String.format(Locale.ROOT, "%.2f", RadiationIntensify.rate(proficiency))));
        }
        if (course(skill)) return new Line("academy.developer.ui.course_learned", List.of());
        return null;
    }
}
