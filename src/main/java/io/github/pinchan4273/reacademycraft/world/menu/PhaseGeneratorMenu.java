package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.PhaseContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.PhaseGeneratorBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
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

/** 原作の3スロットの位相発電機の配置を、現行のサーバーが持つメニューで。 */
public final class PhaseGeneratorMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final @Nullable PhaseGeneratorBlockEntity tile;
    private final UUID owner;
    public final BlockPos pos;
    public PhaseGeneratorMenu(int id, Inventory inv, FriendlyByteBuf buf) { this(id, inv, buf.readBlockPos(), null, new ItemStackHandler(3), new SimpleContainerData(4)); }
    public PhaseGeneratorMenu(int id, Inventory inv, PhaseGeneratorBlockEntity tile) { this(id, inv, tile.getBlockPos(), tile, tile.inventory(), tile.menuData()); }
    private PhaseGeneratorMenu(int id, Inventory inv, BlockPos pos, @Nullable PhaseGeneratorBlockEntity tile, ItemStackHandler items, ContainerData data) {
        super(AcademyContent.PHASE_GEN_MENU.get(), id); this.pos = pos.immutable(); this.tile = tile; this.data = data; owner = inv.player.getUUID();
        // 原作ContainerPhaseGen: 液体の入口(45, 12)、空のユニットの出口(112, 51)、電池(42, 80)。
        // TechUIContainerのインベントリは(6, 105)から。
        addSlot(new SlotItemHandler(items, 0, 45, 12) { @Override public boolean mayPlace(ItemStack stack) { return stack.is(PhaseContent.FILLED.get()); } });
        addSlot(new SlotItemHandler(items, 1, 112, 51) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        addSlot(new SlotItemHandler(items, 2, 42, 80) {
            @Override public boolean mayPlace(ItemStack stack) { return SolarGeneratorBlockEntity.chargeable(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 6 + col * 18, 105 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data);
    }
    public int milliIF() { return Math.max(0, Math.min(PhaseGeneratorBlockEntity.MAX_MILLI_IF, (data.get(0) & 65535) | ((data.get(1) & 65535) << 16))); }
    public int phaseMB() { return Math.max(0, Math.min(PhaseGeneratorBlockEntity.TANK_MB, data.get(2))); }
    public PhaseGeneratorBlockEntity.Status status() { return PhaseGeneratorBlockEntity.Status.byOrdinal(data.get(3)); }
    @Override public boolean stillValid(Player p) {
        if (!owner.equals(p.getUUID()) || !p.isAlive() || p.isSpectator()) return false;
        return tile == null ? p.level().isClientSide : tile.getLevel() == p.level() && !tile.isRemoved() && !tile.readOnly()
                && p.level().hasChunkAt(pos) && p.level().getBlockEntity(pos) == tile && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var before = stack.copy(); boolean moved;
        if (index < 3) moved = moveItemStackTo(stack, 3, 39, true);
        else if (SolarGeneratorBlockEntity.chargeable(stack)) moved = moveItemStackTo(stack, 2, 3, false);
        else if (stack.is(PhaseContent.FILLED.get())) moved = moveItemStackTo(stack, 0, 1, false);
        else moved = index < 30 ? moveItemStackTo(stack, 30, 39, false) : moveItemStackTo(stack, 3, 30, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(p, stack); return before;
    }
}
