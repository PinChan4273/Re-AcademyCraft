package io.github.pinchan4273.reacademycraft.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * 1回の作成でスタックできないアイテムを複数渡す、形のあるレシピ。原作の結晶からのエネルギーユニット（2個・4個）に使う。
 * Forge 1.20.1は出力のcountにそのまま2・4を書け、手に「2個重なったエネルギーユニット」が渡った。1.21はレシピの出力が
 * 最大スタック数を超えると読み込みを拒み、重なったスタックは保存の検証でも落ちる。そこで出力は1個にし、残りの
 * yield-1個を材料の欄へ「残り物」として返す（vanillaの作業台がそのまま欄・インベントリ・足元へ渡す）。個数は原作と同じ。
 * JSONは"type": "academy:crafting_shaped_yield"で、形のあるレシピと同じ鍵に"yield"（1回で渡す個数）を足す。
 * 残りは材料の入った欄にだけ置けるので、yield-1が材料の入った欄の数を超えるレシピは読み込みで拒む（置けない分が消えるのを防ぐ）。
 */
public final class YieldShapedRecipe extends ShapedRecipe {
    private final int yield;

    public YieldShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, int yield) {
        super(group, category, pattern, result, showNotification);
        this.yield = yield;
    }

    /** 1回の作成で渡す個数（出力の1個と残り物の合計）。 */
    public int yield() { return yield; }

    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.YIELD_SHAPED_SERIALIZER.get(); }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = super.getRemainingItems(input);
        int extra = yield - 1;
        // 材料が置かれていて、その材料自身の残り物が無い欄へ1個ずつ置く（作成で材料は1個ずつ減る）。
        for (int i = 0; i < remaining.size() && extra > 0; i++) {
            if (input.getItem(i).isEmpty() || !remaining.get(i).isEmpty()) continue;
            remaining.set(i, getResultItem(null).copyWithCount(1));
            extra--;
        }
        return remaining;
    }

    /** 返す残り（yield-1個）を置ける、材料の入った欄があるか。 */
    private static com.mojang.serialization.DataResult<YieldShapedRecipe> fits(YieldShapedRecipe recipe) {
        long filled = recipe.pattern.ingredients().stream().filter(ingredient -> !ingredient.isEmpty()).count();
        return recipe.yield - 1 <= filled ? com.mojang.serialization.DataResult.success(recipe)
                : com.mojang.serialization.DataResult.error(() -> "yield " + recipe.yield + " needs " + (recipe.yield - 1) + " filled slots for its remainders, the pattern has " + filled);
    }

    public static final class Serializer implements RecipeSerializer<YieldShapedRecipe> {
        private static final MapCodec<YieldShapedRecipe> FIELDS = RecordCodecBuilder.mapCodec(recipe -> recipe.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.getResultItem(null)),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification),
                Codec.intRange(1, 9).fieldOf("yield").forGetter(YieldShapedRecipe::yield)
        ).apply(recipe, YieldShapedRecipe::new));
        private static final MapCodec<YieldShapedRecipe> CODEC = FIELDS.validate(YieldShapedRecipe::fits);
        private static final StreamCodec<RegistryFriendlyByteBuf, YieldShapedRecipe> STREAM_CODEC = StreamCodec.of(
                (buffer, recipe) -> {
                    buffer.writeUtf(recipe.getGroup());
                    buffer.writeEnum(recipe.category());
                    ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.getResultItem(null));
                    buffer.writeBoolean(recipe.showNotification());
                    ByteBufCodecs.VAR_INT.encode(buffer, recipe.yield);
                },
                buffer -> new YieldShapedRecipe(buffer.readUtf(), buffer.readEnum(CraftingBookCategory.class), ShapedRecipePattern.STREAM_CODEC.decode(buffer),
                        ItemStack.STREAM_CODEC.decode(buffer), buffer.readBoolean(), ByteBufCodecs.VAR_INT.decode(buffer)));

        @Override public MapCodec<YieldShapedRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, YieldShapedRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
