package io.github.pinchan4273.reacademycraft.world.block.entity;

import io.github.pinchan4273.reacademycraft.energy.WirelessMatrix;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.MaterialContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 原作TileMatrix: 3枚の制約板とmatrixコアで動き、コアの段階が保持できるネットワークのすべてを決める: 段階ごとにnode 8つ、
 * 範囲は24 × 段階の平方根、帯域は60 × 段階の2乗。
 */
public final class WirelessMatrixBlockEntity extends BlockEntity implements WirelessMatrix {
    public static final int SLOT_CORE = 3;
    private boolean readOnly;
    private CompoundTag preserved = new CompoundTag();
    @Nullable private String placer;
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_CORE ? coreLevel(stack) > 0 : stack.is(MaterialContent.item("constraint_plate"));
        }
        @Override protected void onContentsChanged(int slot) {
            setChanged();
            // RenderMatrixは板とコアに応じて盾を表示するので、クライアントへ伝える。
            if (level != null && !level.isClientSide)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    };
    /** RenderMatrixのための、クライアントが知る板とコア。 */
    private int shownCore, shownPlates;
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);

    public WirelessMatrixBlockEntity(BlockPos pos, BlockState state) { super(AcademyContent.MATRIX_ENTITY.get(), pos, state); }
    public boolean readOnly() { return readOnly; }
    public ItemStackHandler inventory() { return items; }
    @Nullable public String placer() { return placer; }
    public void setPlacer(net.minecraft.world.entity.player.Player player) {
        if (placer == null && player != null) { placer = player.getGameProfile().getName(); setChanged(); }
    }
    /** 原作はコアのmetadataから段階を読む。移植版は段階ごとにアイテムを持つ。 */
    public static int coreLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        for (int tier = 1; tier <= 3; tier++) if (stack.is(MaterialContent.item("mat_core_" + (tier - 1)))) return tier;
        return 0;
    }
    public int coreLevel() { return coreLevel(items.getStackInSlot(SLOT_CORE)); }
    public int plateCount() {
        int count = 0;
        for (int slot = 0; slot < SLOT_CORE; slot++) if (!items.getStackInSlot(slot).isEmpty()) count++;
        return count;
    }
    /** 原作isWorking: コアと3枚の板すべて。 */
    /** RenderMatrix: 3枚の板とコアが入っている間は3つの盾。 */
    public boolean shieldsShown() {
        return level != null && level.isClientSide ? shownPlates == 3 && shownCore > 0 : plateCount() == 3 && coreLevel() > 0;
    }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag(); tag.putInt("core", coreLevel()); tag.putInt("plates", plateCount()); return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void handleUpdateTag(CompoundTag tag) { shownCore = tag.getInt("core"); shownPlates = tag.getInt("plates"); }
    @Override public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    public boolean working() { return !readOnly && coreLevel() > 0 && plateCount() == 3; }
    @Override public int wirelessCapacity() { return working() ? 8 * coreLevel() : 0; }
    @Override public double wirelessRange() { return working() ? 24 * Math.sqrt(coreLevel()) : 0; }
    @Override public int wirelessBandwidthMilliIF() { return working() ? coreLevel() * coreLevel() * 60 * 1000 : 0; }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!readOnly && !isRemoved() && cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCap = LazyOptional.of(() -> items); }
    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = tag.copy(); readOnly = tag.getInt("academy_matrix_schema") > 1;
        if (readOnly) return;
        placer = tag.contains("placer") ? tag.getString("placer") : null;
        var inventoryTag = tag.getCompound("inventory").copy(); inventoryTag.putInt("Size", 4); items.deserializeNBT(inventoryTag);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.merge(preserved); if (readOnly) return;
        tag.putInt("academy_matrix_schema", 1);
        if (placer != null) tag.putString("placer", placer);
        tag.put("inventory", items.serializeNBT());
    }
}
