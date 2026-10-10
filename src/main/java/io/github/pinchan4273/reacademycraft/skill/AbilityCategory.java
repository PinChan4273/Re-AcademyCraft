package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 登録された能力カテゴリ。原作Category/CategoryManager（WeAthFolD）を、移植版が実際に分岐に使うデータだけに絞ったもの:
 * 取得因子のアイテムと、レベルごとの原作の操作可能な技能の数。
 */
public record AbilityCategory(ResourceLocation id, Supplier<Item> factor, List<Integer> legacyControllable) {
    public AbilityCategory { legacyControllable = List.copyOf(legacyControllable); }

    /**
     * 原作getLevelTotalExp()は、そのレベルでカテゴリが持つ操作可能な技能をすべて数えるので、レベルアップの条件は
     * カテゴリの移植の進み具合に依存しない。{@link SkillCatalog#IMPLEMENTED}から導かずカテゴリごとに宣言しているのは、
     * カテゴリが一部しか移植されていない間にレベルアップが黙って安くなるのを防ぐため。
     */
    public int legacyControllable(int level) {
        return level >= 1 && level <= legacyControllable.size() ? legacyControllable.get(level - 1) : 0;
    }

    public String translation() { return "academy.ability." + id.getPath(); }

    public static final AbilityCategory ELECTROMASTER = new AbilityCategory(ArcGen.CATEGORY,
            AcademyContent.FACTOR_ELECTROMASTER, List.of(2, 2, 2, 2, 1));
    /**
     * 原作CatMeltdowner: electronBomb、次にscatterBomb/lightShield、meltdowner/mineRayBasic、rayBarrage/jetEngine/mineRayExpert、
     * mineRayLuck/electronMissile。radIntensifyはこのカテゴリで唯一canControl = falseの技能なので、レベル1は2ではなく1と数える。
     * レベルアップはカテゴリ全体で割るので、数は原作のもの。11個すべて移植済み。
     */
    public static final AbilityCategory MELTDOWNER = new AbilityCategory(
            ResourceLocation.fromNamespaceAndPath("academy", "meltdowner"),
            AcademyContent.FACTOR_MELTDOWNER, List.of(1, 2, 2, 3, 2));
    /**
     * 原作CatTeleporter: threateningTP、次にpenetrateTP/markTP、fleshRipping/locTP、shiftTPのみ（spaceFluctは受動）、
     * flashingのみ。dimFoldingもcanControl = falseの技能なので、レベル1は2ではなく1と数える。数は移植の進み具合によらず原作のもの。
     */
    public static final AbilityCategory TELEPORTER = new AbilityCategory(
            ResourceLocation.fromNamespaceAndPath("academy", "teleporter"),
            AcademyContent.FACTOR_TELEPORTER, List.of(1, 2, 2, 1, 1));
    /**
     * 原作CatVecManip: dirShock/groundShock、vecAccel/vecDeviation、dirBlast/stormWing、bloodRetro/vecReflection、plasmaCannon。
     * 9個すべて操作可能。
     */
    public static final AbilityCategory VECMANIP = new AbilityCategory(
            ResourceLocation.fromNamespaceAndPath("academy", "vecmanip"),
            AcademyContent.FACTOR_VECMANIP, List.of(2, 2, 2, 2, 1));
    public static final List<AbilityCategory> REGISTERED = List.of(ELECTROMASTER, MELTDOWNER, TELEPORTER, VECMANIP);

    public static AbilityCategory find(ResourceLocation id) {
        return REGISTERED.stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    /** 原作の取得因子が与えるカテゴリ。スタックが因子でなければnull。 */
    public static AbilityCategory byFactor(ItemStack stack) {
        return stack.isEmpty() ? null
                : REGISTERED.stream().filter(category -> stack.is(category.factor().get())).findFirst().orElse(null);
    }
}
