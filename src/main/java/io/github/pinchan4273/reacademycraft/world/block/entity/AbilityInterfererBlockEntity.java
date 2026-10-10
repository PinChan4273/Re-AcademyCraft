package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 原作TileAbilityInterferer（WeAthFolD）: オンの間、範囲内のサバイバルの全員は、ホワイトリストの名前を除き能力を一切使えない。
 * 設置した者はそのリストに入る。10tickごとに範囲の2乗を払い、払えなくなった瞬間に自分でオフになる。範囲は原作の10〜100。
 */
public final class AbilityInterfererBlockEntity extends BlockEntity {
    public static final double MIN_RANGE = 10, MAX_RANGE = 100;
    /** 原作自身の10000 IFのバッファ（移植版が数える単位のmilli-IF）と、10tickの拍。 */
    public static final int MAX_MILLI_IF = 10000000, INPUT_FE = 200, INTERVAL = 10;
    public static final int SLOT_BATTERY = 0;
    private int milliIF, beat;
    private boolean enabled, readOnly, transferring;
    private double range = MIN_RANGE;
    /** 原作はSortedSetを持つので、名前は順に並ぶ。 */
    private final Set<String> whitelist = new java.util.TreeSet<>();
    @Nullable private String placer;
    private CompoundTag preserved = new CompoundTag();
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return MetalFormerBlockEntity.battery(stack); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(this::automation);

