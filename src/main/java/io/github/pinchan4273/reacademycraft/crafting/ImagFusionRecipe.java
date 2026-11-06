package io.github.pinchan4273.reacademycraft.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * 虚像融合機のレシピ。原作ImagFusorRecipes.IFRecipe（WeAthFolD）は、消費するアイテム（consumeType）・虚像投影液の量
 * （consumeLiquid）・出力の組で、アイテムが同じかで照合し、getRecipeは最初に合うものを返す。1回の加工で入力を1つ使う。
 * 原作はMFIFRecipesで2つ登録する（低純度の結晶→中純度に3000、中純度→高純度に8000）。ここではデータパックのレシピとして読み、
 * JSONは金属成形機のレシピと同じ考え方のこのmod独自の形にする:
 * <pre>
 * {"type": "academy:imag_fusion", "ingredient": 材料（Ingredient）, "phase_liquid": 液量（mB）,
 *  "result": {"item": アイテム, "count": 個数}}
 * </pre>
 *
 * <p>NeoForge 1.21.1: レシピのIDはレシピ自身でなくRecipeHolderが持つ（読み込みの誤りのログにはRecipeManagerがIDを添える）。
 * JSONの形はForge 1.20.1版と同じ。
 */
public final class ImagFusionRecipe implements Recipe<SingleRecipeInput> {
    /** 原作の融合機のタンクは8000なので、タンク1杯を超えて要求するレシピは認めない。 */
    public static final int MAX_LIQUID = 8000;

    private final Ingredient ingredient;
    private final int liquid;
    private final net.minecraft.world.item.ItemStackTemplate result;

    public ImagFusionRecipe(Ingredient ingredient, int liquid, ItemStack result) {
        this(ingredient, liquid, result.isEmpty() ? null : net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(result));
    }

    /** 26.2 はレシピの読み込みの時に ItemStack を作れないので、出力は template で持つ。スタックの上限は {@link #result()} の時に確かめる。 */
    public ImagFusionRecipe(Ingredient ingredient, int liquid, net.minecraft.world.item.ItemStackTemplate result) {
        if (liquid < 1 || liquid > MAX_LIQUID) throw new IllegalArgumentException("Imag fusion recipe needs 1 to " + MAX_LIQUID + " mB");
        if (result == null || result.count() < 1) throw new IllegalArgumentException("Imag fusion recipe has no result");
        // データパックの読み込みの時はアイテムの部品がまだ結ばれていないので確かめられない（その時は result() の時に分かる）。
        if (result.item().areComponentsBound() && result.count() > result.item().value().getDefaultMaxStackSize())
            throw new IllegalArgumentException("Imag fusion recipe has more than a stack");
        this.ingredient = ingredient;
        this.liquid = liquid;
        this.result = result;
    }

    /** 原作IFRecipe.matches: 入力が材料に当たるか。 */
    public boolean accepts(ItemStack stack) { return !stack.isEmpty() && ingredient.test(stack) && !result().isEmpty(); }
    /** 1回の加工で使う虚像投影液（mB）。 */
    public int liquid() { return liquid; }
    public Ingredient ingredient() { return ingredient; }
    /** 出力の写し。 */
    public ItemStack result() { return result.create(); }

    public static List<ImagFusionRecipe> all(Level level) {
        return MachineRecipes.recipes(level).byType(MachineRecipes.FUSION_TYPE.get()).stream().map(RecipeHolder::value).toList();
    }

    /** 原作ImagFusorRecipes.getRecipe: 入力に合うレシピのうち、最初のもの（IDの順）。 */
    public static Optional<ImagFusionRecipe> find(Level level, ItemStack input) {
        return findHolder(level, input).map(RecipeHolder::value);
    }
    /** {@link #find}と同じものを、IDを持つRecipeHolderで返す（機械が加工中のレシピをIDで保存・照合する）。 */
    public static Optional<RecipeHolder<ImagFusionRecipe>> findHolder(Level level, ItemStack input) {
        return MachineRecipes.recipes(level).byType(MachineRecipes.FUSION_TYPE.get()).stream()
                .filter(holder -> holder.value().accepts(input))
                .min(Comparator.comparing(holder -> holder.id().identifier().toString()));
    }

    /** 照合はaccepts、検索はfindで行う。容器だけでは一致としない。 */
    @Override public boolean matches(SingleRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(SingleRecipeInput input) { return result(); }
    @Override public RecipeSerializer<ImagFusionRecipe> getSerializer() { return MachineRecipes.FUSION_SERIALIZER.get(); }
    @Override public RecipeType<ImagFusionRecipe> getType() { return MachineRecipes.FUSION_TYPE.get(); }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return ""; }
    @Override public net.minecraft.world.item.crafting.PlacementInfo placementInfo() { return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE; }
    @Override public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() { return net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC; }
    /** 機械専用のレシピで、作業台のレシピ本には出さない。 */
    @Override public boolean isSpecial() { return true; }

    /** コンストラクタの検査を、例外でなく読み込みの誤りとして返す。 */
    private static DataResult<ImagFusionRecipe> checked(Ingredient ingredient, int liquid, net.minecraft.world.item.ItemStackTemplate result) {
        try {
            return DataResult.success(new ImagFusionRecipe(ingredient, liquid, result));
        } catch (IllegalArgumentException invalid) {
            return DataResult.error(() -> "Invalid imag fusion recipe: " + invalid.getMessage());
        }
    }

    public static final class Serializer {
        private record Fields(Ingredient ingredient, int liquid, net.minecraft.world.item.ItemStackTemplate result) { }
        private static final MapCodec<ImagFusionRecipe> CODEC = RecordCodecBuilder.<Fields>mapCodec(fields -> fields.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Fields::ingredient),
                Codec.INT.fieldOf("phase_liquid").forGetter(Fields::liquid),
                MachineRecipes.ITEM_RESULT.fieldOf("result").forGetter(Fields::result)
        ).apply(fields, Fields::new)).flatXmap(f -> checked(f.ingredient(), f.liquid(), f.result()),
                recipe -> DataResult.success(new Fields(recipe.ingredient, recipe.liquid, recipe.result)));

        /** 通信の並び: 材料、液量、出力（Forge版と同じ順）。 */
        private static final StreamCodec<RegistryFriendlyByteBuf, ImagFusionRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient);
                    buf.writeVarInt(recipe.liquid);
                    net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result);
                },
                buf -> {
                    var ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    int liquid = buf.readVarInt();
                    var result = net.minecraft.world.item.ItemStackTemplate.STREAM_CODEC.decode(buf);
                    try {
                        return new ImagFusionRecipe(ingredient, liquid, result);
                    } catch (IllegalArgumentException invalid) {
                        throw new io.netty.handler.codec.DecoderException("Invalid imag fusion recipe", invalid);
                    }
                });

        static RecipeSerializer<ImagFusionRecipe> create() { return new RecipeSerializer<>(CODEC, STREAM_CODEC); }
    }
}
