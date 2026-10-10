package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.crafting.MachineRecipes;
import io.github.pinchan4273.reacademycraft.crafting.ImagFusionRecipe;
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
 * 原作TileImagFusor（WeAthFolD）: 虚像投影液と電力で、あるアイテムを別のものに変える。
 *
 * 待機中は10tickごとに入力にあるもののレシピを探し、その後1tickに作業の120分の1ずつ進め、各tickに12 IFを使い、最後にそのレシピの
 * 液体を減らし、入力から1つ取り、結果を出力へ入れる。入力が変わる、電力が足りない、液体が無い、出力にもう入らない、のいずれかで
 * すぐ作業を止める。毎tick、満たされた物質ユニットを1つタンクへ取り込み（1回に1000）、空のものを返し、自身のスロットの電池から電力を引く。
 */
public final class ImagFusorBlockEntity extends BlockEntity {
    public enum Status {
        EMPTY, NO_RECIPE, NO_LIQUID, OUTPUT_BLOCKED, NO_POWER, WORKING, READY, UNAVAILABLE;
        public static Status byOrdinal(int n) { return n >= 0 && n < values().length ? values()[n] : UNAVAILABLE; }
    }
    /** 原作: 2000 IFのバッファ、120tickの間1tickに12 IF、1回に1000ずつ満たす8000のタンク。 */
    public static final int MAX_MILLI_IF = 2000000, WORK_MILLI_IF = 12000, WORK_TICKS = 120;
    public static final int TANK_MB = 8000, UNIT_MB = 1000, INPUT_FE = 200, SEARCH_TICKS = 10;
    public static final int SLOT_INPUT = 0, SLOT_OUTPUT = 1, SLOT_IMAG_INPUT = 2, SLOT_ENERGY = 3, SLOT_IMAG_OUTPUT = 4;
    private int milliIF, phaseMB, workTicks, searchTicks;
    private ImagFusionRecipe current;
    /**
     * 原作writeToNBTは_work_recipeと_work_progressを保つので、作業はchunkの再読込や再起動を越えて残る。レシピはlevelを通してしか
     * 引けないので、読み込んだ作業は最初のtickで解決する。
     */
    @javax.annotation.Nullable private net.minecraft.resources.ResourceLocation loadedRecipe;
    private int loadedTicks;
    private CompoundTag preserved = new CompoundTag();
    private boolean readOnly, transferring;
    private final ItemStackHandler items = new ItemStackHandler(5) {
        @Override public int getSlotLimit(int slot) { return slot == SLOT_ENERGY ? 1 : 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_INPUT -> inputValid(level, stack);
                case SLOT_IMAG_INPUT -> stack.is(PhaseContent.FILLED.get());
                case SLOT_ENERGY -> MetalFormerBlockEntity.battery(stack);
                default -> false;
            };
        }
        @Override protected void onContentsChanged(int slot) { if (slot == SLOT_INPUT) abort(); setChanged(); }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);
    private LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(this::tank);
    private LazyOptional<IItemHandler> all = LazyOptional.of(() -> handler(new int[]{0, 1, 2, 3, 4}, true));
    private LazyOptional<IItemHandler> top = LazyOptional.of(() -> handler(new int[]{SLOT_INPUT, SLOT_IMAG_INPUT}, false));
    private LazyOptional<IItemHandler> bottom = LazyOptional.of(() -> handler(new int[]{SLOT_OUTPUT, SLOT_IMAG_OUTPUT, SLOT_ENERGY}, true));
    private LazyOptional<IItemHandler> sides = LazyOptional.of(() -> handler(new int[]{SLOT_ENERGY}, false));

    public ImagFusorBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.IMAG_FUSOR_ENTITY.get(), pos, state); }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
    public boolean readOnly() { return readOnly; }
    public ItemStackHandler inventory() { return items; }
    public int milliIF() { return milliIF; }
    public int phaseMB() { return phaseMB; }
    public int progress() { return workTicks; }

    public static boolean inputValid(@Nullable Level level, ItemStack stack) {
        return level != null && !stack.isEmpty() && level.getRecipeManager()
                .getAllRecipesFor(MachineRecipes.FUSION_TYPE.get()).stream().anyMatch(r -> r.accepts(stack));
    }
    /** 機械が何をするかの読み取り専用の説明。何も進めない。 */
    public Status status() {
        if (!mutable()) return Status.UNAVAILABLE;
        var input = items.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) return Status.EMPTY;
        var recipe = recipeFor(input);
        if (recipe == null) return Status.NO_RECIPE;
        if (!outputFits(recipe)) return Status.OUTPUT_BLOCKED;
        if (phaseMB < recipe.liquid()) return Status.NO_LIQUID;
        if (milliIF < WORK_MILLI_IF) return Status.NO_POWER;
        return workTicks > 0 ? Status.WORKING : Status.READY;
    }
    @Nullable private ImagFusionRecipe recipeFor(ItemStack input) {
        return level == null ? null : ImagFusionRecipe.find(level, input).orElse(null);
    }
    private boolean outputFits(ImagFusionRecipe recipe) {
        var out = items.getStackInSlot(SLOT_OUTPUT); var result = recipe.result();
        return out.isEmpty() || (ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }
    private void abort() { current = null; workTicks = 0; searchTicks = 0; loadedRecipe = null; }
    /** 原作updateWork自身の確認。進捗の各tickの前に行う。 */
    private boolean canWork() {
        if (current == null || level == null || level.getRecipeManager().byKey(current.getId()).orElse(null) != current) return false;
        var input = items.getStackInSlot(SLOT_INPUT);
        return current.accepts(input) && !input.isEmpty() && outputFits(current) && phaseMB >= current.liquid();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ImagFusorBlockEntity tile) {
        if (!tile.mutable()) return;
        if (tile.loadedRecipe != null) {
            if (level.getRecipeManager().byKey(tile.loadedRecipe).orElse(null) instanceof ImagFusionRecipe recipe) {
                tile.current = recipe; tile.workTicks = tile.loadedTicks;
            }
            tile.loadedRecipe = null;
        }
        if (tile.current != null) {
            if (!tile.canWork() || tile.milliIF < WORK_MILLI_IF) tile.abort();
            else {
                tile.milliIF -= WORK_MILLI_IF;
                if (++tile.workTicks >= WORK_TICKS) tile.finish();
                tile.setChanged();
            }
        } else if (++tile.searchTicks >= SEARCH_TICKS) {
            tile.searchTicks = 0;
            tile.current = tile.recipeFor(tile.items.getStackInSlot(SLOT_INPUT));
        }
        boolean imported = tile.importUnit();
        tile.chargeFromBattery();
        if (imported) tile.setChanged();
        // 原作isWorking(): レシピを持っている。前面と光はこれに従う。
        boolean working = tile.current != null;
        if (state.hasProperty(io.github.pinchan4273.reacademycraft.world.block.ImagFusorBlock.WORKING)
                && state.getValue(io.github.pinchan4273.reacademycraft.world.block.ImagFusorBlock.WORKING) != working)
            level.setBlock(pos, state.setValue(io.github.pinchan4273.reacademycraft.world.block.ImagFusorBlock.WORKING, working), 3);
    }
    /** 原作endWorking: 液体が減り、入力が1つ減り、結果が出る。 */
    private void finish() {
        phaseMB -= current.liquid();
        var input = items.getStackInSlot(SLOT_INPUT).copy(); input.shrink(1);
        var result = current.result(); var old = items.getStackInSlot(SLOT_OUTPUT);
        if (!old.isEmpty()) { result = old.copy(); result.grow(current.result().getCount()); }
        items.setStackInSlot(SLOT_INPUT, input); items.setStackInSlot(SLOT_OUTPUT, result);
        abort();
    }
    /**
     * 原作自身の液体の取り込みとその境界: getLiquidAmount() + PER_UNIT <= TANK_SIZEなので、タンクをちょうど満たす場合もユニットは入る。
     * 余裕を求める条件にすると、タンクが7000で頭打ちになり、8000のレシピ（純粋な結晶）を実行できなくなる。
     */
    private boolean importUnit() {
        var input = items.getStackInSlot(SLOT_IMAG_INPUT);
        if (!input.is(PhaseContent.FILLED.get()) || phaseMB + UNIT_MB > TANK_MB) return false;
        var output = items.getStackInSlot(SLOT_IMAG_OUTPUT);
        if (!output.isEmpty() && (!output.is(PhaseContent.EMPTY.get()) || output.getCount() >= output.getMaxStackSize())) return false;
        var remaining = input.copy(); remaining.shrink(1);
        var returned = output.copy();
        if (returned.isEmpty()) returned = new ItemStack(PhaseContent.EMPTY.get()); else returned.grow(1);
        phaseMB += UNIT_MB; items.setStackInSlot(SLOT_IMAG_INPUT, remaining); items.setStackInSlot(SLOT_IMAG_OUTPUT, returned);
        return true;
    }
    private void chargeFromBattery() {
        var stack = items.getStackInSlot(SLOT_ENERGY); if (!MetalFormerBlockEntity.battery(stack)) return;
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
    private static boolean phaseFluid(FluidStack resource) {
        return resource != null && !resource.isEmpty() && resource.getFluid() == PhaseContent.SOURCE.get();
    }
    /** 原作はパイプからも物質ユニットからと同じように液体を受け取り、何も返さない。 */
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
            public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
            public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
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
            public int get(int index) {
                return switch (index) {
                    case 0 -> milliIF & 65535; case 1 -> milliIF >>> 16;
                    case 2 -> phaseMB; case 3 -> workTicks; case 4 -> status().ordinal(); default -> 0;
                };
            }
            public void set(int index, int value) { }
            public int getCount() { return 5; }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved()) {
            if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
            if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidCap.cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER) return (side == null ? all : side == Direction.UP ? top : side == Direction.DOWN ? bottom : sides).cast();
        } return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); energyCap.invalidate(); fluidCap.invalidate();
        all.invalidate(); top.invalidate(); bottom.invalidate(); sides.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps(); energyCap = LazyOptional.of(this::storage); fluidCap = LazyOptional.of(this::tank);
        all = LazyOptional.of(() -> handler(new int[]{0, 1, 2, 3, 4}, true));
        top = LazyOptional.of(() -> handler(new int[]{SLOT_INPUT, SLOT_IMAG_INPUT}, false));
        bottom = LazyOptional.of(() -> handler(new int[]{SLOT_OUTPUT, SLOT_IMAG_OUTPUT, SLOT_ENERGY}, true));
        sides = LazyOptional.of(() -> handler(new int[]{SLOT_ENERGY}, false));
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_fusor_schema") > 1; abort();
        if (readOnly) return;
        milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if")));
        phaseMB = Math.max(0, Math.min(TANK_MB, tag.getInt("phase_mb")));
        var inventoryTag = tag.getCompound("inventory").copy(); inventoryTag.putInt("Size", 5); items.deserializeNBT(inventoryTag);
        loadedRecipe = tag.contains("work_recipe") ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString("work_recipe")) : null;
        loadedTicks = Math.max(0, Math.min(WORK_TICKS - 1, tag.getInt("work_ticks")));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_fusor_schema", 1); tag.putInt("milli_if", milliIF);
        tag.putInt("phase_mb", phaseMB); tag.put("inventory", items.serializeNBT());
        var job = current != null ? current.getId() : loadedRecipe;
        if (job != null) { tag.putString("work_recipe", job.toString()); tag.putInt("work_ticks", current != null ? workTicks : loadedTicks); }
        else { tag.remove("work_recipe"); tag.remove("work_ticks"); }
    }
}
