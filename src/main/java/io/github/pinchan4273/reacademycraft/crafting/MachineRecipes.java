package io.github.pinchan4273.reacademycraft.crafting;

import io.github.pinchan4273.reacademycraft.AcademyCraft;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 機械のレシピの種類。原作は金属成形機（MetalFormerRecipes）と虚像融合機（ImagFusorRecipes）の2つの登録簿を
 * コードで持っていた。ここではそれぞれをデータパックのレシピ型として登録する。
 * 型のID（academy:metal_forming、academy:imag_fusion）はレシピJSONの"type"に書かれるので変えない。
 */
public final class MachineRecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, AcademyCraft.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AcademyCraft.MODID);

    public static final RegistryObject<RecipeType<MetalFormerRecipe>> FORMING_TYPE = TYPES.register("metal_forming", () -> type("metal_forming"));
    public static final RegistryObject<RecipeSerializer<MetalFormerRecipe>> FORMING_SERIALIZER = SERIALIZERS.register("metal_forming", MetalFormerRecipe.Serializer::new);
    public static final RegistryObject<RecipeType<ImagFusionRecipe>> FUSION_TYPE = TYPES.register("imag_fusion", () -> type("imag_fusion"));
    public static final RegistryObject<RecipeSerializer<ImagFusionRecipe>> FUSION_SERIALIZER = SERIALIZERS.register("imag_fusion", ImagFusionRecipe.Serializer::new);

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
