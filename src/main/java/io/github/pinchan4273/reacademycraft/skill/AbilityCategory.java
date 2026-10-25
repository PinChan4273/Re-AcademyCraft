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

    /** 因子のアイテムがあるか（1.4.0のS4。外部のカテゴリは因子を持たないことがある）。 */
    public boolean hasFactor() { var item = factor.get(); return item != null && item != net.minecraft.world.item.Items.AIR; }

    /** 名前の翻訳キー（1.4.0のS2から登録簿の表示の情報。本体のカテゴリは1.3.0と同じ{@code academy.ability.<path>}）。 */
    public String translation() { return io.github.pinchan4273.reacademycraft.ability.RegistryViews.categoryNameKey(id); }

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
    private static final List<AbilityCategory> BUILT_IN = List.of(ELECTROMASTER, MELTDOWNER, TELEPORTER, VECMANIP);
    /**
     * 登録されたカテゴリ（1.4.0のS2から、登録簿の読み取りの窓）。本体の4つは上の定数そのもの（ほかのコードが定数と比べる）で、並びは1.3.0のまま。
     * 外部のアドオンのカテゴリは、その定義から作った物が、登録の順に本体の後ろに並ぶ（因子のアイテムが無い・見つからなければ空気）。
     */
    public static final List<AbilityCategory> REGISTERED = io.github.pinchan4273.reacademycraft.ability.RegistryViews.list(
            io.github.pinchan4273.reacademycraft.ability.AbilityRegistry.INSTANCE::categories, AbilityCategory::adapt);
    private static AbilityCategory adapt(io.github.pinchan4273.reacademycraft.api.CategoryDefinition definition) {
        var id = io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(definition.id());
        for (var builtIn : BUILT_IN) if (builtIn.id().equals(id)) return builtIn;
        var factor = definition.factorItem() == null ? null : io.github.pinchan4273.reacademycraft.ability.RegistryViews.location(definition.factorItem());
        return new AbilityCategory(id, () -> factor == null ? net.minecraft.world.item.Items.AIR
                : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(factor), definition.controllablePerLevel());
    }

    public static AbilityCategory find(ResourceLocation id) {
        return REGISTERED.stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }

    /** 原作の取得因子が与えるカテゴリ。スタックが因子でなければnull。 */
    public static AbilityCategory byFactor(ItemStack stack) {
        return stack.isEmpty() ? null
                : REGISTERED.stream().filter(category -> stack.is(category.factor().get())).findFirst().orElse(null);
    }
}
