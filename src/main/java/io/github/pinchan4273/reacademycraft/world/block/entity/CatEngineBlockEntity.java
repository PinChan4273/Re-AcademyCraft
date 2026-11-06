package io.github.pinchan4273.reacademycraft.world.block.entity;

import net.minecraft.core.HolderLookup;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 原作TileCatEngine。原作は無限発電機と呼ぶ。TileGeneratorBaseとして2000 IFのバッファを持ち、毎tick min(空き, 500) IF（そのgetGeneration）
 * で満たし、1回の引き出しで最大200 IFの帯域を渡す。このtickに満たした量がthisTickGenで、絵を回す。
 * 原作は右クリックで手動でnodeに繋ぎ、ここでも同じ。
 */
public final class CatEngineBlockEntity extends BlockEntity {
    /** 原作super("infinite_generator", 0, 2000, 200)とgetGenerationの500（milli-IF）。帯域はFE（1 IFにつき4）。 */
    public static final int BUFFER_MILLI_IF = 2000000, GENERATION_MILLI_IF = 500000, BANDWIDTH_FE = 800;
    private int milliIF;
    private boolean readOnly;
    private CompoundTag preserved = new CompoundTag();
    /** 原作thisTickGen（IF）: このtickに満たした量。絵がこれで回るので、サーバーはTileGeneratorBaseと同じく20tickごとに送る。 */
    private double generationIf, sentIf = -1;
    private int syncTicker = SYNC_TICKS;
    private static final int SYNC_TICKS = 20;
    /** tile上のRenderCatEngine自身の状態。クライアント専用で、保存しない。 */
    public double rotation;
    public long lastRender;
    public CatEngineBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.CAT_ENGINE_ENTITY.get(), pos, state); }
    public boolean readOnly() { return readOnly; }
    public double generationIf() { return generationIf; }
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, CatEngineBlockEntity tile) {
        if (!tile.mutable()) return;
        // TileGeneratorBase.update: energy += getGeneration(bufferSize - energy)。
        int generated = Math.min(BUFFER_MILLI_IF - tile.milliIF, GENERATION_MILLI_IF);
        if (generated > 0) { tile.milliIF += generated; tile.setChanged(); }
        tile.generationIf = generated / 1000.0;
        if (--tile.syncTicker > 0) return;
        tile.syncTicker = SYNC_TICKS;
        // 原作は関係なく20tickごとに送る。ここでは何も変わらない送信は飛ばす。
        if (tile.generationIf == tile.sentIf) return;
        tile.sentIf = tile.generationIf;
        level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag(); tag.putDouble("tickGen", generationIf); return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    /** クライアントへ送るのは回転だけ。保存データはサーバーに留める。 */
    @Override public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) { applyUpdateTag(io.github.pinchan4273.reacademycraft.world.NbtCompat.readAll(input), input.lookup()); }
    private void applyUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { generationIf = tag.getDoubleOr("tickGen", 0d); }
    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput input) { applyUpdateTag(io.github.pinchan4273.reacademycraft.world.NbtCompat.readAll(input), input.lookup()); }
    /** テスト用の入口: サーバーが送ったものとしての回転。 */
    public void setGenerationForTest(double value) { generationIf = value; }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide() && level.getBlockEntity(worldPosition) == this; }
    private IEnergyStorage storage() {
        return new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) { return 0; }
            // getProvidedEnergy: 持っている量を、1回の引き出しの帯域まで。
            public int extractEnergy(int amount, boolean simulate) {
                int given = mutable() ? Math.max(0, Math.min(Math.min(BANDWIDTH_FE, milliIF / 250), amount)) : 0;
                if (!simulate && given > 0) { milliIF -= given * 250; setChanged(); }
                return given;
            }
            public int getEnergyStored() { return mutable() ? milliIF / 250 : 0; }
            public int getMaxEnergyStored() { return BUFFER_MILLI_IF / 250; }
            public boolean canReceive() { return false; }
            public boolean canExtract() { return mutable(); }
        };
    }
    // NeoForge: AcademyCapabilitiesがこれらをRegisterCapabilitiesEventで登録する（Forge版はgetCapabilityの上書きとLazyOptional）。
    @Nullable public net.neoforged.neoforge.energy.IEnergyStorage energyCapability(@Nullable Direction side) { return !readOnly && !isRemoved() ? storage() : null; }
    // 1.21.1 の Block#onRemove に当たる処理（ブロックが変わって BE が消える時）。
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof io.github.pinchan4273.reacademycraft.world.block.CatEngineBlock block) block.onBlockEntityRemoved(state, level, pos);
    }
    @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input); loadTag(io.github.pinchan4273.reacademycraft.world.NbtCompat.readAll(input), input.lookup());
    }
    private void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
         preserved = tag.copy(); readOnly = tag.getIntOr("academy_cat_engine_schema", 0) > 1;
        if (!readOnly) milliIF = Math.max(0, Math.min(BUFFER_MILLI_IF, tag.getIntOr("milli_if", 0)));
    }
    @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output); CompoundTag tag = new CompoundTag(); saveTag(tag, io.github.pinchan4273.reacademycraft.world.NbtCompat.registries(this)); output.store(tag);
    }
    private void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
         tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_cat_engine_schema", 1);
        tag.putInt("milli_if", milliIF);
    }
}
