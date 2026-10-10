package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.block.entity.AbilityInterfererBlockEntity;
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

/**
 * 原作ContainAbilityInterferer: (139, 25)の電池スロットとホットバー（原作のパネルにはインベントリの残りを置く余地が無い）、GUIがサーバーを
 * 通して変える2つのもの（オンかどうかと届く範囲）、変わるたびにパネルへ送る原作のホワイトリスト。
 */
public final class AbilityInterfererMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE = 0, BUTTON_SHRINK = 1, BUTTON_GROW = 2;
    /** 原作のGUIのスライダーは1ブロック単位で動く。このパネルは10ずつ進む。 */
    public static final double STEP = 10;
    private final ContainerData data;
    private final @Nullable AbilityInterfererBlockEntity tile;
    private final UUID owner;
    public final BlockPos pos;
    /** クライアント: サーバーが最後に送った機械のホワイトリスト。サーバー: 最後に送ったもの。 */
    private java.util.List<String> whitelist = java.util.List.of();
    private boolean whitelistSent;
    public AbilityInterfererMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), null, new ItemStackHandler(1), new SimpleContainerData(4));
    }
    public AbilityInterfererMenu(int id, Inventory inv, AbilityInterfererBlockEntity tile) {
        this(id, inv, tile.getBlockPos(), tile, tile.inventory(), tile.menuData());
    }
    private AbilityInterfererMenu(int id, Inventory inv, BlockPos pos, @Nullable AbilityInterfererBlockEntity tile,
                                  ItemStackHandler items, ContainerData data) {
        super(AcademyContent.INTERFERER_MENU.get(), id);
        this.pos = pos.immutable(); this.tile = tile; this.data = data; owner = inv.player.getUUID();
        addSlot(new SlotItemHandler(items, AbilityInterfererBlockEntity.SLOT_BATTERY, 139, 25) {
            @Override public boolean mayPlace(ItemStack stack) { return MetalFormerBlockEntity.battery(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data);
    }
    public int milliIF() { return Math.max(0, Math.min(AbilityInterfererBlockEntity.MAX_MILLI_IF, (data.get(0) & 65535) | ((data.get(1) & 65535) << 16))); }
    public int range() { return Math.max(0, data.get(2)); }
    public boolean enabled() { return data.get(3) != 0; }
    @Override public boolean stillValid(Player p) {
        if (!owner.equals(p.getUUID()) || !p.isAlive() || p.isSpectator()) return false;
        return tile == null ? p.level().isClientSide : tile.getLevel() == p.level() && !tile.isRemoved() && !tile.readOnly()
                && p.level().hasChunkAt(pos) && p.level().getBlockEntity(pos) == tile
                && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    /** パネル自身のボタン: 開発機と同じく、決めるのはサーバー。 */
    @Override public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player) || tile == null) return false;
        return switch (id) {
            case BUTTON_TOGGLE -> tile.setEnabled(!tile.enabled());
            case BUTTON_SHRINK -> tile.setRange(tile.range() - STEP);
            case BUTTON_GROW -> tile.setRange(tile.range() + STEP);
            default -> false;
        };
    }
    /** 開いているパネルからの原作set_whitelist。 */
    public boolean setWhitelist(Player player, java.util.List<String> names) {
        return stillValid(player) && tile != null && tile.setWhitelist(names);
    }
    public java.util.List<String> whitelist() { return whitelist; }
    public void acceptWhitelist(java.util.List<String> names) { whitelist = java.util.List.copyOf(names); }
    /** ホワイトリストは、パネルを開いたときと、その後変わるたびにパネルへ届く。 */
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (tile == null || tile.getLevel() == null || tile.getLevel().isClientSide) return;
        var now = java.util.List.copyOf(tile.whitelist());
        if (whitelistSent && now.equals(whitelist)) return;
        var player = tile.getLevel().getServer() == null ? null : tile.getLevel().getServer().getPlayerList().getPlayer(owner);
        if (player == null) return;
        whitelist = now; whitelistSent = true;
        io.github.pinchan4273.reacademycraft.network.AcademyNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new io.github.pinchan4273.reacademycraft.network.InterfererWhitelistSync(containerId, now));
    }
    @Override public ItemStack quickMoveStack(Player p, int index) {
        if (!stillValid(p) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var before = stack.copy(); boolean moved;
        // 原作の規則: 機械のスロットからホットバーへ、ホットバーの電池をそこへ。
        if (index < 1) moved = moveItemStackTo(stack, 1, 10, true);
        else moved = MetalFormerBlockEntity.battery(stack) && moveItemStackTo(stack, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(p, stack); return before;
    }
}
