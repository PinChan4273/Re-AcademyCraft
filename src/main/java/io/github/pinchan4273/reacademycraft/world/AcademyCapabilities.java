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
import io.github.pinchan4273.reacademycraft.world.transfer.LegacyTransfer;
import net.neoforged.neoforge.transfer.access.ItemAccess;

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
        var handler = blockEntity.getLevel().getCapability(Capabilities.Energy.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (handler == null) return null;
        var own = LegacyTransfer.unwrap(handler);
        return own != null ? own : IEnergyStorage.of(handler);
    }
    /** Forge版の{@code stack.getCapability(ForgeCapabilities.ENERGY)}。 */
    @Nullable public static IEnergyStorage energy(ItemStack stack) {
        // 26.2 の ItemAccess.forStack は空のスタックで例外を投げる。1.21.1 の getCapability は空なら null だった。
        if (stack.isEmpty()) return null;
        // 自分のアイテムは 1.21.1 と同じ直接の経路（個数と帯域の条件もそのまま）。ほかの MOD のアイテムは NeoForge の capability から。
        if (stack.getItem() instanceof EnergyUnit) return EnergyUnit.energyStorage(stack);
        if (stack.getItem() instanceof DeveloperPortable) return DeveloperPortable.energyStorage(stack);
        var handler = stack.getCapability(Capabilities.Energy.ITEM, ItemAccess.forStack(stack));
        if (handler == null) return null;
        var own = LegacyTransfer.unwrap(handler);
        return own != null ? own : IEnergyStorage.of(handler);
    }
    /** Forge版の{@code blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)}。 */
    @Nullable public static net.neoforged.neoforge.items.IItemHandler items(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null || blockEntity.getLevel() == null) return null;
        var handler = blockEntity.getLevel().getCapability(Capabilities.Item.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (handler == null) return null;
        var own = LegacyTransfer.unwrapItems(handler);
        return own != null ? own : net.neoforged.neoforge.items.IItemHandler.of(handler);
    }
    /** Forge版の{@code blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side)}。 */
    @Nullable public static net.neoforged.neoforge.fluids.capability.IFluidHandler fluids(@Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (blockEntity == null || blockEntity.getLevel() == null) return null;
        var handler = blockEntity.getLevel().getCapability(Capabilities.Fluid.BLOCK, blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (handler == null) return null;
        var own = LegacyTransfer.unwrapFluids(handler);
        return own != null ? own : net.neoforged.neoforge.fluids.capability.IFluidHandler.of(handler);
    }

    /**
     * 外から見たアイテムのエネルギー。1.21.1 と同じ本体（{@code EnergyUnit.energyStorage} など）を、ItemAccess の中身の写しの上で動かし、
     * 結果を ItemAccess へ返す。重なったスタックは 1.21.1 と同じく出し入れしない。
     */
    private static final class ItemEnergy extends net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler {
        private final java.util.function.Function<ItemStack, IEnergyStorage> body;

        ItemEnergy(ItemAccess access, java.util.function.Function<ItemStack, IEnergyStorage> body, net.minecraft.core.component.DataComponentType<Integer> component) {
            super(access, component, Integer.MAX_VALUE);
            this.body = body;
        }
        private IEnergyStorage on(net.neoforged.neoforge.transfer.item.ItemResource resource) { return body.apply(resource.toStack(1)); }
        @Override protected int getAmountFrom(net.neoforged.neoforge.transfer.item.ItemResource resource) { return on(resource).getEnergyStored(); }
        @Override public long getCapacityAsLong() { return (long) itemAccess.getAmount() * on(itemAccess.getResource()).getMaxEnergyStored(); }
        @Override protected net.neoforged.neoforge.transfer.item.ItemResource update(net.neoforged.neoforge.transfer.item.ItemResource resource, int newAmount) {
            ItemStack stack = resource.toStack(1);
            IEnergyStorage storage = body.apply(stack);
            int delta = newAmount - storage.getEnergyStored();
            if (delta > 0) storage.receiveEnergy(delta, false); else if (delta < 0) storage.extractEnergy(-delta, false);
            return storage.getEnergyStored() == newAmount ? net.neoforged.neoforge.transfer.item.ItemResource.of(stack) : net.neoforged.neoforge.transfer.item.ItemResource.EMPTY;
        }
        @Override public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return itemAccess.getAmount() == 1 ? super.insert(Math.min(amount, on(itemAccess.getResource()).receiveEnergy(amount, true)), transaction) : 0;
        }
        @Override public int extract(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return itemAccess.getAmount() == 1 ? super.extract(Math.min(amount, on(itemAccess.getResource()).extractEnergy(amount, true)), transaction) : 0;
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var energy = Capabilities.Energy.BLOCK;
        var items = Capabilities.Item.BLOCK;
        var fluids = Capabilities.Fluid.BLOCK;
        event.registerBlockEntity(energy, AcademyContent.INTERFERER_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(items, AcademyContent.INTERFERER_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.CAT_ENGINE_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.IMAG_FUSOR_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(fluids, AcademyContent.IMAG_FUSOR_ENTITY.get(), (entity, side) -> LegacyTransfer.fluids(entity.fluidCapability(side)));
        event.registerBlockEntity(items, AcademyContent.IMAG_FUSOR_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.METAL_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(items, AcademyContent.METAL_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.DEV_NORMAL_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.DEV_ADVANCED_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.PHASE_GEN_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(fluids, AcademyContent.PHASE_GEN_ENTITY.get(), (entity, side) -> LegacyTransfer.fluids(entity.fluidCapability(side)));
        event.registerBlockEntity(items, AcademyContent.PHASE_GEN_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.SOLAR_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(items, AcademyContent.SOLAR_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.WIND_BODY_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(items, AcademyContent.WIND_BODY_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(items, AcademyContent.MATRIX_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerBlockEntity(energy, AcademyContent.NODE_ENTITY.get(), (entity, side) -> LegacyTransfer.energy(entity.energyCapability(side)));
        event.registerBlockEntity(items, AcademyContent.NODE_ENTITY.get(), (entity, side) -> LegacyTransfer.items(entity.itemCapability(side)));
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemEnergy(access, EnergyUnit::energyStorage, AcademyComponents.ENERGY_FE.get()), MaterialContent.item("energy_unit"));
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemEnergy(access, DeveloperPortable::energyStorage, AcademyComponents.ENERGY_FE.get()), AcademyContent.DEVELOPER_PORTABLE.get());
    }
}
