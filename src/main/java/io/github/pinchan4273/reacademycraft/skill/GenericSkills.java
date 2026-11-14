package io.github.pinchan4273.reacademycraft.skill;

import net.minecraft.resources.ResourceLocation;

/**
 * 原作の汎用コース（WeAthFolD）。パッシブ技能で、スロットのアクションにはならない。
 *
 * <p>1.0.0の後の仕様では、コースは最大CPを増やさない。習得したコース1つごとに
 * CPの回復が増え（{@link #courses}）、習得した最上位のコースがCPとオーバーロードの回復待ちを短くする（{@link #topCourse}）。
 * 上級コースのオーバーロード上限+100は、廃止するかが未決定なので残す。1.0.0の容量のボーナス（ブレイン+1000、上級+1500。
 * 原作の英語の説明は1000と言うがコードは1500）は、1.0.0のセーブを移すときにだけ{@link #legacyCpBonus}で使う。
 */
public final class GenericSkills {
    public static final ResourceLocation BRAIN = ResourceLocation.fromNamespaceAndPath("academy", "brain_course");
    public static final ResourceLocation ADVANCED_BRAIN = ResourceLocation.fromNamespaceAndPath("academy", "brain_course_advanced");
    public static final ResourceLocation MIND = ResourceLocation.fromNamespaceAndPath("academy", "mind_course");
    private GenericSkills() { }
    /**
     * パッシブ技能か（装備できない）。1.4.0から、レジストリの発動の種類（PASSIVE）で決める。本体のパッシブ技能は1.3.0と同じ6つ
     * （3つのコースと、Meltdowner自身のRadiation Intensify、Teleporterの次元折り畳み・空間の揺らぎ）。登録されていない技能はパッシブでない。
     */
    public static boolean isPassive(ResourceLocation id) {
        return io.github.pinchan4273.reacademycraft.ability.RegistryViews.kind(id) == io.github.pinchan4273.reacademycraft.api.ActivationKind.PASSIVE;
    }
    /** 1.0.0がコースで最大CPに加えていた量。1.0.0のセーブを移すときにだけ使う。 */
    public static float legacyCpBonus(PlayerAbilityData d) { return (d.hasLearned(BRAIN) ? 1000 : 0) + (d.hasLearned(ADVANCED_BRAIN) ? 1500 : 0); }
    public static float overloadBonus(PlayerAbilityData d) { return d.hasLearned(ADVANCED_BRAIN) ? 100 : 0; }
    /** 習得した対象コースの数（0〜3）。レベルから推定せず、実際に習得したものを数える。 */
    public static int courses(PlayerAbilityData d) { return (d.hasLearned(BRAIN) ? 1 : 0) + (d.hasLearned(ADVANCED_BRAIN) ? 1 : 0) + (d.hasLearned(MIND) ? 1 : 0); }
    /** 習得した最上位のコース: 0なし、1ブレイン、2アドバンスド、3マインド。 */
    public static int topCourse(PlayerAbilityData d) { return d.hasLearned(MIND) ? 3 : d.hasLearned(ADVANCED_BRAIN) ? 2 : d.hasLearned(BRAIN) ? 1 : 0; }
}
