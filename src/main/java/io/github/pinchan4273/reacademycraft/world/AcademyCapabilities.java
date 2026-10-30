package io.github.pinchan4273.reacademycraft.world;

import io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.CatEngineBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.NormalDeveloperBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
import io.github.pinchan4273.reacademycraft.world.item.DeveloperPortable;
import io.github.pinchan4273.reacademycraft.world.item.EnergyUnit;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * 機械とアイテムのcapability（エネルギー・アイテム・液体）。NeoForgeはRegisterCapabilitiesEventで登録する
 * （Forge 1.20.1版は各ブロックエンティティのgetCapabilityと、アイテムのinitCapabilities）。
 * どの面に何を出すかの条件は、各ブロックエンティティの*Capabilityメソッド（Forge版のgetCapabilityと同じ条件）にある。
 */
public final class AcademyCapabilities {
    private AcademyCapabilities() { }

    public static void register(IEventBus modBus) { modBus.addListener(AcademyCapabilities::registerCapabilities); }

    /** Forge版の{@code blockEntity.getCapability(ForgeCapabilities.ENERGY, side)}: そのブロックエンティティの位置のエネルギーのcapability。 */
    @Nullable public static IEnergyStorage energy(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null || blockEntity.getLevel() == null) return null;
        return blockEntity.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
    }
    /** Forge版の{@code stack.getCapability(ForgeCapabilities.ENERGY)}。 */
    @Nullable public static IEnergyStorage energy(ItemStack stack) { return stack.getCapability(Capabilities.EnergyStorage.ITEM); }
    /** Forge版の{@code blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)}。 */
    @Nullable public static net.neoforged.neoforge.items.IItemHandler items(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null || blockEntity.getLevel() == null) return null;
        return blockEntity.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
    }
    /** Forge版の{@code blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)}。 */
    @Nullable public static net.neoforged.neoforge.fluids.capability.IFluidHandler fluids(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null || blockEntity.getLevel() == null) return null;
        return blockEntity.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var energy = Capabilities.EnergyStorage.BLOCK;
        var items = Capabilities.ItemHandler.BLOCK;
        var fluids = Capabilities.FluidHandler.BLOCK;
        event.registerBlockEntity(energy, AcademyContent.INTERFERER_ENTITY.get(), AbilityInterfererBlockEntity::energyCapability);
        event.registerBlockEntity(items, AcademyContent.INTERFERER_ENTITY.get(), AbilityInterfererBlockEntity::itemCapability);
        event.registerBlockEntity(energy, AcademyContent.CAT_ENGINE_ENTITY.get(), CatEngineBlockEntity::energyCapability);
        event.registerBlockEntity(energy, AcademyContent.IMAG_FUSOR_ENTITY.get(), ImagFusorBlockEntity::energyCapability);
        event.registerBlockEntity(fluids, AcademyContent.IMAG_FUSOR_ENTITY.get(), ImagFusorBlockEntity::fluidCapability);
        event.registerBlockEntity(items, AcademyContent.IMAG_FUSOR_ENTITY.get(), ImagFusorBlockEntity::itemCapability);
        event.registerBlockEntity(energy, AcademyContent.METAL_ENTITY.get(), MetalFormerBlockEntity::energyCapability);
        event.registerBlockEntity(items, AcademyContent.METAL_ENTITY.get(), MetalFormerBlockEntity::itemCapability);
        event.registerBlockEntity(energy, AcademyContent.DEV_NORMAL_ENTITY.get(), NormalDeveloperBlockEntity::energyCapability);
        event.registerBlockEntity(energy, AcademyContent.DEV_ADVANCED_ENTITY.get(), NormalDeveloperBlockEntity::energyCapability);
        event.registerBlockEntity(energy, AcademyContent.PHASE_GEN_ENTITY.get(), PhaseGeneratorBlockEntity::energyCapability);
        event.registerBlockEntity(fluids, AcademyContent.PHASE_GEN_ENTITY.get(), PhaseGeneratorBlockEntity::fluidCapability);
        event.registerBlockEntity(items, AcademyContent.PHASE_GEN_ENTITY.get(), PhaseGeneratorBlockEntity::itemCapability);
        event.registerBlockEntity(energy, AcademyContent.SOLAR_ENTITY.get(), SolarGeneratorBlockEntity::energyCapability);
        event.registerBlockEntity(items, AcademyContent.SOLAR_ENTITY.get(), SolarGeneratorBlockEntity::itemCapability);
        event.registerBlockEntity(energy, AcademyContent.WIND_BODY_ENTITY.get(), WindBodyBlockEntity::energyCapability);
        event.registerBlockEntity(items, AcademyContent.WIND_BODY_ENTITY.get(), WindBodyBlockEntity::itemCapability);
        event.registerBlockEntity(items, AcademyContent.MATRIX_ENTITY.get(), WirelessMatrixBlockEntity::itemCapability);
        event.registerBlockEntity(energy, AcademyContent.NODE_ENTITY.get(), WirelessNodeBlockEntity::energyCapability);
        event.registerBlockEntity(items, AcademyContent.NODE_ENTITY.get(), WirelessNodeBlockEntity::itemCapability);
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> EnergyUnit.energyStorage(stack), MaterialContent.item("energy_unit"));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> DeveloperPortable.energyStorage(stack), AcademyContent.DEVELOPER_PORTABLE.get());
    }
}
