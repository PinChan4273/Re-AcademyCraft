package io.github.pinchan4273.reacademycraft.crafting;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * 虚像融合機のレシピ。原作ImagFusorRecipes.IFRecipe（WeAthFolD）は、消費するアイテム（consumeType）・虚像投影液の量
 * （consumeLiquid）・出力の組で、アイテムが同じかで照合し、getRecipeは最初に合うものを返す。1回の加工で入力を1つ使う。
 * 原作はMFIFRecipesで2つ登録する（低純度の結晶→中純度に3000、中純度→高純度に8000）。ここではデータパックのレシピとして読み、
 * JSONは金属成形機のレシピと同じ考え方のこのmod独自の形にする:
 * <pre>
 * {"type": "academy:imag_fusion", "ingredient": 材料（Ingredient）, "phase_liquid": 液量（mB）,
 *  "result": {"item": アイテム, "count": 個数}}
 * </pre>
 */
public final class ImagFusionRecipe implements Recipe<Container> {
    /** 原作の融合機のタンクは8000なので、タンク1杯を超えて要求するレシピは認めない。 */
    public static final int MAX_LIQUID = 8000;

    private final ResourceLocation id;
    private final Ingredient ingredient;
    private final int liquid;
    private final ItemStack result;

    public ImagFusionRecipe(ResourceLocation id, Ingredient ingredient, int liquid, ItemStack result) {
        this.id = Objects.requireNonNull(id);
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Imag fusion recipe " + id + " has no ingredient");
        if (liquid < 1 || liquid > MAX_LIQUID) throw new IllegalArgumentException("Imag fusion recipe " + id + " needs 1 to " + MAX_LIQUID + " mB");
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize())
            throw new IllegalArgumentException("Imag fusion recipe " + id + " has no result, or more than a stack");
        this.ingredient = ingredient;
        this.liquid = liquid;
        this.result = result.copy();
    }

    /** 原作IFRecipe.matches: 入力が材料に当たるか。 */
    public boolean accepts(ItemStack stack) { return !stack.isEmpty() && ingredient.test(stack); }
    /** 1回の加工で使う虚像投影液（mB）。 */
    public int liquid() { return liquid; }
    /** 出力の写し。 */
    public ItemStack result() { return result.copy(); }

    public static List<ImagFusionRecipe> all(Level level) {
        return level.getRecipeManager().getAllRecipesFor(MachineRecipes.FUSION_TYPE.get());
    }

    /** 原作ImagFusorRecipes.getRecipe: 入力に合うレシピのうち、最初のもの（IDの順）。 */
    public static Optional<ImagFusionRecipe> find(Level level, ItemStack input) {
        return all(level).stream().filter(recipe -> recipe.accepts(input)).min(Comparator.comparing(recipe -> recipe.id.toString()));
    }

    /** 照合はaccepts、検索はfindで行う。容器だけでは一致としない。 */
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return result(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(RegistryAccess access) { return result(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return MachineRecipes.FUSION_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return MachineRecipes.FUSION_TYPE.get(); }
    /** 機械専用のレシピで、作業台のレシピ本には出さない。 */
    @Override public boolean isSpecial() { return true; }
    @Override public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY, ingredient); }

    public static final class Serializer implements RecipeSerializer<ImagFusionRecipe> {
        @Override
        public ImagFusionRecipe fromJson(ResourceLocation id, JsonObject json) {
            try {
                return new ImagFusionRecipe(id, Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient")),
                        GsonHelper.getAsInt(json, "phase_liquid"), CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true));
            } catch (IllegalArgumentException invalid) {
                throw new JsonSyntaxException("Invalid imag fusion recipe " + id + ": " + invalid.getMessage(), invalid);
            }
        }

        /** 通信の並び: 材料、液量、出力。 */
        @Override
        public ImagFusionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            var ingredient = Ingredient.fromNetwork(buf);
            int liquid = buf.readVarInt();
            var result = buf.readItem();
            try {
                return new ImagFusionRecipe(id, ingredient, liquid, result);
            } catch (IllegalArgumentException invalid) {
                throw new io.netty.handler.codec.DecoderException("Invalid imag fusion recipe " + id, invalid);
            }
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ImagFusionRecipe recipe) {
            recipe.ingredient.toNetwork(buf);
            buf.writeVarInt(recipe.liquid);
            buf.writeItem(recipe.result);
        }
    }
}