    public AbilityInterfererBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.INTERFERER_ENTITY.get(), pos, state); }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
    public boolean readOnly() { return readOnly; }
    public ItemStackHandler inventory() { return items; }
    public int milliIF() { return milliIF; }
    public boolean enabled() { return enabled; }
    public double range() { return range; }
    /** 整列した写し: Set.copyOfでは原作の順を保てない。 */
    public Set<String> whitelist() { return java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(whitelist)); }
    @Nullable public String placer() { return placer; }
    /** 原作: 1tickの妨害は範囲の2乗のコストで、10tickごとに課す。 */
    public int costMilliIF() { return (int) Math.min(Integer.MAX_VALUE, Math.round(range * range * 1000)); }
    public String sourceName() {
        return "interferer@" + (level == null ? "?" : level.dimension().location()) + " " + worldPosition.toShortString();
    }
    public AABB area() {
        return new AABB(worldPosition.getX() + .5 - range, worldPosition.getY() + .5 - range, worldPosition.getZ() + .5 - range,
                worldPosition.getX() + .5 + range, worldPosition.getY() + .5 + range, worldPosition.getZ() + .5 + range);
    }
    /** 原作setPlacer: 最初に設置したプレイヤーを覚え、ホワイトリストに入れる。 */
    public void setPlacer(Player player) {
        if (placer != null || player == null) return;
        placer = player.getGameProfile().getName(); whitelist.add(placer); setChanged();
    }
    public boolean setRange(double value) {
        if (!mutable() || !Double.isFinite(value)) return false;
        range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, value)); setChanged(); return true;
    }
    public boolean setEnabled(boolean value) {
        if (!mutable()) return false;
        enabled = value; setChanged(); return true;
    }
    public static final int MAX_NAMES = 64, MAX_NAME = 64;
    /**
     * 原作hSetWhitelist: リストをパネルが送ったもので置き換える。原作はどんなリストも受け取り、設置者を戻さないので、原作が許すとおり
     * 設置者をリストから外せる。空の名前や、これらの上限を超えるリストや名前は拒否する。
     */
    public boolean setWhitelist(java.util.Collection<String> names) {
        if (!mutable() || names == null || names.size() > MAX_NAMES) return false;
        for (var name : names) if (name == null || name.isBlank() || name.length() > MAX_NAME) return false;
        whitelist.clear(); whitelist.addAll(names);
        setChanged(); return true;
    }
    /**
     * この機械が今そのプレイヤーを妨害するか。
     *
     * 原作は箱の中のすべてのサバイバルプレイヤーを選び（EntitySelectors.survivalPlayer）、独自のIInterfSourceは箱・機械の有効性・
     * クリエイティブ・スイッチだけを確かめ直す。ホワイトリストは保存・同期・編集されるが決して参照されないので、誰も除外しない。
     * これを参照すると、設置者がリストに入るため設置者は常に影響を受けず、機械が何もしていないように見えてしまう。
     * リストは引き続き保ち、原作と同じく設置者を含める。
     */
    public boolean covers(Player player) {
        return enabled && mutable() && player != null && player.isAlive() && !player.isSpectator()
                && !player.getAbilities().instabuild && player.level() == level
                && area().contains(player.getX(), player.getY(), player.getZ());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AbilityInterfererBlockEntity tile) {
        // 原作getActualStateはオンのテクスチャのためにenabled()を読む。ここでは状態がそれを持つ。
        if (state.hasProperty(io.github.pinchan4273.reacademycraft.world.block.AbilityInterfererBlock.ON)
                && state.getValue(io.github.pinchan4273.reacademycraft.world.block.AbilityInterfererBlock.ON) != tile.enabled && !tile.readOnly)
            level.setBlock(pos, state.setValue(io.github.pinchan4273.reacademycraft.world.block.AbilityInterfererBlock.ON, tile.enabled), 3);
        if (!tile.mutable()) return;
        // 原作update()は電池から引く前にスケジューラーを動かす。
        tile.sweep(level);
        tile.chargeFromBattery();
    }
    private void sweep(Level level) {
        // 原作scheduler.every(10).condition(enabled): LambdaLib2のTickSchedulerは条件が成り立つ間だけ数えるので、オフの間は拍が止まり、
        // オンに戻すとそこから続く。
        if (!enabled) return;
        if (++beat < INTERVAL) return;
        beat = 0;
        // 原作cost(): energy -= range*range。何かが残っているときだけ有効とみなすので、ちょうどコストと同じバッファでもオフになる。
        int cost = costMilliIF();
        if (milliIF <= cost) { milliIF = 0; enabled = false; setChanged(); return; }
        milliIF -= cost; setChanged();
        for (var player : level.players()) if (player instanceof ServerPlayer target) interfere(target);
    }
    /**
     * その走査の1人のプレイヤー: それが加える発生源は毎tickこの機械に問い直すので、範囲から出る、オフにする、壊すのいずれでも
     * 妨害は自然に終わる。
     */
    public boolean interfere(ServerPlayer target) {
        if (!covers(target)) return false;
        target.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(data -> {
            if (!data.hasInterference(sourceName())) data.addInterference(sourceName(), () -> covers(target));
        });
        return true;
    }
    private void chargeFromBattery() {
        var stack = items.getStackInSlot(SLOT_BATTERY); if (!MetalFormerBlockEntity.battery(stack)) return;
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
    public ContainerData menuData() {
        return new ContainerData() {
            public int get(int index) {
                return switch (index) {
                    case 0 -> milliIF & 65535; case 1 -> milliIF >>> 16;
                    case 2 -> (int) Math.round(range); case 3 -> enabled ? 1 : 0; default -> 0;
                };
            }
            public void set(int index, int value) { }
            public int getCount() { return 4; }
        };
    }
    /**
     * 原作TileAbilityInterfererのISidedInventory: どの面からも電池を入れられ（canInsertItemはisItemValidForSlot）、どの面からも取り出せない
     * （canExtractItemはfalse）。
     */
    private IItemHandler automation() {
        return new IItemHandler() {
            public int getSlots() { return items.getSlots(); }
            public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return items.insertItem(slot, stack, simulate); }
            public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            public boolean isItemValid(int slot, ItemStack stack) { return items.isItemValid(slot, stack); }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved()) {
            if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        } return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); itemCap.invalidate(); }
    @Override public void reviveCaps() {
        super.reviveCaps(); energyCap = LazyOptional.of(this::storage); itemCap = LazyOptional.of(this::automation);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_interferer_schema") > 1;
        if (readOnly) return;
        milliIF = Math.max(0, Math.min(MAX_MILLI_IF, tag.getInt("milli_if")));
        range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, tag.getDouble("range")));
        enabled = tag.getBoolean("enabled");
        placer = tag.contains("placer") ? tag.getString("placer") : null;
        whitelist.clear();
        for (Tag name : tag.getList("whitelist", Tag.TAG_STRING)) whitelist.add(name.getAsString());
        var inventoryTag = tag.getCompound("inventory").copy(); inventoryTag.putInt("Size", 1); items.deserializeNBT(inventoryTag);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_interferer_schema", 1); tag.putInt("milli_if", milliIF);
        tag.putDouble("range", range); tag.putBoolean("enabled", enabled);
        if (placer != null) tag.putString("placer", placer);
        var names = new ListTag();
        for (var name : whitelist) names.add(StringTag.valueOf(name));
        tag.put("whitelist", names);
        tag.put("inventory", items.serializeNBT());
    }
}
