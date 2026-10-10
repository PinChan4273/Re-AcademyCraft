package io.github.pinchan4273.reacademycraft.world.item;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * エネルギーユニット（原作ItemEnergyBase、10000 IF・帯域20 IF）。
 * WeAthFolD。正確なFEをNBTに保存し、空から始め、アイテムの耐久値をエネルギーとして使わない。
 */
public final class EnergyUnit extends DescribedItem {
    public static final int MAX_FE = 40000, BANDWIDTH_FE = 80;
    private static final String ENERGY = "academy_energy_fe";
    public EnergyUnit() { super(new Properties().stacksTo(1)); }
    public static int energyFE(ItemStack stack) {
        if (!(stack.getItem() instanceof EnergyUnit) || stack.getCount() != 1 || !stack.hasTag()) return 0;
        return Math.max(0, Math.min(MAX_FE, stack.getTag().getInt(ENERGY)));
    }
    public static void setEnergyFE(ItemStack stack, int amount) {
        if (stack.getItem() instanceof EnergyUnit && stack.getCount() == 1)
            stack.getOrCreateTag().putInt(ENERGY, Math.max(0, Math.min(MAX_FE, amount)));
    }
    @Override public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<IEnergyStorage> energy = LazyOptional.of(() -> new IEnergyStorage() {
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    if (stack.getCount() != 1) return 0;
                    int n = Math.min(MAX_FE - energyFE(stack), Math.min(BANDWIDTH_FE, Math.max(0, maxReceive)));
                    if (!simulate && n > 0) setEnergyFE(stack, energyFE(stack) + n); return n;
                }
                public int extractEnergy(int maxExtract, boolean simulate) {
                    if (stack.getCount() != 1) return 0;
                    int n = Math.min(energyFE(stack), Math.min(BANDWIDTH_FE, Math.max(0, maxExtract)));
                    if (!simulate && n > 0) setEnergyFE(stack, energyFE(stack) - n); return n;
                }
                public int getEnergyStored() { return energyFE(stack); }
                public int getMaxEnergyStored() { return MAX_FE; }
                public boolean canReceive() { return stack.getCount() == 1; }
                public boolean canExtract() { return stack.getCount() == 1; }
            });
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return cap == ForgeCapabilities.ENERGY ? energy.cast() : LazyOptional.empty();
            }
        };
    }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> text, TooltipFlag flag) {
        super.appendHoverText(stack, level, text, flag);
        // 原作IFItemManager.getDescription。
        text.add(Component.literal(String.format(java.util.Locale.ROOT, "%.0f/%.0f IF", energyFE(stack) / 4.0, MAX_FE / 4.0)));
        if (stack.getCount() > 1) text.add(Component.translatable("academy.energy.split_stack"));
    }
    @Override public boolean isBarVisible(ItemStack stack) { return LegacyEnergyBar.visible(energyFE(stack), MAX_FE); }
    @Override public int getBarWidth(ItemStack stack) { return LegacyEnergyBar.width(energyFE(stack), MAX_FE); }
    @Override public int getBarColor(ItemStack stack) { return LegacyEnergyBar.color(energyFE(stack), MAX_FE); }
}
