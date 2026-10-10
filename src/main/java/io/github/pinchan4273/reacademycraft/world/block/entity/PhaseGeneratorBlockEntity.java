package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 虚像発電機の状態。原作TilePhaseGen/TileGeneratorBase（WeAthFolD）:
 * 8000mBのタンク、6000 IFのバッファ、0.5 IF/mB、100mB/tick。
 * 他の無線の使用者と同じくnodeに繋ぐ（WirelessNetworks）。原作は音を鳴らさない。原作のモデルはPhaseGeneratorRendererが描く。
 */
public final class PhaseGeneratorBlockEntity extends BlockEntity {
    public static final int MAX_MILLI_IF = 6000000, TANK_MB = 8000, UNIT_MB = 1000, BURN_MB = 100, OUTPUT_FE = 200;
    public enum Status {
        NO_FUEL, GENERATING, BUFFER_FULL, RETURN_BLOCKED, READY, UNAVAILABLE;
        public static Status byOrdinal(int n) { return n >= 0 && n < values().length ? values()[n] : UNAVAILABLE; }
    }
    private int milliIF, phaseMB;
    /** 原作TilePhaseGenは10tickごとに同期し、モデルがタンクの量を表示できるようにする。 */
    private static final int SYNC_TICKS = 10;
    private int untilSync, sentMB = -1;
    private boolean readOnly, transferring, ticking;
    private CompoundTag preserved = new CompoundTag();
    private final ItemStackHandler items = new ItemStackHandler(3) {
        @Override public int getSlotLimit(int slot) { return slot == 2 ? 1 : 16; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 ? stack.is(PhaseContent.FILLED.get()) : slot == 2 && SolarGeneratorBlockEntity.chargeable(stack); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(this::tank);
    // 原作TilePhaseGenは単純なIInventory（sidedではない）: どの面のホッパーも3つのスロットすべてに届く。
    private LazyOptional<IItemHandler> all = LazyOptional.of(() -> handler(new int[]{0, 1, 2}, true));
    public PhaseGeneratorBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.PHASE_GEN_ENTITY.get(), pos, state); }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
    public boolean readOnly() { return readOnly; }
    public ItemStackHandler inventory() { return items; }
    public int milliIF() { return milliIF; }
    public int phaseMB() { return phaseMB; }
    /** RenderPhaseGenのテクスチャ: clamp(0, 4, round(4 * amount / size))。 */
    public static int modelLevel(int amountMb) { return Math.max(0, Math.min(4, (int) Math.round(4.0 * amountMb / TANK_MB))); }
    private boolean outputFits() {
        var out = items.getStackInSlot(1);
        return out.isEmpty() || ItemStack.isSameItemSameTags(out, new ItemStack(PhaseContent.EMPTY.get())) && out.getCount() < 16;
    }
    public Status status() {
        if (!mutable()) return Status.UNAVAILABLE;
        if (phaseMB > 0) return MAX_MILLI_IF - milliIF >= 500 ? Status.GENERATING : Status.BUFFER_FULL;
        if (!items.getStackInSlot(0).is(PhaseContent.FILLED.get())) return Status.NO_FUEL;
        return outputFits() ? Status.READY : Status.RETURN_BLOCKED;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, PhaseGeneratorBlockEntity tile) {
        if (!tile.mutable() || tile.ticking || tile.transferring) return;
        tile.ticking = true;
        try {
            // 原作の順: 発電、ユニットの取り込み、アイテムの充電。整数のmBなので、端数を切り上げて燃料を捨てる代わりに、0.5 IF未満の余地を使わずに残す。
            int burn = Math.min(tile.phaseMB, Math.min(BURN_MB, (MAX_MILLI_IF - tile.milliIF) / 500));
            tile.phaseMB -= burn; tile.milliIF += burn * 500;
            if (++tile.untilSync >= SYNC_TICKS) {
                tile.untilSync = 0;
                // 原作は関係なく10tickごとに送る。何も変わらない送信は飛ばす。
                if (tile.phaseMB != tile.sentMB) { tile.sentMB = tile.phaseMB; level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS); }
            }
            boolean imported = tile.importUnit(); int charged = tile.chargeItem();
            if (burn > 0 || imported || charged > 0) tile.setChanged();
        } finally { tile.ticking = false; }
    }
    private boolean importUnit() {
        var input = items.getStackInSlot(0);
        // 厳密な>は、ちょうど収まる境界を含め、原作の見える挙動。
        if (!input.is(PhaseContent.FILLED.get()) || !outputFits() || TANK_MB - phaseMB <= UNIT_MB) return false;
        var remaining = input.copy(); remaining.shrink(1);
        var output = items.getStackInSlot(1).copy();
        if (output.isEmpty()) output = new ItemStack(PhaseContent.EMPTY.get()); else output.grow(1);
        phaseMB += UNIT_MB; items.setStackInSlot(0, remaining); items.setStackInSlot(1, output); return true;
    }
    private int chargeItem() {
        // canReceive/getCapabilityも外部のコールバック: 調べる前にロックする。
        transferring = true;
        try {
            var stack = items.getStackInSlot(2);
            if (!SolarGeneratorBlockEntity.chargeable(stack)) return 0;
            var target = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            int offer = Math.min(OUTPUT_FE, milliIF / 250);
            if (target == null || offer <= 0) return 0;
            milliIF -= offer * 250;
            int accepted = Math.max(0, Math.min(offer, target.receiveEnergy(offer, false)));
            milliIF += (offer - accepted) * 250;
            return accepted;
        } finally { transferring = false; }
    }
    private IEnergyStorage storage() {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) { return 0; }
            public int extractEnergy(int amount, boolean simulate) {
                if (!mutable() || transferring) return 0;
                int n = Math.min(milliIF / 250, Math.min(OUTPUT_FE, Math.max(0, amount)));
                if (!simulate && n > 0) { milliIF -= n * 250; setChanged(); } return n;
            }
            public int getEnergyStored() { return mutable() ? milliIF / 250 : 0; }
            public int getMaxEnergyStored() { return MAX_MILLI_IF / 250; }
            public boolean canReceive() { return false; }
            public boolean canExtract() { return mutable() && !transferring; }
        };
    }
    private static boolean phaseFluid(FluidStack resource) {
        // タグ付きの液体の保存形式は無い。他のmodの液体のメタデータを黙って捨てない。
        return !resource.isEmpty() && resource.getFluid() == PhaseContent.SOURCE.get() && (!resource.hasTag() || resource.getTag().isEmpty());
    }
    private IFluidHandler tank() {
        return new IFluidHandler() {
            private void index(int n) { if (n != 0) throw new IndexOutOfBoundsException(n); }
            public int getTanks() { return 1; }
            public FluidStack getFluidInTank(int n) { index(n); return mutable() && phaseMB > 0 ? new FluidStack(PhaseContent.SOURCE.get(), phaseMB) : FluidStack.EMPTY; }
            public int getTankCapacity(int n) { index(n); return TANK_MB; }
            public boolean isFluidValid(int n, FluidStack resource) { index(n); return mutable() && phaseFluid(resource); }
            public int fill(FluidStack resource, FluidAction action) {
                if (!mutable() || transferring || !phaseFluid(resource)) return 0;
                int n = Math.min(TANK_MB - phaseMB, Math.max(0, resource.getAmount()));
                if (action.execute() && n > 0) { phaseMB += n; setChanged(); } return n;
            }
            public FluidStack drain(FluidStack resource, FluidAction action) { return phaseFluid(resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY; }
            public FluidStack drain(int maxDrain, FluidAction action) {
                if (!mutable() || transferring) return FluidStack.EMPTY;
                int n = Math.min(phaseMB, Math.max(0, maxDrain)); if (n == 0) return FluidStack.EMPTY;
                if (action.execute()) { phaseMB -= n; setChanged(); } return new FluidStack(PhaseContent.SOURCE.get(), n);
            }
        };
    }
    private IItemHandler handler(int[] slots, boolean extract) {
        return new IItemHandler() {
            private int slot(int index) { if (index < 0 || index >= slots.length) throw new IndexOutOfBoundsException(index); return slots[index]; }
            public int getSlots() { return slots.length; }
            public ItemStack getStackInSlot(int index) { int s = slot(index); return mutable() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY; }
            public ItemStack insertItem(int index, ItemStack stack, boolean simulate) { int s = slot(index); return mutable() && !transferring ? items.insertItem(s, stack, simulate) : stack; }
            public ItemStack extractItem(int index, int amount, boolean simulate) { int s = slot(index); return mutable() && !transferring && extract && amount > 0 ? items.extractItem(s, amount, simulate) : ItemStack.EMPTY; }
            public int getSlotLimit(int index) { return items.getSlotLimit(slot(index)); }
            public boolean isItemValid(int index, ItemStack stack) { int s = slot(index); return mutable() && items.isItemValid(s, stack); }
        };
    }
    public ContainerData menuData() {
        return new ContainerData() {
            public int get(int index) { return switch (index) { case 0 -> milliIF & 65535; case 1 -> milliIF >>> 16; case 2 -> phaseMB; case 3 -> status().ordinal(); default -> 0; }; }
            public void set(int index, int value) { }
            public int getCount() { return 4; }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved()) {
            if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
            if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidCap.cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER) return all.cast();
        } return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); fluidCap.invalidate(); all.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps(); energyCap = LazyOptional.of(this::storage); fluidCap = LazyOptional.of(this::tank);
        all = LazyOptional.of(() -> handler(new int[]{0, 1, 2}, true));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_phase_gen_schema") > 1;
        if (readOnly) return;
        milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if"))); phaseMB = Math.max(0, Math.min(TANK_MB, tag.getInt("phase_mb")));
        var inventoryTag = tag.getCompound("inventory").copy(); inventoryTag.putInt("Size", 3); items.deserializeNBT(inventoryTag);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_phase_gen_schema", 1); tag.putInt("milli_if", milliIF); tag.putInt("phase_mb", phaseMB); tag.put("inventory", items.serializeNBT());
    }
    @Override public CompoundTag getUpdateTag() { var tag = new CompoundTag(); tag.putInt("milli_if", milliIF); tag.putInt("phase_mb", phaseMB); return tag; }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    /** 更新タグが運ぶものだけ。保存データはサーバーに留める。 */
    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    @Override public void handleUpdateTag(CompoundTag tag) { milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if"))); phaseMB = Math.max(0, Math.min(TANK_MB, tag.getInt("phase_mb"))); }
}
