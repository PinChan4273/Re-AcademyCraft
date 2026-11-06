package io.github.pinchan4273.reacademycraft.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.pinchan4273.reacademycraft.AcademyCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
            SERIALIZERS.register("metal_forming", MetalFormerRecipe.Serializer::create);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ImagFusionRecipe>> FUSION_TYPE = TYPES.register("imag_fusion", () -> type("imag_fusion"));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ImagFusionRecipe>> FUSION_SERIALIZER =
            SERIALIZERS.register("imag_fusion", ImagFusionRecipe.Serializer::create);
    /** 結晶からのエネルギーユニット（スタックできないものを1回で複数）。YieldShapedRecipeを参照。NeoForge版で足した。 */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<YieldShapedRecipe>> YIELD_SHAPED_SERIALIZER =
            SERIALIZERS.register("crafting_shaped_yield", YieldShapedRecipe.Serializer::create);

    /**
     * Forge 1.20.1のCraftingHelper.getItemStackが読んだ出力の形: {"item": アイテム, "count": 個数（省略時1）}。
     * 1.21のItemStack.CODECは"id"の鍵を使うので、このmodのレシピJSONの形を保つためにここで定める。
     */
    static final Codec<net.minecraft.world.item.ItemStackTemplate> ITEM_RESULT = RecordCodecBuilder.create(result -> result.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(net.minecraft.world.item.ItemStackTemplate::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(net.minecraft.world.item.ItemStackTemplate::count)
    ).apply(result, net.minecraft.world.item.ItemStackTemplate::new));

    private MachineRecipes() { }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> RecipeType<T> type(String path) {
        String name = AcademyCraft.MODID + ":" + path;
        return new RecipeType<>() {
            @Override public String toString() { return name; }
        };
    }

    /** クライアントが受け取った機械のレシピ（26.2 のレシピは既定で同期されないので、下の sendRecipes で頼んで受け取る）。 */
    private static volatile net.minecraft.world.item.crafting.RecipeMap clientRecipes = net.minecraft.world.item.crafting.RecipeMap.EMPTY;

    /** 1.21.1 の level.getRecipeManager() の、このmodの機械のレシピの読み取りに当たる。サーバーはそのまま、クライアントは同期された分。 */
    public static net.minecraft.world.item.crafting.RecipeMap recipes(net.minecraft.world.level.Level level) {
        return level instanceof net.minecraft.server.level.ServerLevel server ? server.recipeAccess().recipeMap() : clientRecipes;
    }

    /** クライアントの受信の処理（client/ClientMachineRecipes）から呼ぶ。 */
    public static void setClientRecipes(net.minecraft.world.item.crafting.RecipeMap recipes) { clientRecipes = recipes; }

    /** 機械の画面の材料の判定とチュートリアルの表示が、クライアントでレシピを読むので送る。 */
    private static void sendToClients(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        event.sendRecipes(FORMING_TYPE.get(), FUSION_TYPE.get());
        event.sendRecipes(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.RecipeType.SMELTING);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MachineRecipes::sendToClients);
    }
}
