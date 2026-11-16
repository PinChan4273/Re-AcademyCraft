package io.github.pinchan4273.reacademycraft.skill;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * v1.5.0の未元物質の技能のIDと、この版で習得できる範囲。登録は{@code ability/BuiltinAbilities}が、
 * 既存の4カテゴリ・41技能の後ろに行う（Forge v1.5.0 2b1a8119のskill/DarkMatterと同じ意味）。
 * <p>
 * 9技能すべてを登録するのは、技能の数（41＋9＝50）に対する登録・学習の通知・保存・同期の上限を、最初から正しい数で確かめるため。
 * まだ実装していない技能は、開発機の木に出るが習得できない（{@link #learnable}）。未完成の技能を完成済みとして
 * 習得させない。
 */
public final class DarkMatter {
    public static final Identifier CATEGORY = id("dark_matter");
    /** Lv1 白翼刺突（押し続けで連撃）。 */
    public static final Identifier STAB = id("white_wing_stab");
    /** Lv1 白翼飛行。翼の展開中、設定に応じたジャンプ操作で離陸するパッシブ技能。 */
    public static final Identifier FLIGHT = id("white_wing_flight");
    /** Lv2 未元光線（トグル照射）。 */
    public static final Identifier RAY = id("dark_matter_ray");
    /** Lv2 白翼自動防御（トグル有効化・自動防御）。 */
    public static final Identifier GUARD = id("white_wing_guard");
    /** Lv3 未元空気砲（単発）。 */
    public static final Identifier AIR_CANNON = id("dark_matter_air_cannon");
    /**
     * 廃止した Lv3 未元装甲の旧ID（1.5.2: 翼がパッシブにできるので、未元装甲を廃止して翼の防御に集約する）。
     * 技能としては登録せず、習得も表示も防御もしない。古いデータの習得・熟練度の記録を読み書きで残すためだけに名前を持つ
     * （{@link PlayerAbilityData#load}は登録の無いIDも読む。移行は特別扱いなし、記録は消さない）。
     */
    public static final Identifier ARMOR = id("dark_matter_armor");
    /** 廃止した本体の技能の旧ID（1.5.2）。習得・熟練度の記録は残すが、プリセットのスロットには戻さない（{@link PlayerAbilityData#load}）。 */
    private static final Set<Identifier> RETIRED = Set.of(ARMOR);
    /** Lv4 未元物質・甲虫生成。 */
    public static final Identifier BEETLE = id("dark_matter_beetle");
    /** Lv5 天界発現（トグル）。 */
    public static final Identifier HEAVEN = id("heaven_manifest");
    /** Lv5 未元物質・人体生成（パッシブ・致死時発動）。 */
    public static final Identifier BODY = id("dark_matter_body");
    /** 登録の順（1.5.2 で未元装甲を除いた8技能）。 */
    public static final List<Identifier> SKILLS = List.of(STAB, FLIGHT, RAY, GUARD, AIR_CANNON, BEETLE, HEAVEN, BODY);

    /**
     * この版で習得できる未元物質の技能。実装を足したらここへ足す（同時にDarkMatterFoundationGameTestsの期待値も変える）。
     * Forge v1.5.0の公開版と同じく9技能すべて（26.2では第3便で9技能の挙動を移した）。
     */
    private static final Set<Identifier> IMPLEMENTED = Set.of(STAB, FLIGHT, RAY, GUARD, AIR_CANNON, BEETLE, BODY, HEAVEN);

    private DarkMatter() { }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("academy", path); }

    /** 廃止した本体の技能の旧IDか。外部の Mod の、今は登録の無い技能は含まない（入れ直したときのためにスロットを残す今の扱いのまま）。 */
    public static boolean retired(Identifier skill) { return RETIRED.contains(skill); }
    /** 未元物質の技能か。 */
    public static boolean owns(Identifier skill) { return SKILLS.contains(skill); }
    /** 学習機で習得させてよいか。未元物質以外の技能は常にtrue（既存の条件だけで決まる）。 */
    public static boolean learnable(Identifier skill) { return !owns(skill) || IMPLEMENTED.contains(skill); }
    /**
     * パッシブ技能の「同じレベルの技能を習得済み」という条件を掛けない、未元物質のパッシブ技能（人体生成と、パッシブになった白翼飛行）。
     * 開発機の条件のアイコンも出さない。
     */
    public static boolean unconditionalPassive(Identifier skill) { return BODY.equals(skill) || FLIGHT.equals(skill); }
    /** パッシブでも使って熟練度を得る技能（白翼飛行。パッシブになった物）。 */
    public static boolean passiveGains(Identifier skill) { return FLIGHT.equals(skill); }
}
