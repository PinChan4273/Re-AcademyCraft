package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.ImagFusorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.MetalFormerBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** 原作の5スロットの虚像融合機の配置を、現行のサーバーが持つメニューで: 変えるもの、変わった結果、入る液体と戻る空のユニット、電池。 */
public final class ImagFusorMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final @Nullable ImagFusorBlockEntity tile;
    private final UUID owner;
    public final BlockPos pos;
    public ImagFusorMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), null, new ItemStackHandler(5), new SimpleContainerData(5));
    }
    public ImagFusorMenu(int id, Inventory inv, ImagFusorBlockEntity tile) {
        this(id, inv, tile.getBlockPos(), tile, tile.inventory(), tile.menuData());
    }
    private ImagFusorMenu(int id, Inventory inv, BlockPos pos, @Nullable ImagFusorBlockEntity tile, ItemStackHandler items, ContainerData data) {
        super(AcademyContent.IMAG_FUSOR_MENU.get(), id);
        this.pos = pos.immutable(); this.tile = tile; this.data = data; owner = inv.player.getUUID();
        var level = inv.player.level();
        // 原作ContainerImagFusor: 入力(13, 49)、出力(143, 49)、液体の入口(13, 10)、電池(42, 80)、液体の出口(143, 10)。
        // TechUIContainerのインベントリは(6, 105)から。
        addSlot(new SlotItemHandler(items, ImagFusorBlockEntity.SLOT_INPUT, 13, 49) {
            @Override public boolean mayPlace(ItemStack stack) { return ImagFusorBlockEntity.inputValid(level, stack); }
        });
        addSlot(new SlotItemHandler(items, ImagFusorBlockEntity.SLOT_OUTPUT, 143, 49) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        addSlot(new SlotItemHandler(items, ImagFusorBlockEntity.SLOT_IMAG_INPUT, 13, 10) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(PhaseContent.FILLED.get()); }
        });
        addSlot(new SlotItemHandler(items, ImagFusorBlockEntity.SLOT_ENERGY, 42, 80) {
            @Override public boolean mayPlace(ItemStack stack) { return MetalFormerBlockEntity.battery(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new SlotItemHandler(items, ImagFusorBlockEntity.SLOT_IMAG_OUTPUT, 143, 10) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 6 + col * 18, 105 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data);
    }
    public int milliIF() { return Math.max(0, Math.min(ImagFusorBlockEntity.MAX_MILLI_IF, (data.get(0) & 65535) | ((data.get(1) & 65535) << 16))); }
    public int phaseMB() { return Math.max(0, Math.min(ImagFusorBlockEntity.TANK_MB, data.get(2))); }
    public int progress() { return Math.max(0, Math.min(ImagFusorBlockEntity.WORK_TICKS, data.get(3))); }
    public ImagFusorBlockEntity.Status status() { return ImagFusorBlockEntity.Status.byOrdinal(data.get(4)); }
    @Override public boolean stillValid(Player p) {
        if (!owner.equals(p.getUUID()) || !p.isAlive() || p.isSpectator()) return false;
        return tile == null ? p.level().isClientSide : tile.getLevel() == p.level() && !tile.isRemoved() && !tile.readOnly()
                && p.level().hasChunkAt(pos) && p.level().getBlockEntity(pos) == tile
                && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var before = stack.copy(); boolean moved;
        if (index < 5) moved = moveItemStackTo(stack, 5, 41, true);
        else if (MetalFormerBlockEntity.battery(stack)) moved = moveItemStackTo(stack, 3, 4, false);
        else if (stack.is(PhaseContent.FILLED.get())) moved = moveItemStackTo(stack, 2, 3, false);
        else if (ImagFusorBlockEntity.inputValid(p.level(), stack)) moved = moveItemStackTo(stack, 0, 1, false);
        else moved = index < 32 ? moveItemStackTo(stack, 32, 41, false) : moveItemStackTo(stack, 5, 32, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(p, stack); return before;
    }
}
