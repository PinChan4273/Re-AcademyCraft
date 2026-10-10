package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.energy.WirelessNode;
import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** 3種類の原作TileNode: 機械が繋ぐ先で、ネットワークの電力を機械へ運ぶもの。バッファ・帯域・範囲・繋げる数は、その種類の原作自身の数値。 */
public final class WirelessNodeBlockEntity extends BlockEntity implements WirelessNode {
    /** 原作BlockNode.NodeType: 最大エネルギー、帯域、範囲、容量。 */
    public enum Kind {
        BASIC(15000, 150, 9, 5), STANDARD(50000, 300, 12, 10), ADVANCED(200000, 900, 19, 20);
        public final int maxEnergy, bandwidth, range, capacity;
        Kind(int maxEnergy, int bandwidth, int range, int capacity) {
            this.maxEnergy = maxEnergy; this.bandwidth = bandwidth; this.range = range; this.capacity = capacity;
        }
        public static Kind byOrdinal(int n) { return n >= 0 && n < values().length ? values()[n] : BASIC; }
    }
    private final Kind kind;
    /** 原作はnodeに独自の名前を与え、設置した者を覚える。 */
    private String name = "";
    /** 原作TileNodeのパスワード: 設置者がnodeのパネルで設定するまでは空。周波数送信機は、機械をnodeへ繋ぐ前にこれを求める。 */
    private String password = "";
    public static final int MAX_PASSWORD = 32;
    @Nullable private String placer;
    private int milliIF;
    /** 原作TileNode.updateTicker: 見た目を10tickごとに更新する。 */
    private int lookTicker;
    private boolean readOnly, transferring;
    private CompoundTag preserved = new CompoundTag();
    /** 原作TileNodeの2つのスロット: 電力を引くエネルギーアイテムと、充電するもの。 */
    public static final int SLOT_IN = 0, SLOT_OUT = 1;
    private final net.minecraftforge.items.ItemStackHandler items = new net.minecraftforge.items.ItemStackHandler(2) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) { return chargeable(stack); }
    };
    public net.minecraftforge.items.ItemStackHandler inventory() { return items; }
    /** 原作IFItemManager.isSupported: エネルギーを持つアイテム。 */
    public static boolean chargeable(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
    }
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);

    /** 種類はブロック自体から来る: 原作はブロックのmetadataから読んだ。 */
    public WirelessNodeBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyContent.NODE_ENTITY.get(), pos, state);
        kind = state.getBlock() instanceof io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock block ? block.kind() : Kind.BASIC;
    }
    public Kind kind() { return kind; }
    public String nodeName() { return name.isEmpty() ? kind.name().toLowerCase(java.util.Locale.ROOT) : name; }
    @Nullable public String placer() { return placer; }
    public void setPlacer(net.minecraft.world.entity.player.Player player) {
        if (placer == null && player != null) { placer = player.getGameProfile().getName(); setChanged(); }
    }
    public boolean rename(String value) {
        if (readOnly || value == null || value.length() > 32) return false;
        name = value; setChanged(); return true;
    }
    public boolean passwordIs(String value) { return password.equals(value); }
    /** 設置者のパネル専用。 */
    public String password() { return password; }
    public boolean setPassword(String value) {
        if (readOnly || value == null || value.length() > MAX_PASSWORD) return false;
        password = value; setChanged(); return true;
    }
    public boolean readOnly() { return readOnly; }
    public int milliIF() { return milliIF; }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
    @Override public int wirelessCapacity() { return readOnly ? 0 : kind.capacity; }
    @Override public double wirelessRange() { return readOnly ? 0 : kind.range; }
    @Override public int wirelessBandwidthMilliIF() { return readOnly ? 0 : kind.bandwidth * 1000; }
    @Override public int wirelessBufferMilliIF() { return milliIF; }
    public int maxMilliIF() { return kind.maxEnergy * 1000; }
    /** 原作BlockNodeのENERGY: min(4, round(4 * energy / maxEnergy))。 */
    public int energyLevel() { return (int) Math.min(4, Math.round(4.0 * milliIF / maxMilliIF())); }

    /**
     * 原作TileNode.update: 見た目と2つの電池スロット。ネットワークの均しとnode自身の接続は、原作のWiWorldDataがtileの後でtickするのと同じく、
     * すべてのブロックエンティティの後で動く（WirelessNetworks.tickを参照）。
     */
    public static void tick(Level level, BlockPos pos, BlockState state, WirelessNodeBlockEntity tile) {
        if (!tile.mutable() || !(level instanceof ServerLevel server)) return;
        // 原作TileNode.update: ネットワークの検索と見た目は10tickごと、電池は毎tick。
        if (++tile.lookTicker < 10) { tile.chargeIn(); tile.chargeOut(); return; }
        tile.lookTicker = 0;
        // 原作TileNode: ネットワーク上にある間はCONNECTED、ENERGYはバッファの5分の1単位で丸めたもの。
        boolean connected = WirelessNetworks.of(server).networkAt(pos) != null;
        int energy = tile.energyLevel();
        if (state.hasProperty(io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock.CONNECTED)
                && (state.getValue(io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock.CONNECTED) != connected
                || state.getValue(io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock.ENERGY) != energy))
            level.setBlock(pos, state.setValue(io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock.CONNECTED, connected)
                    .setValue(io.github.pinchan4273.reacademycraft.world.block.WirelessNodeBlock.ENERGY, energy), 3);
        tile.chargeIn(); tile.chargeOut();
    }
    /** nodeのエネルギーをネットワークの平均へ近づける、ネットワークの均しのため。 */
    public boolean balanced() { return mutable(); }
    public void setMilliIF(int value) {
        int next = Math.max(0, Math.min(maxMilliIF(), value));
        if (next != milliIF) { milliIF = next; setChanged(); }
    }
    /** 原作updateChargeIn: 最初のスロットのアイテムから、帯域と残りの空きまで。 */
    private void chargeIn() {
        var energy = items.getStackInSlot(SLOT_IN).getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (energy == null || !energy.canExtract()) return;
        int req = Math.min(kind.bandwidth * 1000, maxMilliIF() - milliIF) / 250;
        if (req <= 0) return;
        int got = energy.extractEnergy(req, false);
        if (got > 0) { milliIF += got * 250; setChanged(); }
    }
    /** 原作updateChargeOut: 2つ目のスロットのアイテムへ、帯域と持っている量まで。 */
    private void chargeOut() {
        var energy = items.getStackInSlot(SLOT_OUT).getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (energy == null || !energy.canReceive() || milliIF <= 0) return;
        int cur = Math.min(kind.bandwidth * 1000, milliIF) / 250;
        int accepted = energy.receiveEnergy(cur, false);
        if (accepted > 0) { milliIF -= accepted * 250; setChanged(); }
    }
    /**
     * 原作NodeConn.tick: 電力を与えられるものがnodeを満たし、次に受け取れるものへnodeから与える。どちらもシャッフルした順で、
     * そのtickのnodeの帯域内で行う。原作の接続はnode自身のものなので、nodeがネットワーク上にあるかどうかに関係なく動く。
     */
    public void connectionTick(ServerLevel level, WirelessNetworks.Connection connection, net.minecraft.util.RandomSource random) {
        if (!mutable() || connection.users().isEmpty()) return;
        var generators = new it.unimi.dsi.fastutil.objects.ObjectArrayList<IEnergyStorage>();
        var receivers = new it.unimi.dsi.fastutil.objects.ObjectArrayList<IEnergyStorage>();
        for (var userPos : connection.users()) {
            if (!level.hasChunkAt(userPos)) continue;
            var user = level.getBlockEntity(userPos);
            if (user == null) continue;
            var energy = user.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (energy == null) continue;
            if (energy.canExtract()) generators.add(energy);
            if (energy.canReceive()) receivers.add(energy);
        }
        transferring = true;
        try {
            net.minecraft.Util.shuffle(generators, random);
            int transferLeft = wirelessBandwidthMilliIF();
            for (var generator : generators) {
                if (transferLeft <= 0) break;
                int required = Math.min(transferLeft, maxMilliIF() - milliIF) / 250;
                if (required <= 0) break;
                int got = generator.extractEnergy(required, false);
                if (got > 0) { milliIF += got * 250; transferLeft -= got * 250; setChanged(); }
            }
            net.minecraft.Util.shuffle(receivers, random);
            transferLeft = wirelessBandwidthMilliIF();
            for (var receiver : receivers) {
                if (transferLeft <= 0) break;
                int give = Math.min(milliIF, transferLeft) / 250;
                if (give <= 0) break;
                int accepted = receiver.receiveEnergy(give, false);
                if (accepted > 0) { milliIF -= accepted * 250; transferLeft -= accepted * 250; setChanged(); }
            }
        } finally { transferring = false; }
    }

    private IEnergyStorage storage() {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) {
                if (!mutable() || transferring) return 0;
                int n = Math.min((maxMilliIF() - milliIF) / 250, Math.min(kind.bandwidth * 4, Math.max(0, amount)));
                if (!simulate && n > 0) { milliIF += n * 250; setChanged(); } return n;
            }
            public int extractEnergy(int amount, boolean simulate) {
                if (!mutable() || transferring) return 0;
                int n = Math.min(milliIF / 250, Math.min(kind.bandwidth * 4, Math.max(0, amount)));
                if (!simulate && n > 0) { milliIF -= n * 250; setChanged(); } return n;
            }
            public int getEnergyStored() { return mutable() ? milliIF / 250 : 0; }
            public int getMaxEnergyStored() { return maxMilliIF() / 250; }
            public boolean canReceive() { return mutable() && !transferring; }
            public boolean canExtract() { return mutable() && !transferring; }
        };
    }
    private LazyOptional<net.minecraftforge.items.IItemHandler> itemCap = LazyOptional.of(this::automation);
    /** 原作TileNodeは単純なIInventory: どの面のホッパーも、入出力とも両方のスロットに届く。 */
    private net.minecraftforge.items.IItemHandler automation() {
        return new net.minecraftforge.items.IItemHandler() {
            public int getSlots() { return items.getSlots(); }
            public net.minecraft.world.item.ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
            public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
                return mutable() && !transferring ? items.insertItem(slot, stack, simulate) : stack;
            }
            public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
                return mutable() && !transferring ? items.extractItem(slot, amount, simulate) : net.minecraft.world.item.ItemStack.EMPTY;
            }
            public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
            public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) { return mutable() && items.isItemValid(slot, stack); }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved()) {
            if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
            if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); itemCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); energyCap = LazyOptional.of(this::storage); itemCap = LazyOptional.of(this::automation); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_node_schema") > 1;
        if (readOnly) return;
        milliIF = Math.max(0, Math.min(maxMilliIF(), tag.getInt("milli_if")));
        name = tag.getString("node_name");
        password = tag.getString("password");
        placer = tag.contains("placer") ? tag.getString("placer") : null;
        if (tag.contains("items")) items.deserializeNBT(tag.getCompound("items"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_node_schema", 1); tag.putInt("milli_if", milliIF);
        tag.putString("node_name", name);
        tag.putString("password", password);
        if (placer != null) tag.putString("placer", placer);
        tag.put("items", items.serializeNBT());
    }
}
