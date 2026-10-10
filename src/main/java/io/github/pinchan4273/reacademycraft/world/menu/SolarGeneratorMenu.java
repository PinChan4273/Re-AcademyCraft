package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** 原作ContainerSolarGenの1つの電池スロットを原作の位置に置き、範囲を限ったForgeのメニューデータを持つ。 */
public final class SolarGeneratorMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final @Nullable SolarGeneratorBlockEntity tile;
    private final UUID owner;
    public final BlockPos pos;
    public SolarGeneratorMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), null, new ItemStackHandler(1), new SimpleContainerData(3));
    }
    public SolarGeneratorMenu(int id, Inventory inv, SolarGeneratorBlockEntity tile) {
        this(id, inv, tile.getBlockPos(), tile, tile.inventory(), tile.menuData());
    }
    private SolarGeneratorMenu(int id, Inventory inv, BlockPos pos, @Nullable SolarGeneratorBlockEntity tile, ItemStackHandler items, ContainerData data) {
        super(AcademyContent.SOLAR_MENU.get(), id); this.pos = pos.immutable(); this.tile = tile; this.data = data; owner = inv.player.getUUID();
        // 原作ContainerSolarGenとTechUIContainer: 電池は(42, 81)、インベントリは(6, 105)から。
        addSlot(new SlotItemHandler(items, 0, 42, 81) {
            @Override public boolean mayPlace(ItemStack stack) { return SolarGeneratorBlockEntity.chargeable(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 6 + col * 18, 105 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data);
    }
    public int milliIF() { return Math.max(0, Math.min(SolarGeneratorBlockEntity.MAX_MILLI_IF, (data.get(0) & 65535) | ((data.get(1) & 65535) << 16))); }
    public int status() { return Math.max(0, Math.min(2, data.get(2))); }
    @Override public boolean stillValid(Player p) {
        if (!owner.equals(p.getUUID()) || !p.isAlive() || p.isSpectator()) return false;
        return tile == null ? p.level().isClientSide : tile.getLevel() == p.level() && !tile.isRemoved() && !tile.readOnly()
                && p.level().hasChunkAt(pos) && p.level().getBlockEntity(pos) == tile && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var before = stack.copy();
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, 37, true)
                : SolarGeneratorBlockEntity.chargeable(stack) ? moveItemStackTo(stack, 0, 1, false)
                : index < 28 ? moveItemStackTo(stack, 28, 37, false) : moveItemStackTo(stack, 1, 28, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p, stack); return before;
    }
}
