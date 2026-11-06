package io.github.pinchan4273.reacademycraft.world.transfer;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.slf4j.Logger;

/**
 * 機械の非公開の中身（1.21.1 と同じ IEnergyStorage・IItemHandler・IFluidHandler。守りの条件を持つ）を、26.2 のトランザクションの capability として外へ見せる。
 *
 * <p>変更はルートの commit の時にまとめて本体へ当てる。それまでの量は「本体＋保留」で見せ、本体の simulate で受け入れの量を決める。
 * NeoForge の旧 API は forRemoval なので、後の版で機械の側をトランザクションへ移すまでの橋（移植の記録の負債の欄）。
 */
public final class LegacyTransfer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LegacyTransfer() { }

    public static EnergyHandler energy(IEnergyStorage legacy) { return legacy == null ? null : new EnergyAdapter(legacy); }

    public static ResourceHandler<ItemResource> items(IItemHandler legacy) { return legacy == null ? null : new ItemAdapter(legacy); }

    public static ResourceHandler<FluidResource> fluids(IFluidHandler legacy) { return legacy == null ? null : new FluidAdapter(legacy); }

    /** capability がこの橋なら本体を返す（自分の機械どうしはトランザクションを通さず 1.21.1 と同じ経路で動く）。 */
    public static IEnergyStorage unwrap(EnergyHandler handler) { return handler instanceof EnergyAdapter a ? a.legacy : null; }

    public static IItemHandler unwrapItems(ResourceHandler<ItemResource> handler) { return handler instanceof ItemAdapter a ? a.legacy : null; }

    public static IFluidHandler unwrapFluids(ResourceHandler<FluidResource> handler) { return handler instanceof FluidAdapter a ? a.legacy : null; }

    private static final class EnergyAdapter extends SnapshotJournal<Integer> implements EnergyHandler {
        final IEnergyStorage legacy;
        /** 保留の量。正は受け入れ、負は取り出し。 */
        private int pending;

        EnergyAdapter(IEnergyStorage legacy) { this.legacy = legacy; }

        @Override public long getAmountAsLong() { return Math.max(0L, (long) legacy.getEnergyStored() + pending); }
        @Override public long getCapacityAsLong() { return legacy.getMaxEnergyStored(); }

        @Override public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0 || pending < 0) return 0;
            int accepted = Math.min(amount, legacy.receiveEnergy(pending + amount, true) - pending);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            pending += accepted;
            return accepted;
        }

        @Override public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0 || pending > 0) return 0;
            int accepted = Math.min(amount, legacy.extractEnergy(-pending + amount, true) + pending);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            pending -= accepted;
            return accepted;
        }

        @Override protected Integer createSnapshot() { return pending; }
        @Override protected void revertToSnapshot(Integer snapshot) { pending = snapshot; }

        @Override protected void onRootCommit(Integer original) {
            int amount = pending;
            pending = 0;
            int done = amount > 0 ? legacy.receiveEnergy(amount, false) : amount < 0 ? legacy.extractEnergy(-amount, false) : 0;
            if (done != Math.abs(amount)) LOGGER.warn("Energy transfer changed between simulate and commit: {} of {}", done, amount);
        }
    }

    /** スロットごとの保留。同じトランザクションで同じスロットの入れと出しは混ぜない（混ぜる呼び出しには 0 を返す）。 */
    private abstract static class SlotAdapter<R extends Resource> extends SnapshotJournal<int[]> implements ResourceHandler<R> {
        private final int[] pending;
        private final Object[] pendingResource;

        SlotAdapter(int size) {
            pending = new int[size];
            pendingResource = new Object[size];
        }

        abstract R current(int index);
        abstract int currentAmount(int index);
        /** 本体が total のうち受け入れる量（simulate）。 */
        abstract int simulateInsert(int index, R resource, int total);
        /** 本体が total のうち出せる量（simulate）。 */
        abstract int simulateExtract(int index, R resource, int total);
        abstract int commitInsert(int index, R resource, int total);
        abstract int commitExtract(int index, R resource, int total);

        @Override public int size() { return pending.length; }

        @SuppressWarnings("unchecked")
        @Override public R getResource(int index) {
            R now = current(index);
            return now.isEmpty() && pending[index] > 0 ? (R) pendingResource[index] : now;
        }

        @Override public long getAmountAsLong(int index) { return Math.max(0L, (long) currentAmount(index) + pending[index]); }

        @Override public int insert(int index, R resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || pending[index] < 0 || (pending[index] > 0 && !resource.equals(pendingResource[index]))) return 0;
            int accepted = Math.min(amount, simulateInsert(index, resource, pending[index] + amount) - pending[index]);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            pending[index] += accepted;
            pendingResource[index] = resource;
            return accepted;
        }

        @Override public int extract(int index, R resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || pending[index] > 0 || !resource.equals(current(index))) return 0;
            int accepted = Math.min(amount, simulateExtract(index, resource, -pending[index] + amount) + pending[index]);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            pending[index] -= accepted;
            pendingResource[index] = resource;
            return accepted;
        }

        @Override protected int[] createSnapshot() { return pending.clone(); }
        @Override protected void revertToSnapshot(int[] snapshot) { System.arraycopy(snapshot, 0, pending, 0, pending.length); }

        @SuppressWarnings("unchecked")
        @Override protected void onRootCommit(int[] original) {
            for (int i = 0; i < pending.length; i++) {
                int amount = pending[i];
                pending[i] = 0;
                if (amount == 0) continue;
                R resource = (R) pendingResource[i];
                int done = amount > 0 ? commitInsert(i, resource, amount) : commitExtract(i, resource, -amount);
                if (done != Math.abs(amount)) LOGGER.warn("Transfer of {} changed between simulate and commit: {} of {}", resource, done, amount);
            }
        }
    }

    private static final class ItemAdapter extends SlotAdapter<ItemResource> {
        final IItemHandler legacy;

        ItemAdapter(IItemHandler legacy) { super(legacy.getSlots()); this.legacy = legacy; }

        @Override ItemResource current(int index) { return ItemResource.of(legacy.getStackInSlot(index)); }
        @Override int currentAmount(int index) { return legacy.getStackInSlot(index).getCount(); }
        @Override int simulateInsert(int index, ItemResource resource, int total) { return total - legacy.insertItem(index, resource.toStack(total), true).getCount(); }
        @Override int simulateExtract(int index, ItemResource resource, int total) { return legacy.extractItem(index, total, true).getCount(); }
        @Override int commitInsert(int index, ItemResource resource, int total) { return total - legacy.insertItem(index, resource.toStack(total), false).getCount(); }
        @Override int commitExtract(int index, ItemResource resource, int total) { return legacy.extractItem(index, total, false).getCount(); }
        @Override public long getCapacityAsLong(int index, ItemResource resource) {
            return resource.isEmpty() ? legacy.getSlotLimit(index) : Math.min(legacy.getSlotLimit(index), resource.getMaxStackSize());
        }
        @Override public boolean isValid(int index, ItemResource resource) {
            ItemStack stack = resource.toStack();
            return legacy.isItemValid(index, stack);
        }
    }

    private static final class FluidAdapter extends SlotAdapter<FluidResource> {
        final IFluidHandler legacy;

        FluidAdapter(IFluidHandler legacy) { super(legacy.getTanks()); this.legacy = legacy; }

        @Override FluidResource current(int index) { return FluidResource.of(legacy.getFluidInTank(index)); }
        @Override int currentAmount(int index) { return legacy.getFluidInTank(index).getAmount(); }
        @Override int simulateInsert(int index, FluidResource resource, int total) { return legacy.fill(resource.toStack(total), IFluidHandler.FluidAction.SIMULATE); }
        @Override int simulateExtract(int index, FluidResource resource, int total) { return legacy.drain(resource.toStack(total), IFluidHandler.FluidAction.SIMULATE).getAmount(); }
        @Override int commitInsert(int index, FluidResource resource, int total) { return legacy.fill(resource.toStack(total), IFluidHandler.FluidAction.EXECUTE); }
        @Override int commitExtract(int index, FluidResource resource, int total) { return legacy.drain(resource.toStack(total), IFluidHandler.FluidAction.EXECUTE).getAmount(); }
        @Override public long getCapacityAsLong(int index, FluidResource resource) { return legacy.getTankCapacity(index); }
        @Override public boolean isValid(int index, FluidResource resource) {
            FluidStack stack = resource.toStack(1);
            return legacy.isFluidValid(index, stack);
        }
    }
}
