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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** 虚像投影液と物質ユニットの登録。原作ACFluids/BlockImagPhaseの遅く短い広がりと、原作ItemMatterUnitの容器。 */
public final class PhaseContent {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, "academy");
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(net.minecraft.core.registries.Registries.FLUID, "academy");
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK, "academy");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, "academy");
    public static final DeferredHolder<net.neoforged.neoforge.fluids.FluidType, FluidType> TYPE = TYPES.register("phase_liquid", io.github.pinchan4273.reacademycraft.world.fluid.PhaseFluidType::new);
    public static final DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Source> SOURCE = FLUIDS.register("phase_liquid", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<net.minecraft.world.level.material.Fluid, BaseFlowingFluid.Flowing> FLOWING = FLUIDS.register("flowing_phase_liquid", () -> new BaseFlowingFluid.Flowing(properties()));
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, "academy");
    public static final DeferredHolder<net.minecraft.world.level.block.Block, LiquidBlock> BLOCK = BLOCKS.register("phase_liquid", () -> new io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock(
            SOURCE, BlockBehaviour.Properties.ofLegacyCopy(Blocks.WATER).lightLevel(s -> 8), PhaseContent::tileType));
    /** 原作TileImagPhase。液体の各ブロックにあり、RenderImagPhaseLiquidのためのもの。 */
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile>> TILE =
            BLOCK_ENTITIES.register("phase_liquid", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .<io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile>of((pos, state) -> new io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile(tileType(), pos, state), BLOCK.get()).build(null));
    private static net.minecraft.world.level.block.entity.BlockEntityType<io.github.pinchan4273.reacademycraft.world.block.PhaseLiquidBlock.Tile> tileType() { return TILE.get(); }
    public static final DeferredHolder<net.minecraft.world.item.Item, MatterUnit> EMPTY = ITEMS.register("matter_unit", () -> new MatterUnit(false));
    public static final DeferredHolder<net.minecraft.world.item.Item, MatterUnit> FILLED = ITEMS.register("matter_unit_phase_liquid", () -> new MatterUnit(true));
    private static BaseFlowingFluid.Properties properties() {
        // 原作の粘度6000 / 200 = 30tick、quanta 3 → 水平に2段。
        // 原作独自の縦の流れの高さは再現しない: 現行の8段階の液体は、ここでは8/5/2へ対応付ける。
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING).block(BLOCK)
                .tickRate(30).slopeFindDistance(1).levelDecreasePerBlock(3).explosionResistance(100);
    }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); BLOCK_ENTITIES.register(bus); ITEMS.register(bus); }
    private PhaseContent() {}
}
