package io.github.pinchan4273.reacademycraft.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.pinchan4273.reacademycraft.AcademyCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 機械のレシピの種類。原作は金属成形機（MetalFormerRecipes）と虚像融合機（ImagFusorRecipes）の2つの登録簿を
 * コードで持っていた。ここではそれぞれをデータパックのレシピ型として登録する。
 * 型のID（academy:metal_forming、academy:imag_fusion）はレシピJSONの"type"に書かれるので変えない。
 */
public final class MachineRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, AcademyCraft.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, AcademyCraft.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MetalFormerRecipe>> FORMING_TYPE = TYPES.register("metal_forming", () -> type("metal_forming"));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MetalFormerRecipe>> FORMING_SERIALIZER =
            SERIALIZERS.register("metal_forming", MetalFormerRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ImagFusionRecipe>> FUSION_TYPE = TYPES.register("imag_fusion", () -> type("imag_fusion"));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ImagFusionRecipe>> FUSION_SERIALIZER =
            SERIALIZERS.register("imag_fusion", ImagFusionRecipe.Serializer::new);
    /** 結晶からのエネルギーユニット（スタックできないものを1回で複数）。YieldShapedRecipeを参照。NeoForge版で足した。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<YieldShapedRecipe>> YIELD_SHAPED_SERIALIZER =
            SERIALIZERS.register("crafting_shaped_yield", YieldShapedRecipe.Serializer::new);

    /**
     * Forge 1.20.1のCraftingHelper.getItemStackが読んだ出力の形: {"item": アイテム, "count": 個数（省略時1）}。
     * 1.21のItemStack.CODECは"id"の鍵を使うので、このmodのレシピJSONの形を保つためにここで定める。
     */
    static final Codec<ItemStack> ITEM_RESULT = RecordCodecBuilder.create(result -> result.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemStack::getItem),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemStack::getCount)
    ).apply(result, ItemStack::new));

    private MachineRecipes() { }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeType<T> type(String path) {
        String name = AcademyCraft.MODID + ":" + path;
        return new RecipeType<>() {
            @Override public String toString() { return name; }
        };
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
