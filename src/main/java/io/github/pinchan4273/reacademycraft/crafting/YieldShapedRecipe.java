package io.github.pinchan4273.reacademycraft.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 1回の作成でスタックできないアイテムを複数渡す、形のあるレシピ。原作の結晶からのエネルギーユニット（2個・4個）に使う。
 * Forge 1.20.1は出力のcountにそのまま2・4を書け、手に「2個重なったエネルギーユニット」が渡った。1.21はレシピの出力が
 * 最大スタック数を超えると読み込みを拒み、重なったスタックは保存の検証でも落ちる。そこで出力は1個にし、残りの
 * yield-1個を材料の欄へ「残り物」として返す（vanillaの作業台がそのまま欄・インベントリ・足元へ渡す）。個数は原作と同じ。
 * JSONは"type": "academy:crafting_shaped_yield"で、形のあるレシピと同じ鍵に"yield"（1回で渡す個数）を足す。
 * 残りは材料の入った欄にだけ置けるので、yield-1が材料の入った欄の数を超えるレシピは読み込みで拒む（置けない分が消えるのを防ぐ）。
 * 26.2: ShapedRecipe の getSerializer の戻り型が固定になり継承で替えられないので、NormalCraftingRecipe から同じ形で作る。
 */
public final class YieldShapedRecipe extends NormalCraftingRecipe {
    private final ShapedRecipePattern pattern;
    private final ItemStackTemplate result;
    private final int yield;

    public YieldShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ShapedRecipePattern pattern, ItemStackTemplate result, int yield) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
        this.yield = yield;
    }

    /** 1回の作成で渡す個数（出力の1個と残り物の合計）。 */
    public int yield() { return yield; }

    /** 出力の1個（試験と表示から読む）。 */
    public ItemStack resultStack() { return result.create(); }

    @Override public RecipeSerializer<YieldShapedRecipe> getSerializer() { return MachineRecipes.YIELD_SHAPED_SERIALIZER.get(); }
    @Override protected PlacementInfo createPlacementInfo() { return PlacementInfo.createFromOptionals(pattern.ingredients()); }
    @Override public boolean matches(CraftingInput input, Level level) { return pattern.matches(input); }
    @Override public ItemStack assemble(CraftingInput input) { return result.create(); }
    @Override public java.util.List<RecipeDisplay> display() {
        return java.util.List.of(new ShapedCraftingRecipeDisplay(pattern.width(), pattern.height(),
                pattern.ingredients().stream().map(e -> e.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE)).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = CraftingRecipe.defaultCraftingReminder(input);
        int extra = yield - 1;
        // 材料が置かれていて、その材料自身の残り物が無い欄へ1個ずつ置く（作成で材料は1個ずつ減る）。
        for (int i = 0; i < remaining.size() && extra > 0; i++) {
            if (input.getItem(i).isEmpty() || !remaining.get(i).isEmpty()) continue;
            remaining.set(i, result.create().copyWithCount(1));
            extra--;
        }
        return remaining;
    }

    /** 返す残り（yield-1個）を置ける、材料の入った欄があるか。 */
    private static com.mojang.serialization.DataResult<YieldShapedRecipe> fits(YieldShapedRecipe recipe) {
        long filled = recipe.pattern.ingredients().stream().filter(java.util.Optional::isPresent).count();
        return recipe.yield - 1 <= filled ? com.mojang.serialization.DataResult.success(recipe)
                : com.mojang.serialization.DataResult.error(() -> "yield " + recipe.yield + " needs " + (recipe.yield - 1) + " filled slots for its remainders, the pattern has " + filled);
    }

    public static final class Serializer {
        private static final MapCodec<YieldShapedRecipe> FIELDS = RecordCodecBuilder.mapCodec(recipe -> recipe.group(
                Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
                CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
                ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result),
                Codec.intRange(1, 9).fieldOf("yield").forGetter(YieldShapedRecipe::yield)
        ).apply(recipe, YieldShapedRecipe::new));
        private static final MapCodec<YieldShapedRecipe> CODEC = FIELDS.validate(YieldShapedRecipe::fits);
        private static final StreamCodec<RegistryFriendlyByteBuf, YieldShapedRecipe> STREAM_CODEC = StreamCodec.composite(
                Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
                CraftingRecipe.CraftingBookInfo.STREAM_CODEC, r -> r.bookInfo,
                ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
                ItemStackTemplate.STREAM_CODEC, r -> r.result,
                ByteBufCodecs.VAR_INT, YieldShapedRecipe::yield,
                YieldShapedRecipe::new);

        static RecipeSerializer<YieldShapedRecipe> create() { return new RecipeSerializer<>(CODEC, STREAM_CODEC); }
    }
}
