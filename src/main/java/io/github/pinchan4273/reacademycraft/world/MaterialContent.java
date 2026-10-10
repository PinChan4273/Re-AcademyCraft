package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.world.item.DescribedItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 鉱石・素材の登録。内容は原作ACBlocks/ACItems/default.recipeのもの。
 * WeAthFolD/KSkun。戦利品・採掘の段階・生成は1.20.1のデータパックにある。
 */
public final class MaterialContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "academy");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "academy");
    public static final List<String> MATERIAL_IDS = List.of("constraint_ingot", "constraint_plate", "imag_silicon_ingot", "wafer",
            "imag_silicon_piece", "crystal_low", "reso_crystal", "brain_component", "info_component", "energy_unit", "energy_convert_component",
            // 原作の残りの素材。上位の2つの結晶は、虚像融合機が低純度のものから作るもの。mat_coreの3つは原作のmetadata 0、1、2で、
            // 1.20.1では別々に分ける必要がある。
            "crystal_normal", "crystal_pure", "resonance_component", "needle", "mat_core_0", "mat_core_1", "mat_core_2");
    public static final List<String> ORE_IDS = List.of("constraint_metal", "crystal_ore", "imagsil_ore", "reso_ore");
    private static final Map<String, RegistryObject<Item>> MATERIALS = new LinkedHashMap<>();
    private static final Map<String, RegistryObject<Block>> ORES = new LinkedHashMap<>();
    static {
        for (var id : MATERIAL_IDS) MATERIALS.put(id, ITEMS.register(id, () -> id.equals("energy_unit")
                ? new io.github.pinchan4273.reacademycraft.world.item.EnergyUnit() : new DescribedItem(new Item.Properties())));
        for (var id : ORE_IDS) {
            float hardness = id.equals("constraint_metal") ? 4 : id.equals("imagsil_ore") ? 3.75f : 3;
            var block = BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of().strength(hardness).sound(SoundType.STONE).requiresCorrectToolForDrops()));
            ORES.put(id, block); MATERIALS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
    }
    private MaterialContent() { }
    public static Item item(String id) { return java.util.Objects.requireNonNull(MATERIALS.get(id), id).get(); }
    public static Block ore(String id) { return java.util.Objects.requireNonNull(ORES.get(id), id).get(); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); }
    public static void creativeItems(CreativeModeTab.Output output) {
        MATERIALS.values().forEach(item -> output.accept(item.get()));
        var charged = new net.minecraft.world.item.ItemStack(item("energy_unit"));
        io.github.pinchan4273.reacademycraft.world.item.EnergyUnit.setEnergyFE(charged, io.github.pinchan4273.reacademycraft.world.item.EnergyUnit.MAX_FE);
        output.accept(charged);
    }
}
