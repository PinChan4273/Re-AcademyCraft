package io.github.pinchan4273.reacademycraft.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity.Mode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;

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
 * そのレシピはforge:tag_emptyの条件を持つので、空のタグを読むことはない。
 */
public final class MetalFormerRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Mode operation;
    private final Ingredient ingredient;
    private final int amount;
    private final ItemStack result;

    public MetalFormerRecipe(ResourceLocation id, Mode operation, Ingredient ingredient, int amount, ItemStack result) {
        this.id = Objects.requireNonNull(id);
        this.operation = Objects.requireNonNull(operation);
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Metal former recipe " + id + " has no ingredient");
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("Metal former recipe " + id + " needs 1 to 64 of its ingredient");
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize())
            throw new IllegalArgumentException("Metal former recipe " + id + " has no result, or more than a stack");
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
        return level.getRecipeManager().getAllRecipesFor(MachineRecipes.FORMING_TYPE.get());
    }

    /** 原作MetalFormerRecipes.getRecipe: 受け付けるレシピのうち、最初のもの（IDの順）。 */
    public static Optional<MetalFormerRecipe> find(Level level, ItemStack input, Mode mode) {
        return all(level).stream().filter(recipe -> recipe.accepts(input, mode))
                .min(Comparator.comparing(recipe -> recipe.id.toString()));
    }

    /** 照合はモードが要るのでaccepts(stack, mode)で行い、検索はfindを使う。容器だけでは一致としない。 */
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return result(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return result(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.FORMING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return MachineRecipes.FORMING_TYPE.get(); }
    /** 機械専用のレシピで、作業台のレシピ本には出さない。 */
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }

    public static final class Serializer implements RecipeSerializer<MetalFormerRecipe> {
        @Override
        public MetalFormerRecipe fromJson(ResourceLocation id, JsonObject json) {
            return fromJson(id, json, ICondition.IContext.EMPTY);
        }

        @Override
        public MetalFormerRecipe fromJson(ResourceLocation id, JsonObject json, ICondition.IContext context) {
            try {
                return new MetalFormerRecipe(id, operation(GsonHelper.getAsString(json, "operation")),
                        Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient")), GsonHelper.getAsInt(json, "amount", 1),
                        result(GsonHelper.getAsJsonObject(json, "result"), context));
            } catch (IllegalArgumentException invalid) {
                throw new JsonSyntaxException("Invalid metal former recipe " + id + ": " + invalid.getMessage(), invalid);
            }
        }

        private static Mode operation(String name) {
            for (Mode mode : Mode.values()) if (mode.name().toLowerCase(Locale.ROOT).equals(name)) return mode;
            throw new IllegalArgumentException("unknown operation \"" + name + "\"");
        }

        private static ItemStack result(JsonObject json, ICondition.IContext context) {
            if (!json.has("tag")) return CraftingHelper.getItemStack(json, true);
            var tag = ItemTags.create(new ResourceLocation(GsonHelper.getAsString(json, "tag")));
            var first = context.getTag(tag).stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("result tag " + tag.location() + " is empty"));
            return new ItemStack(first.value(), GsonHelper.getAsInt(json, "count", 1));
        }

        /** 通信の並び: 材料、個数、モード、出力。 */
        @Override
        public MetalFormerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var ingredient = Ingredient.fromNetwork(buf);
            int amount = buf.readVarInt();
            int operation = buf.readUnsignedByte();
            if (operation >= Mode.values().length) throw new io.netty.handler.codec.DecoderException("Unknown metal former operation " + operation);
            var result = buf.readItem();
            try {
                return new MetalFormerRecipe(id, Mode.values()[operation], ingredient, amount, result);
            } catch (IllegalArgumentException invalid) {
                throw new io.netty.handler.codec.DecoderException("Invalid metal former recipe " + id, invalid);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, MetalFormerRecipe recipe) {
            recipe.ingredient.toNetwork(buf);
            buf.writeVarInt(recipe.amount);
            buf.writeByte(recipe.operation.ordinal());
            buf.writeItem(recipe.result);
        }
    }
}
