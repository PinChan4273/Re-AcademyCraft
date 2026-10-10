package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.develop.DeveloperTier;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.NormalDeveloperBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 据え置きの開発機の各セルのBlockEntity（原作TileDeveloper）。エネルギーは原点のセルだけが持つ: 原作の容量は通常50000 IF、
 * 上位200000 IF。原点は状態から逆算する（NormalDeveloperBlock.origin）。
 * 保存はacademy_developer: schema 2はenergy_fe（原点のみ）。schema 1（以前の版）のstructure_idとoriginは読み捨てる。
 */
public final class NormalDeveloperBlockEntity extends BlockEntity {
    /** academy_developerの版。1は以前の版で、読むときは識別子と原点を捨ててenergy_feだけを使う。 */
    public static final int SCHEMA = 2;
    private final DeveloperTier tier;
    private int maxFE() { return tier.capacity * 4; }
    private int transferFE() { return tier.bandwidth * 4; }
    public DeveloperTier tier() { return tier; }
    private @Nullable CompoundTag future;
    private int energyFE;
    private boolean removing;
    private @Nullable Binding user;
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(this::storage);
    public NormalDeveloperBlockEntity(BlockPos pos, BlockState state) {
        super(((NormalDeveloperBlock)state.getBlock()).tier() == DeveloperTier.ADVANCED
                ? AcademyContent.DEV_ADVANCED_ENTITY.get() : AcademyContent.DEV_NORMAL_ENTITY.get(), pos, state);
        tier = ((NormalDeveloperBlock)state.getBlock()).tier();
    }
    /** 設置した直後の初期状態。 */
    public void initialize() {
        energyFE = 0; future = null; removing = false; user = null; setChanged();
    }
    private BlockPos origin() { return NormalDeveloperBlock.origin(worldPosition, getBlockState()); }
    public boolean removing() { return removing; }
    /** 知らない版のデータを持ち、そのまま保っているか。 */
    public boolean unknownSchema() { return future != null; }
    public void beginRemoval() { removing = true; }
    public boolean sameStructure(NormalDeveloperBlockEntity main) {
        // どちらも読める版のデータで、同じ種類のブロックで、同じ向きで、状態から逆算した原点に、部品0の本体がある。
        // 知らない版（future）の部品は、位置と状態が合っていても通常の機械に含めない: 完成・給電・使用・まとめての撤去の対象外。
        return future == null && main.future == null
                && getBlockState().is(main.getBlockState().getBlock()) && tier == main.tier
                && main.getBlockState().getValue(NormalDeveloperBlock.PART) == 0
                && getBlockState().getValue(NormalDeveloperBlock.FACING) == main.getBlockState().getValue(NormalDeveloperBlock.FACING)
                && origin().equals(main.worldPosition);
    }
    public @Nullable NormalDeveloperBlockEntity main() {
        if (level == null || future != null) return null;
        var origin = origin();
        if (!level.hasChunkAt(origin)) return null;
        return level.getBlockEntity(origin) instanceof NormalDeveloperBlockEntity main && sameStructure(main) ? main : null;
    }
    public boolean complete() {
        if (main() != this || removing || isRemoved() || level == null) return false;
        for (var pos : NormalDeveloperBlock.cells(worldPosition, getBlockState().getValue(NormalDeveloperBlock.FACING)))
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof NormalDeveloperBlockEntity sub) || sub.removing || !sub.sameStructure(this)) return false;
        return true;
    }
    @Override public void onLoad() { super.onLoad(); scheduleReconcile(); }
    private void scheduleReconcile() {
        if (level != null && !level.isClientSide && !isRemoved()) level.scheduleTick(worldPosition,getBlockState().getBlock(),20);
    }
    /**
     * chunkの境界・再読込の後の、読み込まれた部品の回復。他のchunkを読み込まない。
     * 未知の形式は、互換の読み手ができるまで、見えるグループ全体を保つ。
     */
    public void reconcile() {
        if (level == null || level.isClientSide || isRemoved() || removing || future != null) return;
        var origin = origin();
        if (!level.hasChunkAt(origin)) { scheduleReconcile(); return; }
        if (level.getBlockEntity(origin) instanceof NormalDeveloperBlockEntity original && original.future != null) return;
        var main = main();
        if (main == null) {
            // 壊れた本体でも、保存されたエネルギーとメタデータを持つ: 決して消さない。
            if (getBlockState().getValue(NormalDeveloperBlock.PART) != 0) level.destroyBlock(worldPosition,false);
            return; // 孤立した子部品は独自のアイテムを持たない。
        }
        for (var pos : NormalDeveloperBlock.cells(main.worldPosition,main.getBlockState().getValue(NormalDeveloperBlock.FACING))) {
            if (!level.hasChunkAt(pos)) { scheduleReconcile(); return; }
            if (level.getBlockEntity(pos) instanceof NormalDeveloperBlockEntity part && part.future != null) return;
        }
        // 本体が読み込まれていない間に子部品が壊された。残った1つの本体が最終的なドロップを持ち、その撤去はこの機械の部品だけを消す。
        if (!main.complete()) level.destroyBlock(main.worldPosition,true);
    }
    public int energyIF() { return energyFE / 4; }
    public boolean consumeIF(int amount) {
        if (amount < 0 || amount > energyIF() || !complete()) return false;
        energyFE -= amount * 4; setChanged(); return true;
    }
    /** プレビュー用のOPコマンド専用。通常の充電はFEの受け口を通る。 */
    public boolean setEnergyIFForTest(int amount) {
        if (level == null || level.isClientSide || amount < 0 || amount > tier.capacity || !complete()) return false;
        energyFE = amount * 4; setChanged(); return true;
    }
    private boolean accessible(net.minecraft.server.level.ServerPlayer player) {
        return level == player.level() && player.isAlive() && !player.isSpectator() && complete()
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) <= 64
                && level.mayInteract(player, worldPosition);
    }
    public @Nullable io.github.pinchan4273.reacademycraft.develop.DevelopmentDevice bind(net.minecraft.server.level.ServerPlayer player) {
        if (!accessible(player)) return null;
        if (user != null && user.valid(user.owner) && user.owner.containerMenu instanceof io.github.pinchan4273.reacademycraft.develop.DeveloperMenu menu
                && menu.usesDevice(user)) return null;
        user = new Binding(player); return user;
    }
    private final class Binding implements io.github.pinchan4273.reacademycraft.develop.DevelopmentDevice {
        final net.minecraft.server.level.ServerPlayer owner;
        Binding(net.minecraft.server.level.ServerPlayer owner) { this.owner = owner; }
        @Override public DeveloperTier tier() { return NormalDeveloperBlockEntity.this.tier; }
        @Override public boolean valid(net.minecraft.server.level.ServerPlayer player) {
            return user == this && player == owner && accessible(player);
        }
        @Override public int energy() { return energyIF(); }
        @Override public boolean consume(int amount) { return valid(owner) && consumeIF(amount); }
        @Override public void close() { if (user == this) user = null; }
    }
    private IEnergyStorage storage() {
        return new IEnergyStorage() {
            @Override public int receiveEnergy(int maximum, boolean simulate) {
                if (!complete()) return 0;
                int accepted = Math.min(maxFE() - energyFE, Math.min(transferFE(), Math.max(0, maximum)));
                if (!simulate && accepted > 0) { energyFE += accepted; setChanged(); }
                return accepted;
            }
            @Override public int extractEnergy(int maximum, boolean simulate) { return 0; } // 原作の受電機。発電機ではない。
            @Override public int getEnergyStored() { return energyFE; }
            @Override public int getMaxEnergyStored() { return maxFE(); }
            @Override public boolean canExtract() { return false; }
            @Override public boolean canReceive() { return true; }
        };
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY && !isRemoved() && future == null
                && getBlockState().getValue(NormalDeveloperBlock.PART) == 0 ? energy.cast() : super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { user = null; super.invalidateCaps(); energy.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); energy = LazyOptional.of(this::storage); }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public void load(CompoundTag tag) {
        super.load(tag); var own = tag.getCompound("academy_developer");
        energyFE = 0; future = null; removing = false; user = null;
        if (own.isEmpty()) return;
        int version = own.contains("schema_version", Tag.TAG_INT) ? own.getInt("schema_version") : -1;
        if (version != 1 && version != SCHEMA) { future = own.copy(); return; }
        if (own.contains("energy_fe", Tag.TAG_INT) && getBlockState().getValue(NormalDeveloperBlock.PART) == 0)
            energyFE = Math.max(0, Math.min(maxFE(), own.getInt("energy_fe")));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (future != null) { tag.put("academy_developer", future.copy()); return; }
        var own = new CompoundTag(); own.putInt("schema_version", SCHEMA);
        if (getBlockState().getValue(NormalDeveloperBlock.PART) == 0) own.putInt("energy_fe", energyFE);
        tag.put("academy_developer", own);
    }
}
