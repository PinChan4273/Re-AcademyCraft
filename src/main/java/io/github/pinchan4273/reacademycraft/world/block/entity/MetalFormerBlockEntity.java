package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.crafting.*;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 金属成形機の状態。原作TileMetalFormer（WeAthFolD）の作業・電力・モード。
 * 処理はサーバーが持つ。整数のmilli-IFで、13.3 IFの作業と4分の1 IF単位のFEを保つ。
 * 無線の電力はエネルギーのcapabilityを通して届く。見た目と稼働音はMetalFormerBlockのもの。
 */
public final class MetalFormerBlockEntity extends BlockEntity {
    /** 原作TileMetalFormer.Mode。順序はNBTの"mode"とメニューのデータに序数で入るので変えない。 */
    public enum Mode {
        PLATE, INCISE, ETCH, REFINE;
        /** 保存された序数から読む。知らない値（壊れた保存）はPLATEとして扱う。 */
        public static Mode fromSaved(int ordinal) {
            Mode[] modes = values();
            return ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : PLATE;
        }
    }
    public enum Status {
        EMPTY, WRONG_MODE, TOO_FEW, OUTPUT_BLOCKED, NO_POWER, WORKING, READY, UNAVAILABLE;
        public static Status byOrdinal(int n) { return n >= 0 && n < values().length ? values()[n] : UNAVAILABLE; }
    }
    public static final int MAX_MILLI_IF = 3000000, WORK_MILLI_IF = 13300, WORK_TICKS = 60, INPUT_FE = 200;
    private int milliIF, workTicks, searchTicks;
    private Mode mode = Mode.PLATE;
    private MetalFormerRecipe current;
    private CompoundTag preserved = new CompoundTag();
    private boolean readOnly, transferring;
    private final ItemStackHandler items = new ItemStackHandler(3) {
        @Override public int getSlotLimit(int slot) { return slot == 2 ? 1 : 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 ? inputValid(level, stack) : slot == 2 && battery(stack); }
        @Override protected void onContentsChanged(int slot) { if (slot == 0) abort(); setChanged(); }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);
    private LazyOptional<IItemHandler> all = LazyOptional.of(() -> handler(new int[]{0, 1, 2}, true));
    private LazyOptional<IItemHandler> top = LazyOptional.of(() -> handler(new int[]{0}, false));
    private LazyOptional<IItemHandler> bottom = LazyOptional.of(() -> handler(new int[]{1, 2}, true));
    private LazyOptional<IItemHandler> sides = LazyOptional.of(() -> handler(new int[]{2}, false));
    public MetalFormerBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.METAL_ENTITY.get(), pos, state); }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
    public boolean readOnly() { return readOnly; }
    public ItemStackHandler inventory() { return items; }
    public int milliIF() { return milliIF; }
    public int progress() { return workTicks; }
    public Mode mode() { return mode; }
    /** サーバーの読み取り専用の説明。作業を進めたりレシピの選択を変えたりしない。 */
    public Status status() {
        if (!mutable()) return Status.UNAVAILABLE;
        var input = items.getStackInSlot(0);
        if (input.isEmpty()) return Status.EMPTY;
        var recipe = MetalFormerRecipe.find(level, input, mode).orElse(null);
        if (recipe == null) {
            boolean matchesMode = MetalFormerRecipe.all(level).stream().anyMatch(r -> r.operation() == mode && r.usesAsIngredient(input));
            return matchesMode ? Status.TOO_FEW : Status.WRONG_MODE;
        }
        if (!outputFits(recipe)) return Status.OUTPUT_BLOCKED;
        if (milliIF < WORK_MILLI_IF) return Status.NO_POWER;
        return workTicks > 0 ? Status.WORKING : Status.READY;
    }
    public static boolean inputValid(@Nullable Level level, ItemStack stack) {
        return level != null && !stack.isEmpty() && MetalFormerRecipe.all(level).stream().anyMatch(r -> r.usesAsIngredient(stack));
    }
    public static boolean battery(ItemStack stack) { return stack.getCount() == 1 && stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canExtract).orElse(false); }
    private void abort() { current = null; workTicks = 0; searchTicks = 0; }
    public boolean cycleMode(int delta) {
        if (!mutable() || (delta != -1 && delta != 1)) return false;
        mode = Mode.values()[Math.floorMod(mode.ordinal() + delta, Mode.values().length)]; abort(); setChanged(); return true;
    }
    private boolean canWork() {
        if (current == null || level.getRecipeManager().byKey(current.getId()).orElse(null) != current
                || !current.accepts(items.getStackInSlot(0), mode)) return false;
        return outputFits(current);
    }
    private boolean outputFits(MetalFormerRecipe recipe) {
        var out = items.getStackInSlot(1); var result = recipe.result();
        return out.isEmpty() || (ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MetalFormerBlockEntity tile) {
        if (!tile.mutable()) return;
        if (tile.current != null) {
            if (!tile.canWork()) tile.abort();
            else {
                int paid = Math.min(WORK_MILLI_IF, tile.milliIF); tile.milliIF -= paid;
                if (paid < WORK_MILLI_IF) tile.abort(); // 原作の不足時は使える分の割合を消費する。入力は残る。
                else if (++tile.workTicks == WORK_TICKS) {
                    var input = tile.items.getStackInSlot(0).copy(); input.shrink(tile.current.amount());
                    var result = tile.current.result(); var old = tile.items.getStackInSlot(1);
                    if (!old.isEmpty()) { result = old.copy(); result.grow(tile.current.result().getCount()); }
                    tile.items.setStackInSlot(0, input); tile.items.setStackInSlot(1, result); tile.abort();
                }
                tile.setChanged();
            }
        } else if (++tile.searchTicks >= 5) {
            tile.searchTicks = 0;
            tile.current = MetalFormerRecipe.find(level, tile.items.getStackInSlot(0), tile.mode).orElse(null);
        }
        tile.chargeFromBattery(); // 原作の順: 処理が電池の入力より先。
        // 原作isWorkInProgress: レシピを持っている。クライアントの稼働音はこれに従う。
        boolean working = tile.current != null;
        if (state.hasProperty(io.github.pinchan4273.reacademycraft.world.block.MetalFormerBlock.WORKING)
                && state.getValue(io.github.pinchan4273.reacademycraft.world.block.MetalFormerBlock.WORKING) != working)
            level.setBlock(pos, state.setValue(io.github.pinchan4273.reacademycraft.world.block.MetalFormerBlock.WORKING, working), 3);
    }
    private void chargeFromBattery() {
        var stack = items.getStackInSlot(2); if (!battery(stack)) return;
        var source = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        int request = Math.min(INPUT_FE, (MAX_MILLI_IF - milliIF) / 250);
        if (source == null || request <= 0) return;
        transferring = true;
        int received;
        try { received = Math.max(0, Math.min(request, source.extractEnergy(request, false))); }
        finally { transferring = false; }
        if (received > 0) { milliIF += received * 250; setChanged(); }
    }
    private IEnergyStorage storage() {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) {
                if (!mutable() || transferring) return 0;
                int n = Math.min((MAX_MILLI_IF - milliIF) / 250, Math.min(INPUT_FE, Math.max(0, amount)));
                if (!simulate && n > 0) { milliIF += n * 250; setChanged(); } return n;
            }
            public int extractEnergy(int amount, boolean simulate) { return 0; }
            public int getEnergyStored() { return mutable() ? milliIF / 250 : 0; }
            public int getMaxEnergyStored() { return MAX_MILLI_IF / 250; }
            public boolean canReceive() { return mutable() && !transferring; }
            public boolean canExtract() { return false; }
        };
    }
    private IItemHandler handler(int[] slots, boolean extract) {
        return new IItemHandler() {
            private int slot(int index) { if (index < 0 || index >= slots.length) throw new IndexOutOfBoundsException(index); return slots[index]; }
            public int getSlots() { return slots.length; }
            public ItemStack getStackInSlot(int index) { int s = slot(index); return mutable() ? items.getStackInSlot(s).copy() : ItemStack.EMPTY; }
            public ItemStack insertItem(int index, ItemStack stack, boolean simulate) { int s = slot(index); return mutable() ? items.insertItem(s, stack, simulate) : stack; }
            public ItemStack extractItem(int index, int amount, boolean simulate) { int s = slot(index); return mutable() && extract && amount > 0 ? items.extractItem(s, amount, simulate) : ItemStack.EMPTY; }
            public int getSlotLimit(int index) { return items.getSlotLimit(slot(index)); }
            public boolean isItemValid(int index, ItemStack stack) { int s = slot(index); return mutable() && items.isItemValid(s, stack); }
        };
    }
    public ContainerData menuData() {
        return new ContainerData() {
            public int get(int index) { return switch (index) { case 0 -> milliIF & 65535; case 1 -> milliIF >>> 16; case 2 -> mode.ordinal(); case 3 -> workTicks; case 4 -> status().ordinal(); default -> 0; }; }
            public void set(int index, int value) { }
            public int getCount() { return 5; }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved()) {
            if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER) return (side == null ? all : side == Direction.UP ? top : side == Direction.DOWN ? bottom : sides).cast();
        } return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); all.invalidate(); top.invalidate(); bottom.invalidate(); sides.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps(); energyCap = LazyOptional.of(this::storage);
        all = LazyOptional.of(() -> handler(new int[]{0, 1, 2}, true)); top = LazyOptional.of(() -> handler(new int[]{0}, false));
        bottom = LazyOptional.of(() -> handler(new int[]{1, 2}, true)); sides = LazyOptional.of(() -> handler(new int[]{2}, false));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_metal_schema") > 1; abort();
        if (readOnly) return;
        milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if"))); mode = Mode.fromSaved(tag.getInt("mode"));
        var inventoryTag = tag.getCompound("inventory").copy(); inventoryTag.putInt("Size", 3); items.deserializeNBT(inventoryTag);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_metal_schema", 1); tag.putInt("milli_if", milliIF); tag.putInt("mode", mode.ordinal()); tag.put("inventory", items.serializeNBT());
    }
    @Override public CompoundTag getUpdateTag() { var tag = new CompoundTag(); tag.putInt("mode", mode.ordinal()); return tag; }
    @Override public void handleUpdateTag(CompoundTag tag) { mode = Mode.fromSaved(tag.getInt("mode")); }
}
