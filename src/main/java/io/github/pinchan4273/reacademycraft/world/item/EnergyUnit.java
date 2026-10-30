package io.github.pinchan4273.reacademycraft.world.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import io.github.pinchan4273.reacademycraft.world.AcademyComponents;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * エネルギーユニット（原作ItemEnergyBase、10000 IF・帯域20 IF）。
 * WeAthFolD。正確なFEをアイテムのData Component（Forge版はNBT）に保存し、空から始め、アイテムの耐久値をエネルギーとして使わない。
 * エネルギーのcapabilityは{@link #energyStorage}で、AcademyCapabilitiesが登録する（Forge版はinitCapabilities）。
 */
public final class EnergyUnit extends DescribedItem {
    public static final int MAX_FE = 40000, BANDWIDTH_FE = 80;
    public EnergyUnit() { super(new Properties().stacksTo(1)); }
    public static int energyFE(ItemStack stack) {
        if (!(stack.getItem() instanceof EnergyUnit) || stack.getCount() != 1) return 0;
        return Math.max(0, Math.min(MAX_FE, stack.getOrDefault(AcademyComponents.ENERGY_FE, 0)));
    }
    public static void setEnergyFE(ItemStack stack, int amount) {
        if (stack.getItem() instanceof EnergyUnit && stack.getCount() == 1)
            stack.set(AcademyComponents.ENERGY_FE, Math.max(0, Math.min(MAX_FE, amount)));
    }
    public static IEnergyStorage energyStorage(ItemStack stack) {
        return new IEnergyStorage() {
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
        };
    }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> text, TooltipFlag flag) {
        super.appendHoverText(stack, context, text, flag);
        // 原作IFItemManager.getDescription。
        text.add(Component.literal(String.format(java.util.Locale.ROOT, "%.0f/%.0f IF", energyFE(stack) / 4.0, MAX_FE / 4.0)));
        if (stack.getCount() > 1) text.add(Component.translatable("academy.energy.split_stack"));
    }
    @Override public boolean isBarVisible(ItemStack stack) { return LegacyEnergyBar.visible(energyFE(stack), MAX_FE); }
    @Override public int getBarWidth(ItemStack stack) { return LegacyEnergyBar.width(energyFE(stack), MAX_FE); }
    @Override public int getBarColor(ItemStack stack) { return LegacyEnergyBar.color(energyFE(stack), MAX_FE); }
}
