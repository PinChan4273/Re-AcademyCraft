package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.energy.WirelessNetworks;
import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.WirelessNodeBlockEntity;
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
import org.jetbrains.annotations.Nullable;

/**
 * 原作ContainerNode: nodeの2つのエネルギーアイテムスロット（(42, 10)は電力を引くもの、(42, 80)は充電するもの）をTechUIContainerの
 * インベントリの上に置き、パネルが表示するもの: nodeが持つ量、容量に対して繋いでいる数、範囲、ネットワーク上か、名前と設置者、
 * 設置者だけにはパスワード。
 */
public final class WirelessNodeMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final @Nullable WirelessNodeBlockEntity tile;
    private final UUID owner;
    public final BlockPos pos;
    /** 名前は数値ではないので、データスロットではなくメニュー自身の開くときのバッファで運ぶ。 */
    private final String name;
    /** 原作のパネルはnodeを置いた者を表示し、その者だけに名前を編集させる。 */
    private final String placer;
    /** 原作はパスワードをnodeの近くのすべてのクライアントへ同期する。ここでは設置者だけに伝える。 */
    private final String password;
    /**
     * GUI-ENERGY-001: データスロットの並び。1.20.1は各スロットをshortで送るので、16bitを超えうるIFの蓄電量と最大値
     * （ADVANCEDは200,000 IF）は上位・下位の2つに分ける。最後のSYNCEDはサーバーが常に1を送る。スロットは番号の順に
     * 届くので、1が届いた後は前のスロットの値も届いている（初回の同期の前の0と、本当の0 IFを区別する）。
     */
    static final int STORED_LOW = 0, STORED_HIGH = 1, MAX_LOW = 2, MAX_HIGH = 3, LOAD = 4, CAPACITY = 5, RANGE = 6,
            ON_NETWORK = 7, SYNCED = 8, DATA_COUNT = 9;
    public WirelessNodeMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), null, new SimpleContainerData(DATA_COUNT), buf.readUtf(32), buf.readUtf(64), buf.readUtf(32),
                new net.neoforged.neoforge.items.ItemStackHandler(2));
    }
    public WirelessNodeMenu(int id, Inventory inv, WirelessNodeBlockEntity tile) {
        this(id, inv, tile.getBlockPos(), tile, menuData(tile), tile.nodeName(), tile.placer() == null ? "" : tile.placer(), "", tile.inventory());
    }
    private static ContainerData menuData(WirelessNodeBlockEntity tile) {
        return new ContainerData() {
            public int get(int index) {
                var networks = tile.getLevel() instanceof net.minecraft.server.level.ServerLevel server
                        ? WirelessNetworks.of(server) : null;
                var network = networks == null ? null : networks.networkAt(tile.getBlockPos());
                return switch (index) {
                    case STORED_LOW -> MenuInts.low(tile.milliIF() / 1000);
                    case STORED_HIGH -> MenuInts.high(tile.milliIF() / 1000);
                    case MAX_LOW -> MenuInts.low(tile.maxMilliIF() / 1000);
                    case MAX_HIGH -> MenuInts.high(tile.maxMilliIF() / 1000);
                    case LOAD -> networks == null ? 0 : networks.connection(tile.getBlockPos()).load();
                    case CAPACITY -> tile.wirelessCapacity();
                    case RANGE -> (int) Math.round(tile.wirelessRange());
                    case ON_NETWORK -> network == null ? 0 : 1;
                    case SYNCED -> 1;
                    default -> 0;
                };
            }
            public void set(int index, int value) { }
            public int getCount() { return DATA_COUNT; }
        };
    }
    private WirelessNodeMenu(int id, Inventory inv, BlockPos pos, @Nullable WirelessNodeBlockEntity tile,
                             ContainerData data, String name, String placer, String password, net.neoforged.neoforge.items.ItemStackHandler items) {
        super(AcademyContent.NODE_MENU.get(), id);
        this.pos = pos.immutable(); this.tile = tile; this.data = data; this.name = name; this.placer = placer; this.password = password; owner = inv.player.getUUID();
        for (int slot = 0; slot < 2; slot++) addSlot(new net.neoforged.neoforge.items.SlotItemHandler(items, slot, 42, slot == 0 ? 10 : 80) {
            @Override public boolean mayPlace(ItemStack stack) { return WirelessNodeBlockEntity.chargeable(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 6 + col * 18, 105 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data);
    }
    /** 初回の同期が届いたか（届く前は蓄電量を0と表示しない）。 */
    public boolean synced() { return data.get(SYNCED) == 1; }
    /** 蓄電量（IF）。上位・下位のスロットから組み立てる。0から最大値の間に収める。 */
    public int storedIF() {
        return Math.max(0, Math.min(maxIF(), MenuInts.join(data.get(STORED_LOW), data.get(STORED_HIGH))));
    }
    public int maxIF() { return Math.max(1, MenuInts.join(data.get(MAX_LOW), data.get(MAX_HIGH))); }
    public int load() { return Math.max(0, data.get(LOAD)); }
    public int capacity() { return Math.max(0, data.get(CAPACITY)); }
    public int range() { return Math.max(0, data.get(RANGE)); }
    public boolean onNetwork() { return data.get(ON_NETWORK) != 0; }
    public String nodeName() { return tile == null ? name : tile.nodeName(); }
    public String placer() { return placer; }
    public String password() { return password; }
    /** 原作: 名前欄を編集できるのはnodeの設置者だけ。 */
    public boolean mayRename(Player player) { return !placer.isEmpty() && placer.equals(player.getGameProfile().getName()); }
    @Override public boolean stillValid(Player p) {
        if (!owner.equals(p.getUUID()) || !p.isAlive() || p.isSpectator()) return false;
        return tile == null ? p.level().isClientSide : tile.getLevel() == p.level() && !tile.isRemoved() && !tile.readOnly()
                && p.level().hasChunkAt(pos) && p.level().getBlockEntity(pos) == tile
                && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    /** 原作の移動の規則: nodeのアイテムをインベントリへ、エネルギーアイテムをnodeへ。 */
    @Override public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var before = stack.copy();
        boolean moved = index < 2 ? moveItemStackTo(stack, 2, 38, true)
                : WirelessNodeBlockEntity.chargeable(stack) ? moveItemStackTo(stack, 0, 2, false)
                : index < 29 ? moveItemStackTo(stack, 29, 38, false) : moveItemStackTo(stack, 2, 29, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(p, stack); return before;
    }
}
