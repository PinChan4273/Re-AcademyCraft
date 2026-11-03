package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * v1.5.0の未元物質（採用仕様・版41）の技能のIDと、この版で習得できる範囲。登録は{@code ability/BuiltinAbilities}が、
 * 既存の4カテゴリ・41技能の後ろに行う。
 * <p>
 * 9技能すべてを登録するのは、技能の数（41＋9＝50）に対する登録・学習の通知・保存・同期の上限を、最初から正しい数で確かめるため
 * （指示書C1）。まだ実装していない技能は、開発機の木に出るが習得できない（{@link #learnable}）。未完成の技能を完成済みとして
 * 習得させない（指示書C3）。
 */
public final class DarkMatter {
    public static final ResourceLocation CATEGORY = id("dark_matter");
    /** Lv1 白翼刺突（押し続けで連撃）。 */
    public static final ResourceLocation STAB = id("white_wing_stab");
    /** Lv1 白翼飛行。翼の展開中、設定に応じたジャンプ操作で離陸する受動技能（2026-11-03のユーザーの決定でトグルから変更）。 */
    public static final ResourceLocation FLIGHT = id("white_wing_flight");
    /** Lv2 未元光線（トグル照射）。 */
    public static final ResourceLocation RAY = id("dark_matter_ray");
    /** Lv2 白翼自動防御（トグル有効化・自動防御）。 */
    public static final ResourceLocation GUARD = id("white_wing_guard");
    /** Lv3 未元空気砲（単発）。 */
    public static final ResourceLocation AIR_CANNON = id("dark_matter_air_cannon");
    /** Lv3 未元装甲。能力使用モード中に被ダメージを自動で軽減する受動技能（2026-11-03のユーザーの決定でトグルから変更）。 */
    public static final ResourceLocation ARMOR = id("dark_matter_armor");
    /** Lv4 未元物質・甲虫生成。 */
    public static final ResourceLocation BEETLE = id("dark_matter_beetle");
    /** Lv5 天界発現（トグル）。 */
    public static final ResourceLocation HEAVEN = id("heaven_manifest");
    /** Lv5 未元物質・人体生成（受動・致死時発動）。 */
    public static final ResourceLocation BODY = id("dark_matter_body");
    /** 登録の順（§1の表の順）。 */
    public static final List<ResourceLocation> SKILLS = List.of(STAB, FLIGHT, RAY, GUARD, AIR_CANNON, ARMOR, BEETLE, HEAVEN, BODY);

    /**
     * この版で習得できる未元物質の技能。実装を足したらここへ足す（同時にDarkMatterGameTestsの期待値も変える）。
     * クラウドの先行実装C3の時点では白翼刺突だけ。
     */
    private static final Set<ResourceLocation> IMPLEMENTED = Set.of(STAB, FLIGHT, RAY, GUARD, AIR_CANNON, ARMOR, BEETLE, BODY, HEAVEN);

    private DarkMatter() { }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("academy", path); }

    /** 未元物質の技能か。 */
    public static boolean owns(ResourceLocation skill) { return SKILLS.contains(skill); }
    /** 学習機で習得させてよいか。未元物質以外の技能は常にtrue（既存の条件だけで決まる）。 */
    public static boolean learnable(ResourceLocation skill) { return !owns(skill) || IMPLEMENTED.contains(skill); }
    /**
     * 受動技能の「同じレベルの技能を習得済み」という条件を掛けない、未元物質の受動技能（人体生成§16と、受動になった白翼飛行・未元装甲）。
     * 開発機の条件のアイコンも出さない。
     */
    public static boolean unconditionalPassive(ResourceLocation skill) { return BODY.equals(skill) || FLIGHT.equals(skill) || ARMOR.equals(skill); }
    /** 受動でも使って熟練度を得る技能（白翼飛行・未元装甲。2026-11-03に受動になった物）。 */
    public static boolean passiveGains(ResourceLocation skill) { return FLIGHT.equals(skill) || ARMOR.equals(skill); }
}
