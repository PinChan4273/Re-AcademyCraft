package io.github.pinchan4273.reacademycraft.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity.Mode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * 金属成形機のレシピ。原作MetalFormerRecipes.RecipeObject（WeAthFolD）は、入力（個数を含む）・出力・モードの組で、
 * accepts(stack, mode)で照合し、MetalFormerRecipes.getRecipe(input, mode)が最初に受け付けるものを選ぶ。
 * 原作はレシピをコードで登録していた（MFIFRecipes、VanillaCategories）。ここではデータパックのレシピとして読み、
 * JSONは次の形にする（このmod独自の形式）:
 * <pre>
 * {"type": "academy:metal_forming", "operation": "plate" | "incise" | "etch" | "refine",
 *  "ingredient": 材料（Ingredient）, "amount": 必要な個数（省略時1）,
 *  "result": {"item": アイテム, "count": 個数} または {"tag": タグ, "count": 個数}}
 * </pre>
 * resultのtagは原作MFIFRecipes.addOreDictRefineRecipe(ore, "ingotX")に当たる: 他modが中身を入れるタグの最初のアイテム。
 * そのレシピはタグが空でないことの条件（Forge版はforge:tag_empty、NeoForgeではneoforge:tag_empty）を持つので、空のタグを読むことはない。
 *
 * <p>NeoForge 1.21.1: レシピのIDはRecipeHolderが持つ。タグは、Forge版と同じくレシピを読むときに条件の文脈
 * （{@link ConditionalOps#retrieveContext()}）から引く。JSONの形はForge 1.20.1版と同じ（タグ名と条件の名前空間は資源の移植で変わる）。
 */
public final class MetalFormerRecipe implements Recipe<SingleRecipeInput> {
    private final Mode operation;
    private final Ingredient ingredient;
    private final int amount;
    private final ItemStack result;

    public MetalFormerRecipe(Mode operation, Ingredient ingredient, int amount, ItemStack result) {
        this.operation = Objects.requireNonNull(operation);
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Metal former recipe has no ingredient");
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("Metal former recipe needs 1 to 64 of its ingredient");
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize())
            throw new IllegalArgumentException("Metal former recipe has no result, or more than a stack");
        this.ingredient = ingredient;
        this.amount = amount;
        this.result = result.copy();
    }

    public Mode operation() { return operation; }
    public Ingredient ingredient() { return ingredient; }
    public int amount() { return amount; }
    /** 出力の写し。呼び出し元が変えてもレシピは変わらない。 */
    public ItemStack result() { return result.copy(); }

    /** 原作RecipeObject.accepts: 同じモードで、材料に当たり、個数が足りること。 */
    public boolean accepts(ItemStack stack, Mode mode) {
        return mode == operation && !stack.isEmpty() && stack.getCount() >= amount && ingredient.test(stack);
    }

    /** モードと個数を問わず、材料に当たるか。入力スロットへ入れてよいかの判定に使う。 */
    public boolean usesAsIngredient(ItemStack stack) { return !stack.isEmpty() && ingredient.test(stack); }

    public static List<MetalFormerRecipe> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(MachineRecipes.FORMING_TYPE.get()).stream().map(RecipeHolder::value).toList();
    }

    /** 原作MetalFormerRecipes.getRecipe: 受け付けるレシピのうち、最初のもの（IDの順）。 */
    public static Optional<MetalFormerRecipe> find(Level level, ItemStack input, Mode mode) {
        return findHolder(level, input, mode).map(RecipeHolder::value);
    }
    /** {@link #find}と同じものを、IDを持つRecipeHolderで返す（機械が加工中のレシピをIDで保存・照合する）。 */
    public static Optional<RecipeHolder<MetalFormerRecipe>> findHolder(Level level, ItemStack input, Mode mode) {
        return level.getRecipeManager().getAllRecipesFor(MachineRecipes.FORMING_TYPE.get()).stream()
                .filter(holder -> holder.value().accepts(input, mode))
                .min(Comparator.comparing(holder -> holder.id().toString()));
    }

    /** 照合はモードが要るのでaccepts(stack, mode)で行い、検索はfindを使う。容器だけでは一致としない。 */
    @Override public boolean matches(SingleRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) { return result(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result(); }
    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.FORMING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return MachineRecipes.FORMING_TYPE.get(); }
    /** 機械専用のレシピで、作業台のレシピ本には出さない。 */
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }

    /** コンストラクタの検査を、例外でなく読み込みの誤りとして返す。 */
    private static DataResult<MetalFormerRecipe> create(Mode operation, Ingredient ingredient, int amount, ItemStack result) {
        try {
            return DataResult.success(new MetalFormerRecipe(operation, ingredient, amount, result));
        } catch (IllegalArgumentException invalid) {
            return DataResult.error(() -> "Invalid metal former recipe: " + invalid.getMessage());
        }
    }

    public static final class Serializer implements RecipeSerializer<MetalFormerRecipe> {
        private static final Codec<Mode> OPERATION = Codec.STRING.comapFlatMap(Serializer::operation, mode -> mode.name().toLowerCase(Locale.ROOT));

        private static DataResult<Mode> operation(String name) {
            for (Mode mode : Mode.values()) if (mode.name().toLowerCase(Locale.ROOT).equals(name)) return DataResult.success(mode);
            return DataResult.error(() -> "unknown operation \"" + name + "\"");
        }

        /**
         * resultのJSON。Forge版と同じく、tagがあればitemより先に見る（tagのcountは省略時1）。書き出すときはitemの形にする。
         * contextはJSONに無く、読むときの条件の文脈。
         */
        private record ResultJson(Optional<Item> item, Optional<TagKey<Item>> tag, int count, ICondition.IContext context) {
            DataResult<ItemStack> resolve() {
                if (tag.isEmpty()) return item.map(i -> DataResult.success(new ItemStack(i, count)))
                        .orElseGet(() -> DataResult.error(() -> "result needs an item or a tag"));
                var first = context.getTag(tag.get()).stream().findFirst();
                return first.map(holder -> DataResult.success(new ItemStack(holder.value(), count)))
                        .orElseGet(() -> DataResult.error(() -> "result tag " + tag.get().location() + " is empty"));
            }
            static ResultJson of(ItemStack stack) {
                return new ResultJson(Optional.of(stack.getItem()), Optional.empty(), stack.getCount(), ICondition.IContext.EMPTY);
            }
        }
        private static final Codec<ItemStack> RESULT = RecordCodecBuilder.<ResultJson>mapCodec(json -> json.group(
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(ResultJson::item),
                TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(ResultJson::tag),
                Codec.INT.optionalFieldOf("count", 1).forGetter(ResultJson::count),
                ConditionalOps.retrieveContext().forGetter(ResultJson::context)
        ).apply(json, ResultJson::new)).codec().flatXmap(ResultJson::resolve, stack -> DataResult.success(ResultJson.of(stack)));

        private record Fields(Mode operation, Ingredient ingredient, int amount, ItemStack result) { }
        private static final MapCodec<MetalFormerRecipe> CODEC = RecordCodecBuilder.<Fields>mapCodec(fields -> fields.group(
                OPERATION.fieldOf("operation").forGetter(Fields::operation),
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Fields::ingredient),
                Codec.INT.optionalFieldOf("amount", 1).forGetter(Fields::amount),
                RESULT.fieldOf("result").forGetter(Fields::result)
        ).apply(fields, Fields::new)).flatXmap(f -> create(f.operation(), f.ingredient(), f.amount(), f.result()),
                recipe -> DataResult.success(new Fields(recipe.operation, recipe.ingredient, recipe.amount, recipe.result)));

        /** 通信の並び: 材料、個数、モード、出力（Forge版と同じ順）。 */
        private static final StreamCodec<RegistryFriendlyByteBuf, MetalFormerRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient);
                    buf.writeVarInt(recipe.amount);
                    buf.writeByte(recipe.operation.ordinal());
                    ItemStack.STREAM_CODEC.encode(buf, recipe.result);
                },
                buf -> {
                    var ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
                    int amount = buf.readVarInt();
                    int operation = buf.readUnsignedByte();
                    if (operation >= Mode.values().length) throw new io.netty.handler.codec.DecoderException("Unknown metal former operation " + operation);
                    var result = ItemStack.STREAM_CODEC.decode(buf);
                    try {
                        return new MetalFormerRecipe(Mode.values()[operation], ingredient, amount, result);
                    } catch (IllegalArgumentException invalid) {
                        throw new io.netty.handler.codec.DecoderException("Invalid metal former recipe", invalid);
                    }
                });

        @Override public MapCodec<MetalFormerRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MetalFormerRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
