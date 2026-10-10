package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.MaterialContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessMatrixBlockEntity;
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

/** 原作のmatrixのパネル: 3枚の板とコア、保持するネットワークが運んでいる量、コアが与える数値。ネットワークの命名とパスワードは独自のパケットで送る。 */
public final class WirelessMatrixMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final @Nullable WirelessMatrixBlockEntity tile;
    private final UUID owner;
    public final BlockPos pos;
    public WirelessMatrixMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), null, new ItemStackHandler(4), new SimpleContainerData(5), buf.readUtf(64), buf.readUtf(32));
    }
    public WirelessMatrixMenu(int id, Inventory inv, WirelessMatrixBlockEntity tile) {
        this(id, inv, tile.getBlockPos(), tile, tile.inventory(), menuData(tile, inv.player), tile.placer() == null ? "" : tile.placer(), "");
    }
    /**
     * 原作のパネルはmatrixを置いた者を表示する。ネットワークの命名と保護ができるのは設置者だけで、パスワードを伝えるのも設置者だけ
     * （原作はパネルを開いた誰にでも送る）。
     */
    private final String placer, password;
    public String placer() { return placer; }
    public String password() { return password; }
    public boolean isPlacer(Player player) { return !placer.isEmpty() && placer.equals(player.getGameProfile().getName()); }
    /** 負荷とバッファは、機械ではなくワールドのネットワークから来る。 */
    private static ContainerData menuData(WirelessMatrixBlockEntity tile, Player viewer) {
        return new ContainerData() {
            public int get(int index) {
                var network = tile.getLevel() instanceof net.minecraft.server.level.ServerLevel server
                        ? WirelessNetworks.of(server).networkAt(tile.getBlockPos()) : null;
                return switch (index) {
                    case 0 -> tile.wirelessCapacity();
                    case 1 -> (int) Math.round(tile.wirelessRange());
                    case 2 -> tile.wirelessBandwidthMilliIF() / 1000;
                    case 3 -> network == null ? -1 : network.load();
                    case 4 -> network == null ? 0 : network.bufferMilliIF() / 1000;
                    default -> 0;
                };
            }
            public void set(int index, int value) { }
            public int getCount() { return 5; }
        };
    }
    private WirelessMatrixMenu(int id, Inventory inv, BlockPos pos, @Nullable WirelessMatrixBlockEntity tile,
                               ItemStackHandler items, ContainerData data, String placer, String password) {
        super(AcademyContent.MATRIX_MENU.get(), id);
        this.pos = pos.immutable(); this.tile = tile; this.data = data; this.placer = placer; this.password = password; owner = inv.player.getUUID();
        // 原作ContainerMatrix: 板は(78, 11)、(53, 60)、(104, 60)、コアは(78, 36)。
        int[][] plates = {{78, 11}, {53, 60}, {104, 60}};
        for (int slot = 0; slot < 3; slot++)
            addSlot(new SlotItemHandler(items, slot, plates[slot][0], plates[slot][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(MaterialContent.item("constraint_plate")); }
                @Override public int getMaxStackSize() { return 1; }
            });
        addSlot(new SlotItemHandler(items, WirelessMatrixBlockEntity.SLOT_CORE, 78, 36) {
            @Override public boolean mayPlace(ItemStack stack) { return WirelessMatrixBlockEntity.coreLevel(stack) > 0; }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 6 + col * 18, 105 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data);
    }
    public int capacity() { return Math.max(0, data.get(0)); }
    public int range() { return Math.max(0, data.get(1)); }
    public int bandwidth() { return Math.max(0, data.get(2)); }
    /** 参加したnodeの数。まだネットワークが無ければ-1。 */
    public int load() { return data.get(3); }
    public int bufferIF() { return Math.max(0, data.get(4)); }
    public boolean hasNetwork() { return load() >= 0; }
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
        if (index < 4) moved = moveItemStackTo(stack, 4, 40, true);
        else if (WirelessMatrixBlockEntity.coreLevel(stack) > 0) moved = moveItemStackTo(stack, 3, 4, false);
        else if (stack.is(MaterialContent.item("constraint_plate"))) moved = moveItemStackTo(stack, 0, 3, false);
        else moved = index < 31 ? moveItemStackTo(stack, 31, 40, false) : moveItemStackTo(stack, 4, 31, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(p, stack); return before;
    }
}
