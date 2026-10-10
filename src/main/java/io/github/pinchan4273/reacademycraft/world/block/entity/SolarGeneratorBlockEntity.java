package io.github.pinchan4273.reacademycraft.world.block.entity;

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
 * 太陽光発電機の状態。原作TileSolarGen/TileGeneratorBase（WeAthFolD）の発電と蓄え。
 * 正確なmilli-IFの蓄えで、雨天の0.6 IFの発電と4分の1 IF単位のForgeの転送を保つ。
 */
public final class SolarGeneratorBlockEntity extends BlockEntity {
    public static final int MAX_MILLI_IF = 1000000, MILLI_IF_PER_FE = 250, OUTPUT_FE = 400;
    private int milliIF;
    private CompoundTag preserved = new CompoundTag();
    private boolean readOnly, transferring, ticking;
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return chargeable(stack); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return mutable() && !transferring ? super.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return mutable() && !transferring ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);
    public SolarGeneratorBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.SOLAR_ENTITY.get(), pos, state); }
    public boolean readOnly() { return readOnly; }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
    public ItemStackHandler inventory() { return items; }
    public int milliIF() { return milliIF; }
    public static boolean chargeable(ItemStack stack) {
        return stack.getCount() == 1 && stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false);
    }
    /** STOPPED 0 / WEAK 1 / STRONG 2。原作の、12500を含む昼の境界を使う。 */
    public static int status(long dayTime, boolean sky, boolean raining) {
        long time = dayTime % 24000;
        return sky && time >= 0 && time <= 12500 ? (raining ? 1 : 2) : 0;
    }
    public int status() {
        return level == null || readOnly ? 0 : status(level.getDayTime(), seesSky(level, worldPosition.above()), level.isRaining());
    }
    /**
     * 原作World.canSeeSkyはchunkの高さマップを読み、それはどのディメンションにもあるので、ネザーの天井の上やエンドでも空が見える
     * （オーバーワールドの共有の時刻で）。1.20のcanSeeSkyは空の光を読み、その2つには無いので、そこでは代わりに高さマップを読む。
     * 原作の不透明度のものに最も近い1.20の高さマップはMOTION_BLOCKING。
     */
    public static boolean seesSky(Level level, BlockPos above) {
        return level.dimensionType().hasSkyLight() ? level.canSeeSky(above)
                : above.getY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, above.getX(), above.getZ());
    }
    public static void tick(Level level, BlockPos pos, BlockState state, SolarGeneratorBlockEntity tile) {
        if (!tile.mutable() || tile.ticking || tile.transferring) return;
        tile.ticking = true;
        try {
            int generated = Math.min(MAX_MILLI_IF - tile.milliIF, switch (tile.status()) { case 1 -> 600; case 2 -> 3000; default -> 0; });
            tile.milliIF += generated;
            // 原作の発電機と同じく、蓄えたエネルギーは夜や屋根の下でも充電に使う。
            int sent = tile.chargeItem();
            if (generated > 0 || sent > 0) tile.setChanged();
        } finally { tile.ticking = false; }
    }

    private int chargeItem() {
        // canReceive/getCapabilityも外部のコールバック: 調べる前にロックする。
        transferring = true;
        try {
            var stack = items.getStackInSlot(0);
            if (!chargeable(stack)) return 0;
            var target = stack.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            int offer = Math.min(OUTPUT_FE, milliIF / MILLI_IF_PER_FE);
            if (target == null || offer <= 0) return 0;
            milliIF -= offer * MILLI_IF_PER_FE;
            int accepted = Math.max(0, Math.min(offer, target.receiveEnergy(offer, false)));
            milliIF += (offer - accepted) * MILLI_IF_PER_FE;
            return accepted;
        } finally { transferring = false; }
    }
    private IEnergyStorage storage() {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) { return 0; }
            public int extractEnergy(int amount, boolean simulate) {
                if (!mutable() || transferring) return 0;
                int n = Math.min(milliIF / MILLI_IF_PER_FE, Math.min(OUTPUT_FE, Math.max(0, amount)));
                if (!simulate && n > 0) { milliIF -= n * MILLI_IF_PER_FE; setChanged(); } return n;
            }
            public int getEnergyStored() { return mutable() ? milliIF / MILLI_IF_PER_FE : 0; }
            public int getMaxEnergyStored() { return MAX_MILLI_IF / MILLI_IF_PER_FE; }
            public boolean canReceive() { return false; }
            public boolean canExtract() { return mutable() && !transferring; }
        };
    }
    public ContainerData menuData() {
        return new ContainerData() {
            public int get(int index) { return index == 0 ? milliIF & 65535 : index == 1 ? milliIF >>> 16 : status(); }
            public void set(int index, int value) { /* クライアントの変更はサーバーの発電機へ書かない */ }
            public int getCount() { return 3; }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved()) {
            if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        } return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCap.invalidate(); energyCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCap = LazyOptional.of(() -> items); energyCap = LazyOptional.of(this::storage); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_solar_schema") > 1;
        if (readOnly) return;
        milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if")));
        var inventoryTag = tag.getCompound("inventory").copy(); inventoryTag.putInt("Size", 1);
        items.deserializeNBT(inventoryTag);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved);
        if (readOnly) return;
        tag.putInt("academy_solar_schema", 1); tag.putInt("milli_if", milliIF); tag.put("inventory", items.serializeNBT());
    }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag(); tag.putInt("milli_if", milliIF); return tag; // アイテムの非公開のNBTを観測者へ送らない。
    }
    @Override public void handleUpdateTag(CompoundTag tag) { milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if"))); }
}
