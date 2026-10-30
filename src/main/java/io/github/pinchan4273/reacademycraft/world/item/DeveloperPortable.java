package io.github.pinchan4273.reacademycraft.world.item;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.develop.DeveloperMenu;
import io.github.pinchan4273.reacademycraft.network.AbilitySyncEvents;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import io.github.pinchan4273.reacademycraft.world.AcademyComponents;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 携帯型開発機。原作ItemDeveloper（WeAthFolD）はItemEnergyBaseで、DeveloperType.PORTABLEの容量10000 IF・帯域50 IF/tを持つ。
 * 使うと開発機の画面を開く（原作はクライアントでDeveloperUIを開く。ここではサーバーがメニューを開く）。手と地面では
 * 原作のdeveloper_portable.objをPortableDeveloperRendererが描き、GUIではアイコンのまま。
 * <p>エネルギーは原作IFItemManagerと同じくスタックにあり（Forge版はNBT、ここではData Component）、ダメージ値は使わない。
 * エネルギーのcapabilityとは原作RFSupportの1 IF = 4 FEで換算し、IFに満たない端数（0〜3 FE）も別に保つ。
 * 保存はacademy:energyとacademy:energy_remainder（Forge版のNBTキーはacademy_energyとacademy_energy_remainder）。
 */
public final class DeveloperPortable extends DescribedItem {
    public static final int MAX_ENERGY = 10000, BANDWIDTH = 50, FE_PER_IF = 4;
    private static final int MAX_FE = MAX_ENERGY * FE_PER_IF, BANDWIDTH_FE = BANDWIDTH * FE_PER_IF;

    public DeveloperPortable() {
        super(new Properties().stacksTo(1));
    }

    /** 蓄えたIF（端数は含めない）。 */
    public static int energy(ItemStack stack) {
        if (!(stack.getItem() instanceof DeveloperPortable)) return 0;
        return clamp(stack.getOrDefault(AcademyComponents.ENERGY, 0), MAX_ENERGY);
    }

    /** 端数を含めたFE。読むだけで、クライアントのモデルからも使える。 */
    public static int energyFE(ItemStack stack) {
        return stack.getItem() instanceof DeveloperPortable ? storedFE(stack) : 0;
    }

    /** IFを直接設定し、端数を捨てる。サーバーの充電・消費と、クリエイティブタブの満タンの品に使う。 */
    public static void setEnergy(ItemStack stack, int amount) {
        if (!(stack.getItem() instanceof DeveloperPortable)) return;
        stack.set(AcademyComponents.ENERGY, clamp(amount, MAX_ENERGY));
        stack.remove(AcademyComponents.ENERGY_REMAINDER);
    }

    /** 原作IFItemManager.pull（帯域を無視）: 足りればamount IFを使う。 */
    public static boolean consume(ItemStack stack, int amount) {
        if (amount < 0 || !(stack.getItem() instanceof DeveloperPortable) || energy(stack) < amount) return false;
        storeFE(stack, storedFE(stack) - amount * FE_PER_IF);
        return true;
    }

    private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }

    private static int storedFE(ItemStack stack) {
        int fraction = clamp(stack.getOrDefault(AcademyComponents.ENERGY_REMAINDER, 0), FE_PER_IF - 1);
        return Math.min(MAX_FE, energy(stack) * FE_PER_IF + fraction);
    }

    private static void storeFE(ItemStack stack, int fe) {
        int bounded = clamp(fe, MAX_FE);
        setEnergy(stack, bounded / FE_PER_IF);
        if (bounded % FE_PER_IF != 0) stack.set(AcademyComponents.ENERGY_REMAINDER, bounded % FE_PER_IF);
    }

    /** 原作IFItemManager.charge / pull の、1tickあたりの帯域つきの出し入れ。エネルギーのcapabilityとして見せる。 */
    private static final class Storage implements IEnergyStorage {
        private final ItemStack stack;

        Storage(ItemStack stack) { this.stack = stack; }

        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            int stored = storedFE(stack), moved = Math.min(MAX_FE - stored, Math.min(BANDWIDTH_FE, Math.max(0, maxReceive)));
            if (!simulate && moved > 0) storeFE(stack, stored + moved);
            return moved;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            int stored = storedFE(stack), moved = Math.min(stored, Math.min(BANDWIDTH_FE, Math.max(0, maxExtract)));
            if (!simulate && moved > 0) storeFE(stack, stored - moved);
            return moved;
        }
        @Override public int getEnergyStored() { return storedFE(stack); }
        @Override public int getMaxEnergyStored() { return MAX_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }

    /** エネルギーのcapability。AcademyCapabilitiesが登録する（Forge版はinitCapabilities）。 */
    public static IEnergyStorage energyStorage(ItemStack stack) {
        return new Storage(stack);
    }

    /** 原作onItemRightClick: 開発機の画面を開く。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.isSpectator()) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer viewer)) return InteractionResultHolder.consume(stack);
        var data = AbilityCapabilities.of(viewer).orElse(null);
        // 書き込めない（新しい版の）能力データの者や、別の画面を開いている者には開かない。
        if (data == null || data.isReadOnly() || viewer.containerMenu != viewer.inventoryMenu) return InteractionResultHolder.fail(stack);
        AbilitySyncEvents.sync(viewer, true);
        var title = Component.translatable("academy.developer.title");
        viewer.openMenu(new SimpleMenuProvider((id, inventory, who) -> new DeveloperMenu(id, inventory, hand), title),
                extra -> extra.writeBoolean(false));
        return InteractionResultHolder.consume(stack);
    }

    /** 原作IFItemManager.getDescription: 「蓄え/容量 IF」。 */
    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.literal(energy(stack) + "/" + MAX_ENERGY + " IF"));
    }

    @Override public boolean isBarVisible(ItemStack stack) { return LegacyEnergyBar.visible(energy(stack), MAX_ENERGY); }
    @Override public int getBarWidth(ItemStack stack) { return LegacyEnergyBar.width(energy(stack), MAX_ENERGY); }
    @Override public int getBarColor(ItemStack stack) { return LegacyEnergyBar.color(energy(stack), MAX_ENERGY); }

    // 手に持ったときの独自の描画（クライアント側の拡張）は、client/ClientExtensionsRegistrationで登録する。
}
