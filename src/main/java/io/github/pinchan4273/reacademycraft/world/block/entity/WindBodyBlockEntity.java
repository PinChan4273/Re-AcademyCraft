package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.WindGeneratorRules;
import io.github.pinchan4273.reacademycraft.world.WindStructureProbe;
import io.github.pinchan4273.reacademycraft.world.WindVisualState;
import io.github.pinchan4273.reacademycraft.world.block.WindBodyBlock;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * 本体ごとの所有権と、原作の1スロットの回転翼のインベントリ。エネルギーは土台に属する。
 * 原作TileWindGenMain/TileInventory: WeAthFolD。
 */
public final class WindBodyBlockEntity extends BlockEntity {
    private @Nullable UUID structureId;
    private @Nullable BlockPos origin;
    private @Nullable CompoundTag future;
    private @Nullable Tag frozenFan;
    private @Nullable Tag preservedFanTag;
    private CompoundTag preservedFan = new CompoundTag();
    private ItemStack fan = ItemStack.EMPTY;
    private @Nullable CompoundTag savedFan;
    private boolean removing;
    private boolean fanTransfer;
    private boolean energyTicking;
    private WindVisualState visualState = WindVisualState.NONE;
    private long visualTick = Long.MIN_VALUE;
    private final WindBaseEnergy energy = new WindBaseEnergy();
    private final WindChargeInventory charge = new WindChargeInventory(this::setChanged);
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(this::energyStorage);
    private LazyOptional<IItemHandler> fanCapability = LazyOptional.of(this::fanInventory);
    private LazyOptional<IItemHandler> chargeCapability = LazyOptional.of(this::chargeInventory);

