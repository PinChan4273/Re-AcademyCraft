package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.world.item.MatterUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 虚像投影液と物質ユニットの登録。原作ACFluids/BlockImagPhaseの遅く短い広がりと、原作ItemMatterUnitの容器。 */
public final class PhaseContent {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, "academy");
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, "academy");
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "academy");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "academy");
    public static final RegistryObject<FluidType> TYPE = TYPES.register("phase_liquid", io.github.pinchan4273.reacademycraft.world.fluid.PhaseFluidType::new);
    public static final RegistryObject<ForgeFlowingFluid.Source> SOURCE = FLUIDS.register("phase_liquid", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING = FLUIDS.register("flowing_phase_liquid", () -> new ForgeFlowingFluid.Flowing(properties()));
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "academy");
    public static final RegistryObject<LiquidBlock> BLOCK = BLOCKS.register("phase_liquid", () -> new io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock(
            SOURCE, BlockBehaviour.Properties.copy(Blocks.WATER).lightLevel(s -> 8), PhaseContent::tileType));
    /** 原作TileImagPhase。液体の各ブロックにあり、RenderImagPhaseLiquidのためのもの。 */
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile>> TILE =
            BLOCK_ENTITIES.register("phase_liquid", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .<io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile>of((pos, state) -> new io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile(tileType(), pos, state), BLOCK.get()).build(null));
    private static net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile> tileType() { return TILE.get(); }
    public static final RegistryObject<MatterUnit> EMPTY = ITEMS.register("matter_unit", () -> new MatterUnit(false));
    public static final RegistryObject<MatterUnit> FILLED = ITEMS.register("matter_unit_phase_liquid", () -> new MatterUnit(true));
    private static ForgeFlowingFluid.Properties properties() {
        // 原作の粘度6000 / 200 = 30tick、quanta 3 → 水平に2段。
        // 原作独自の縦の流れの高さは再現しない: 現行の8段階の液体は、ここでは8/5/2へ対応付ける。
        return new ForgeFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK)
                .tickRate(30).slopeFindDistance(1).levelDecreasePerBlock(3).explosionResistance(100);
    }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); BLOCK_ENTITIES.register(bus); ITEMS.register(bus); }
    private PhaseContent() {}
}
