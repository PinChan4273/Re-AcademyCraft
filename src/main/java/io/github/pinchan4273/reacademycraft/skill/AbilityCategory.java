package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 登録された能力カテゴリ。原作Category/CategoryManager（WeAthFolD）を、移植版が実際に分岐に使うデータだけに絞ったもの:
 * 取得因子のアイテムと、レベルごとの原作の操作可能な技能の数。
 * 1.5.2から、因子なしの初回の取得と磁気コイルでの変更のランダムの抽選に参加するか（randomAcquire）も持つ。
 */
public record AbilityCategory(Identifier id, Supplier<Item> factor, List<Integer> legacyControllable, boolean randomAcquire) {
    public AbilityCategory { legacyControllable = List.copyOf(legacyControllable); }
    /** 抽選に参加しないカテゴリ。 */
    public AbilityCategory(Identifier id, Supplier<Item> factor, List<Integer> legacyControllable) {
        this(id, factor, legacyControllable, false);
    }

    /**
     * 原作getLevelTotalExp()は、そのレベルでカテゴリが持つ操作可能な技能をすべて数えるので、レベルアップの条件は
     * カテゴリの移植の進み具合に依存しない。{@link SkillCatalog#IMPLEMENTED}から導かずカテゴリごとに宣言しているのは、
     * カテゴリが一部しか移植されていない間にレベルアップが黙って安くなるのを防ぐため。
     */
    public int legacyControllable(int level) {
        return level >= 1 && level <= legacyControllable.size() ? legacyControllable.get(level - 1) : 0;
    }

    /** 因子のアイテムがあるか（1.4.0。外部のカテゴリは因子を持たないことがある）。 */
    public boolean hasFactor() { var item = factor.get(); return item != null && item != net.minecraft.world.item.Items.AIR; }

    /** 名前の翻訳キー（1.4.0からレジストリの表示の情報。本体のカテゴリは1.3.0と同じ{@code academy.ability.<path>}）。 */
    public String translation() { return io.github.pinchan4273.reacademycraft.ability.RegistryViews.categoryNameKey(id); }

    public static final AbilityCategory ELECTROMASTER = new AbilityCategory(ArcGen.CATEGORY,
            AcademyContent.FACTOR_ELECTROMASTER, List.of(2, 2, 2, 2, 1), true);
    /**
     * 原作CatMeltdowner: electronBomb、次にscatterBomb/lightShield、meltdowner/mineRayBasic、rayBarrage/jetEngine/mineRayExpert、
     * mineRayLuck/electronMissile。radIntensifyはこのカテゴリで唯一canControl = falseの技能なので、レベル1は2ではなく1と数える。
     * レベルアップはカテゴリ全体で割るので、数は原作のもの。11個すべて移植済み。
     */
    public static final AbilityCategory MELTDOWNER = new AbilityCategory(
            Identifier.fromNamespaceAndPath("academy", "meltdowner"),
            AcademyContent.FACTOR_MELTDOWNER, List.of(1, 2, 2, 3, 2), true);
    /**
     * 原作CatTeleporter: threateningTP、次にpenetrateTP/markTP、fleshRipping/locTP、shiftTPのみ（spaceFluctはパッシブ）、
     * flashingのみ。dimFoldingもcanControl = falseの技能なので、レベル1は2ではなく1と数える。数は移植の進み具合によらず原作のもの。
     */
    public static final AbilityCategory TELEPORTER = new AbilityCategory(
            Identifier.fromNamespaceAndPath("academy", "teleporter"),
            AcademyContent.FACTOR_TELEPORTER, List.of(1, 2, 2, 1, 1), true);
    /**
     * 原作CatVecManip: dirShock/groundShock、vecAccel/vecDeviation、dirBlast/stormWing、bloodRetro/vecReflection、plasmaCannon。
     * 9個すべて操作可能。
     */
    public static final AbilityCategory VECMANIP = new AbilityCategory(
            Identifier.fromNamespaceAndPath("academy", "vecmanip"),
            AcademyContent.FACTOR_VECMANIP, List.of(2, 2, 2, 2, 1), true);
    /**
     * v1.5.0の未元物質: 白翼刺突/白翼飛行、未元光線/白翼自動防御、未元空気砲/未元装甲、甲虫生成、天界発現。
     * 人体生成はパッシブなので数えない。既存の4つの後ろに置く。
     * 白翼飛行・未元装甲はパッシブにしたが、使って熟練度と経験値を得続けるので、レベル1・3は2のまま数える（レベルアップを安くしない）。
     * 1.5.2で未元装甲を廃止し、レベル3の技能は未元空気砲だけになったが、レベル3の2はそのまま残す。この値は昇級の
     * 必要経験の閾値にも使うので、技能が減ったことだけで Lv3→4 を安くしない（成長の速さを変えるなら別の仕様にする）。
     */
    public static final AbilityCategory DARK_MATTER = new AbilityCategory(DarkMatter.CATEGORY,
            AcademyContent.FACTOR_DARK_MATTER, List.of(2, 2, 2, 1, 1), true);
    private static final List<AbilityCategory> BUILT_IN = List.of(ELECTROMASTER, MELTDOWNER, TELEPORTER, VECMANIP, DARK_MATTER);
    /**
     * 登録されたカテゴリ（1.4.0から、レジストリの読み取りビュー）。本体の4つは上の定数そのもの（ほかのコードが定数と比べる）で、並びは1.3.0のまま。
     * 外部のアドオンのカテゴリは、その定義から作った物が、登録の順に本体の後ろに並ぶ（因子のアイテムが無い・見つからなければ空気）。
     */
    public static final List<AbilityCategory> REGISTERED = io.github.pinchan4273.reacademycraft.ability.RegistryViews.list(
            io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE::categories, AbilityCategory::adapt);
    private static AbilityCategory adapt(io.github.pinchan4273.reacademycraft.api.CategoryDefinition definition) {
        var id = io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(definition.id());
        for (var builtIn : BUILT_IN) if (builtIn.id().equals(id)) return builtIn;
        var factor = definition.factorItem() == null ? null : io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(definition.factorItem());
        return new AbilityCategory(id, () -> factor == null ? net.minecraft.world.item.Items.AIR
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(factor), definition.controllablePerLevel(),
                definition.randomAcquire());
    }

    public static AbilityCategory find(Identifier id) {
        return REGISTERED.stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    /** 原作の取得因子が与えるカテゴリ。スタックが因子でなければnull。 */
    public static AbilityCategory byFactor(ItemStack stack) {
        return stack.isEmpty() ? null
                : REGISTERED.stream().filter(category -> stack.is(category.factor().get())).findFirst().orElse(null);
    }

    /**
     * 因子なしの初回の取得・磁気コイルでの変更の、ランダムの抽選の候補（1.5.2）。登録の順で、抽選に参加するカテゴリだけ。
     * except のカテゴリ（変更では今のカテゴリ）は除く。null なら除かない。
     */
    public static List<AbilityCategory> randomCandidates(@javax.annotation.Nullable Identifier except) {
        return randomCandidates(REGISTERED, except);
    }
    /** {@link #randomCandidates(Identifier)}を、与えた一覧から選ぶ（テストで、登録していないカテゴリの参加・不参加を確かめるため）。 */
    public static List<AbilityCategory> randomCandidates(List<AbilityCategory> from, @javax.annotation.Nullable Identifier except) {
        return from.stream().filter(category -> category.randomAcquire() && !category.id().equals(except)).toList();
    }
}