    public WindBodyBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyContent.WIND_BODY_ENTITY.get(), pos, state);
    }
    public void initialize(BlockPos root, UUID id) {
        // 初期化してよいのは新しく設置したエンティティだけ。より新しいセーブを上書きしない。
        if (isFuture() || charge.busy() || fanTransfer) return;
        origin = root.immutable(); structureId = id; removing = false; setChanged();
    }
    public boolean removing() { return removing; }
    public boolean isFuture() { return future != null || frozenFan != null || energy.readOnly() || charge.readOnly(); }
    public void beginRemoval() { removing = true; }
    public boolean sameStructure(WindBodyBlockEntity root) {
        if (!(getBlockState().getBlock() instanceof WindBodyBlock own)
                || !(root.getBlockState().getBlock() instanceof WindBodyBlock main)) return false;
        int part = getBlockState().getValue(WindBodyBlock.PART);
        return !isFuture() && !root.isFuture() && own.body() == main.body()
                && origin != null && origin.equals(root.worldPosition)
                && structureId != null && structureId.equals(root.structureId)
                && root.getBlockState().getValue(WindBodyBlock.PART) == 0
                && getBlockState().getValue(WindBodyBlock.FACING)
                        == root.getBlockState().getValue(WindBodyBlock.FACING)
                && part < main.cells(origin, root.getBlockState().getValue(WindBodyBlock.FACING)).size()
                && main.cells(origin, root.getBlockState().getValue(WindBodyBlock.FACING)).get(part).equals(worldPosition);
    }
    public @Nullable WindBodyBlockEntity root() {
        if (level == null || origin == null || structureId == null || isFuture()
                || origin.distSqr(worldPosition) > 4 || !level.hasChunkAt(origin)) return null;
        return level.getBlockEntity(origin) instanceof WindBodyBlockEntity root && sameStructure(root) ? root : null;
    }
    public boolean complete() {
        if (root() != this || removing || isRemoved() || level == null
                || !(getBlockState().getBlock() instanceof WindBodyBlock body)) return false;
        for (BlockPos pos : body.cells(worldPosition, getBlockState().getValue(WindBodyBlock.FACING)))
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof WindBodyBlockEntity part)
                    || part.removing || !part.sameStructure(this)) return false;
        return true;
    }
    public WindStructureProbe.Cell probeCell() {
        if (isRemoved() || removing || isFuture() || structureId == null || origin == null
                || !(getBlockState().getBlock() instanceof WindBodyBlock body))
            return WindStructureProbe.Cell.simple(WindStructureProbe.Kind.OTHER);
        return new WindStructureProbe.Cell(
                body.body() == WindBodyBlock.Body.BASE ? WindStructureProbe.Kind.BASE : WindStructureProbe.Kind.MAIN,
                origin, structureId, getBlockState().getValue(WindBodyBlock.FACING),
                getBlockState().getValue(WindBodyBlock.PART), hasFanInstalled());
    }

    private boolean mainBody() {
        return getBlockState().getBlock() instanceof WindBodyBlock body && body.body() == WindBodyBlock.Body.MAIN;
    }
    private boolean mainRoot() { return mainBody() && getBlockState().getValue(WindBodyBlock.PART) == 0; }
    private boolean baseBody() {
        return getBlockState().getBlock() instanceof WindBodyBlock body && body.body() == WindBodyBlock.Body.BASE;
    }
    private boolean baseRoot() { return baseBody() && getBlockState().getValue(WindBodyBlock.PART) == 0; }
    public int energyUnits() { return baseRoot() && !isFuture() ? energy.units() : 0; }
    /** クライアントの描画はこの観測だけを読み、アイテムやエネルギーのハンドラーは読まない。 */
    public WindVisualState visualState() { return visualState; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return mainRoot() ? new net.minecraft.world.phys.AABB(worldPosition).inflate(8)
                : new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 1, 0).inflate(.1);
    }
    private WindVisualState observeVisual() {
        if (level == null || level.isClientSide || isRemoved() || removing || isFuture()
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this
                || root() != this || !complete()) return WindVisualState.NONE;
        var view = WindStructureProbe.loadedWorld(level, p -> WindStructureProbe.decodeLoaded(level, p));
        return mainRoot() ? WindVisualState.fromMain(view, worldPosition) : WindVisualState.fromBase(view, worldPosition);
    }
    private void updateVisual() {
        long now = level.getGameTime();
        if (now == visualTick || now % 10 != 0) return;
        visualTick = now; var observed = observeVisual();
        // 新しいchunkの観測者は、標本の間に隣が利用できないと一時的なNONEを受け取ることがある。サーバーのキャッシュした状態が
        // その間変わらなくても、定期的な公開部分だけの再同期がその表示を直す。原作の20tickごとの観測の送信の間隔を保つ。
        if (!observed.equals(visualState) || now % 20 == 0) {
            visualState = observed;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
    @Override public CompoundTag getUpdateTag() {
        return (level != null && level.isClientSide ? visualState : observeVisual()).encode();
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        if (level == null || level.isClientSide) visualState = WindVisualState.decode(tag);
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        var tag = packet.getTag(); handleUpdateTag(tag == null ? new CompoundTag() : tag);
    }
    private @Nullable WindBodyBlockEntity accessibleEnergyRoot() {
        if (!baseBody() || isFuture() || removing || isRemoved() || level == null || level.isClientSide
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this) return null;
        WindBodyBlockEntity root = root();
        return root != null && root.baseRoot() && !root.energyTicking && !root.charge.busy() && root.complete() ? root : null;
    }
    private @Nullable WindChargeInventory accessibleChargeRoot() {
        if (!baseBody() || isFuture() || removing || isRemoved() || level == null || level.isClientSide
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this) return null;
        WindBodyBlockEntity root = root();
        return root != null && root.baseRoot() && !root.energyTicking && root.complete() ? root.charge : null;
    }
    private boolean liveChargeOwner() {
        return baseRoot() && !removing && !isRemoved() && !isFuture() && level != null && !level.isClientSide
                && level.hasChunkAt(worldPosition) && level.getBlockEntity(worldPosition) == this && root() == this && complete();
    }
    public IItemHandlerModifiable chargeInventory() { return WindChargeInventory.view(this::accessibleChargeRoot); }
    private void dropDeferredCharge(ItemStack dropped) {
        if (level != null && !level.isClientSide && !dropped.isEmpty())
            Containers.dropItemStack(level, worldPosition.getX() + .5,
                    worldPosition.getY() + .5, worldPosition.getZ() + .5, dropped);
    }
    public void dropChargeOnRemoval() {
        if (!baseRoot() || !removing || isFuture() || level == null || level.isClientSide) return;
        ItemStack dropped = charge.requestDrop();
        if (!dropped.isEmpty()) dropDeferredCharge(dropped);
    }
    public static void tick(Level level, BlockPos pos, BlockState state, WindBodyBlockEntity tile) {
        if (level != tile.level || !pos.equals(tile.worldPosition) || !state.equals(tile.getBlockState())
                || level.isClientSide || tile.isRemoved() || level.getBlockEntity(pos) != tile) return;
        if (tile.baseRoot() || tile.mainRoot()) tile.updateVisual();
        if (!tile.baseRoot() || tile.accessibleEnergyRoot() != tile) return;
        tile.energyTicking = true;
        try {
            if (!tile.energy.beginTick(level.getGameTime())) return;
            var view = WindStructureProbe.loadedWorld(level, p -> WindStructureProbe.decodeLoaded(level, p));
            int generation = WindStructureProbe.inspect(view, pos).generationUnits();
            int generated = tile.energy.generate(generation);
            int charged = tile.charge.charge(tile.energy, tile::liveChargeOwner, tile::dropDeferredCharge);
            if (generated > 0 || charged > 0) tile.setChanged();
        } finally { tile.energyTicking = false; }
    }
    private IEnergyStorage energyStorage() {
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
            @Override public int extractEnergy(int amount, boolean simulate) {
                WindBodyBlockEntity root = accessibleEnergyRoot();
                if (root == null) return 0;
                int sent = root.energy.extract(amount, simulate, root.level.getGameTime());
                if (!simulate && sent > 0) root.setChanged();
                return sent;
            }
            @Override public int getEnergyStored() {
                WindBodyBlockEntity root = accessibleEnergyRoot();
                return root == null ? 0 : root.energy.units() / WindGeneratorRules.UNITS_PER_FE;
            }
            @Override public int getMaxEnergyStored() {
                return accessibleEnergyRoot() == null ? 0 : WindGeneratorRules.CAPACITY_UNITS / WindGeneratorRules.UNITS_PER_FE;
            }
            @Override public boolean canReceive() { return false; }
            @Override public boolean canExtract() { return accessibleEnergyRoot() != null; }
        };
    }
    public boolean hasFanInstalled() {
        return mainRoot() && !isRemoved() && !removing && !isFuture() && fan.is(AcademyContent.WIND_FAN.get());
    }
    /** 以前にキャッシュした代理のcapabilityを含め、アクセスのたびに現在のワールドを確かめる。 */
    private @Nullable WindBodyBlockEntity accessibleFanRoot() {
        if (!mainBody() || isFuture() || removing || isRemoved() || level == null || level.isClientSide
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this) return null;
        WindBodyBlockEntity root = root();
        return root != null && root.mainRoot() && root.complete() ? root : null;
    }
    private static boolean validFan(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AcademyContent.WIND_FAN.get());
    }

    /**
     * 結び付いたメニューのための、サーバー専用の入れ替え。渡した確認は、アイテムのcapability・写し・保存のコールバックの後で、
     * メニューとプレイヤーの持つ元を改めて確かめる。通常のメニューの呼び出し元では副作用の無い観測でなければならない。
     */
    public WindItemExchange exchangeMenuItem(ItemStack incoming, java.util.function.BooleanSupplier menuAndSourceValid) {
        return exchangeMenuItem(incoming, false, menuAndSourceValid);
    }
    public WindItemExchange exchangeMenuItem(ItemStack incoming, boolean onlyIfEmpty,
            java.util.function.BooleanSupplier menuAndSourceValid) {
        WindBodyBlockEntity root = root();
        if (root == null) return WindItemExchange.rejected();
        if (baseBody()) {
            WindChargeInventory inventory = accessibleChargeRoot();
            if (inventory == null) return WindItemExchange.rejected();
            return inventory.exchange(incoming, onlyIfEmpty, () -> accessibleChargeRoot() == inventory
                    && root() == root && menuAndSourceValid.getAsBoolean()
                    && accessibleChargeRoot() == inventory && root() == root);
        }
        if (accessibleFanRoot() != root || root.fanTransfer
                || !incoming.isEmpty() && (incoming.getCount() != 1 || !validFan(incoming)))
            return WindItemExchange.rejected();
        root.fanTransfer = true;
        try {
            ItemStack previous = root.fan;
            if (onlyIfEmpty && !previous.isEmpty()) return WindItemExchange.rejected();
            boolean clearing = incoming.isEmpty();
            java.util.function.BooleanSupplier live = () -> accessibleFanRoot() == root && root.fan == previous
                    && menuAndSourceValid.getAsBoolean()
                    && accessibleFanRoot() == root && root.fan == previous;
            if (!live.getAsBoolean()) return WindItemExchange.rejected();
            ItemStack prepared = incoming.copy();
            if (!live.getAsBoolean() || !prepared.isEmpty()
                    && (prepared.getCount() != 1 || !validFan(prepared))) return WindItemExchange.rejected();
            CompoundTag serialized = prepared.isEmpty() ? null : prepared.save(new CompoundTag());
            if (!live.getAsBoolean() || (clearing ? !prepared.isEmpty()
                    : prepared.getCount() != 1 || !validFan(prepared))) return WindItemExchange.rejected();
            root.fan = prepared; root.savedFan = serialized; root.setChanged();
            return new WindItemExchange(true, previous);
        } finally { root.fanTransfer = false; }
    }
    /**
     * 自動化と、後の原作風のメニューが使う、確認付きの1スロットのアダプタ。
     * スタックの読み取りや写しで、保存したアイテムの変更可能なNBTを呼び出し元へ見せてはならない。
     */
    public IItemHandlerModifiable fanInventory() {
        return new IItemHandlerModifiable() {
            private void check(int slot) { if (slot != 0) throw new IndexOutOfBoundsException(slot); }
            @Override public int getSlots() { return 1; }
            @Override public int getSlotLimit(int slot) { check(slot); return 1; }
            @Override public ItemStack getStackInSlot(int slot) {
                check(slot); WindBodyBlockEntity root = accessibleFanRoot();
                if (root == null || root.fanTransfer || root.fan.isEmpty()) return ItemStack.EMPTY;
                root.fanTransfer = true;
                try {
                    ItemStack source = root.fan, copy = source.copy();
                    return accessibleFanRoot() == root && root.fan == source ? copy : ItemStack.EMPTY;
                } finally { root.fanTransfer = false; }
            }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                check(slot); WindBodyBlockEntity root = accessibleFanRoot();
                return root != null && !root.fanTransfer && validFan(stack);
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                check(slot); WindBodyBlockEntity root = accessibleFanRoot();
                if (root == null || root.fanTransfer || !validFan(stack) || !root.fan.isEmpty()) return stack;
                root.fanTransfer = true;
                try {
                    // Forgeのアイテムの写しは付属のcapabilityのコールバックを呼ぶ。ロックしたまま写し、確定する前に生きている構造を検証する。
                    ItemStack inserted = stack.copyWithCount(1), rest = stack.copy(); rest.shrink(1);
                    CompoundTag serialized = inserted.save(new CompoundTag());
                    if (accessibleFanRoot() != root || !root.fan.isEmpty()) return stack;
                    if (!simulate) { root.fan = inserted; root.savedFan = serialized; root.setChanged(); }
                    return rest;
                } finally { root.fanTransfer = false; }
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                check(slot); WindBodyBlockEntity root = accessibleFanRoot();
                if (root == null || root.fanTransfer || amount <= 0 || root.fan.isEmpty()) return ItemStack.EMPTY;
                if (!simulate) {
                    // 唯一の所有権を直接移す: 切り離す前に、コールバックが保存したスタックを写して同じファンを壊したりドロップしたりできないようにする。
                    ItemStack result = root.fan; root.fan = ItemStack.EMPTY; root.savedFan = null;
                    root.setChanged(); return result;
                }
                root.fanTransfer = true;
                try {
                    ItemStack source = root.fan, copy = source.copy();
                    return accessibleFanRoot() == root && root.fan == source ? copy : ItemStack.EMPTY;
                } finally { root.fanTransfer = false; }
            }
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                check(slot); WindBodyBlockEntity root = accessibleFanRoot();
                if (root == null || root.fanTransfer || !stack.isEmpty() && (!validFan(stack) || stack.getCount() != 1)) return;
                root.fanTransfer = true;
                try {
                    ItemStack copied = stack.copy();
                    CompoundTag serialized = copied.isEmpty() ? null : copied.save(new CompoundTag());
                    if (accessibleFanRoot() == root) { root.fan = copied; root.savedFan = serialized; root.setChanged(); }
                } finally { root.fanTransfer = false; }
            }
        };
    }
    /**
     * 一致する本体の部品すべてに撤去中の印を付けた後で1回呼ぶ。エンティティを生成する前にインベントリを空にするので、
     * 撤去のコールバックがそれを複製することはできない。
     */
    public void dropFanOnRemoval() {
        if (!mainRoot() || !removing || isFuture() || level == null || level.isClientSide || fan.isEmpty()) return;
        ItemStack dropped = fan; fan = ItemStack.EMPTY; savedFan = null; setChanged();
        Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                worldPosition.getZ() + .5, dropped);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY && baseBody() && !isFuture() && !isRemoved())
            return energyCapability.cast();
        if (capability == ForgeCapabilities.ITEM_HANDLER && mainBody() && !isFuture() && !isRemoved())
            return fanCapability.cast();
        if (capability == ForgeCapabilities.ITEM_HANDLER && baseBody() && !isFuture() && !isRemoved())
            return chargeCapability.cast();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); fanCapability.invalidate(); energyCapability.invalidate(); chargeCapability.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps(); fanCapability = LazyOptional.of(this::fanInventory);
        energyCapability = LazyOptional.of(this::energyStorage);
        chargeCapability = LazyOptional.of(this::chargeInventory);
    }
    @Override public void onLoad() { super.onLoad(); scheduleReconcile(); }
    private void scheduleReconcile() {
        if (level != null && !level.isClientSide && !isRemoved())
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
    }
    /** 将来の形式の部品を強制的に読み込んだり消したりしない。 */
    public void reconcile() {
        if (level == null || level.isClientSide || isRemoved() || removing || isFuture()
                || origin == null || structureId == null) return;
        if (!level.hasChunkAt(origin)) { scheduleReconcile(); return; }
        if (level.getBlockEntity(origin) instanceof WindBodyBlockEntity savedRoot && savedRoot.isFuture()) return;
        WindBodyBlockEntity root = root();
        if (root == null) {
            if (getBlockState().getValue(WindBodyBlock.PART) != 0)
                level.destroyBlock(worldPosition, false);
            return;
        }
        WindBodyBlock body = (WindBodyBlock)root.getBlockState().getBlock();
        for (BlockPos pos : body.cells(root.worldPosition, root.getBlockState().getValue(WindBodyBlock.FACING))) {
            if (!level.hasChunkAt(pos)) { scheduleReconcile(); return; }
            if (level.getBlockEntity(pos) instanceof WindBodyBlockEntity part && part.isFuture()) return;
        }
        if (!root.complete()) level.destroyBlock(root.worldPosition, true);
    }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public void load(CompoundTag tag) {
        // 外部のアイテムのコールバックが、取引の途中で所有者やセーブを置き換えることはできない。
        if (charge.busy() || fanTransfer) return;
        super.load(tag); CompoundTag own = tag.getCompound("academy_wind_body");
        structureId = null; origin = null; future = null; removing = false;
        energy.load(tag, baseRoot());
        charge.load(tag, baseRoot());
        fan = ItemStack.EMPTY; savedFan = null; frozenFan = null; preservedFanTag = null; preservedFan = new CompoundTag();
        if (tag.contains("academy_wind_main")) preservedFanTag = tag.get("academy_wind_main").copy();
        if (preservedFanTag != null && !tag.contains("academy_wind_main", Tag.TAG_COMPOUND)) {
            frozenFan = preservedFanTag.copy();
        } else if (tag.contains("academy_wind_main", Tag.TAG_COMPOUND)) {
            CompoundTag inventory = tag.getCompound("academy_wind_main");
            preservedFan = inventory.copy();
            if (!inventory.contains("schema_version", Tag.TAG_INT) || inventory.getInt("schema_version") != 1) {
                frozenFan = inventory.copy();
            } else if (inventory.contains("fan")) {
                CompoundTag raw = inventory.getCompound("fan");
                // ItemStack.ofは数値のCountをすべてbyteに狭める。intの257は、再読込で使える1つのファンにならず、不透明なまま残らなければならない。
                boolean supported = inventory.contains("fan", Tag.TAG_COMPOUND)
                        && raw.contains("id", Tag.TAG_STRING) && raw.contains("Count", Tag.TAG_BYTE)
                        && raw.getByte("Count") == 1;
                ItemStack saved = supported ? ItemStack.of(raw) : ItemStack.EMPTY;
                // 未対応や壊れた内容は読み取り専用で保ち、スタックを消したり切り詰めたりして、その個数・ダメージ・独自データを黙って失わない。
                if (!mainRoot() || !validFan(saved) || saved.getCount() != 1) frozenFan = inventory.copy();
                else { fan = saved; savedFan = raw.copy(); }
            }
        }
        if (own.isEmpty()) return;
        if (!own.contains("schema_version", Tag.TAG_INT) || own.getInt("schema_version") != 1) {
            future = own.copy(); return;
        }
        if (own.hasUUID("structure_id")) structureId = own.getUUID("structure_id");
        if (own.contains("origin", Tag.TAG_LONG)) origin = BlockPos.of(own.getLong("origin"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        energy.save(tag, baseRoot(), future != null || frozenFan != null || charge.readOnly());
        charge.save(tag, baseRoot(), future != null || frozenFan != null || energy.readOnly());
        if (future != null) {
            // より新しい本体は、現在のインベントリに別の意味を与えるかもしれない。正規化せず、渡されたcompoundをそのまま保つ。
            if (preservedFanTag != null) tag.put("academy_wind_main", preservedFanTag.copy());
        } else if (frozenFan != null) tag.put("academy_wind_main", frozenFan.copy());
        else if (mainRoot() || !preservedFan.isEmpty()) {
            CompoundTag inventory = preservedFan.copy(); inventory.putInt("schema_version", 1);
            inventory.remove("fan");
            // ブロックのセーブに他のアイテムのコールバックを入れない。確認付きのインベントリの写し・移動の途中で再帰的に行う保存も含む。
            if (savedFan != null) inventory.put("fan", savedFan.copy());
            tag.put("academy_wind_main", inventory);
        }
        if (future != null) { tag.put("academy_wind_body", future.copy()); return; }
        CompoundTag own = new CompoundTag(); own.putInt("schema_version", 1);
        if (structureId != null) own.putUUID("structure_id", structureId);
        if (origin != null) own.putLong("origin", origin.asLong());
        tag.put("academy_wind_body", own);
    }
}
