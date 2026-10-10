package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
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
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(this::storage);
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
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag(); tag.putDouble("tickGen", generationIf); return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    /** クライアントへ送るのは回転だけ。保存データはサーバーに留める。 */
    @Override public void handleUpdateTag(CompoundTag tag) { generationIf = tag.getDouble("tickGen"); }
    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    /** テスト用の入口: サーバーが送ったものとしての回転。 */
    public void setGenerationForTest(double value) { generationIf = value; }
    private boolean mutable() { return !readOnly && !isRemoved() && level != null && !level.isClientSide && level.getBlockEntity(worldPosition) == this; }
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
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved() && cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); energyCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); energyCap = LazyOptional.of(this::storage); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_cat_engine_schema") > 1;
        if (!readOnly) milliIF = Math.max(0, Math.min(BUFFER_MILLI_IF, tag.getInt("milli_if")));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_cat_engine_schema", 1);
        tag.putInt("milli_if", milliIF);
    }
}
