package io.github.pinchan4273.reacademycraft.world.menu;

import io.github.pinchan4273.reacademycraft.world.AcademyContent;
import io.github.pinchan4273.reacademycraft.world.WindStructureProbe;
import io.github.pinchan4273.reacademycraft.world.block.entity.WindBodyBlockEntity;
import io.github.pinchan4273.reacademycraft.world.block.entity.SolarGeneratorBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 原作の土台・本体のスロットの役割を分け、明示的なサーバーの取引で扱う。
 * 見えるスロットはスナップショット。バニラはクライアントでそれを予測してよいが、コールバックの失敗後にSlot.setでサーバーのcapabilityへ
 * 黙って書いてはならない。
 * TODO: 同じ所有権の保証を持つ、機械のスロットへのドラッグ配分を加える。
 */
public final class WindGeneratorMenu extends AbstractContainerMenu {
    public static final int PAGE_WIDTH = 176, PAGE_HEIGHT = 187;
    private final Player owner;
    private final @Nullable WindMenuAccess access;
    private final @Nullable WindBodyBlockEntity part;
    private final SimpleContainer display = new SimpleContainer(1);
    private final ContainerData data;
    private boolean interacting;
    public final boolean main;
    public final BlockPos pos;

    public static WindGeneratorMenu base(int id, Inventory inv, FriendlyByteBuf buf) {
        return new WindGeneratorMenu(id, inv, buf.readBlockPos(), false, null, null);
    }
    public static WindGeneratorMenu main(int id, Inventory inv, FriendlyByteBuf buf) {
        return new WindGeneratorMenu(id, inv, buf.readBlockPos(), true, null, null);
    }
    public WindGeneratorMenu(int id, Inventory inv, WindBodyBlockEntity part, WindMenuAccess access) {
        this(id, inv, access.rootPos(), access.main(), part, access);
    }
    private WindGeneratorMenu(int id, Inventory inv, BlockPos pos, boolean main,
            @Nullable WindBodyBlockEntity part, @Nullable WindMenuAccess access) {
        super(main ? AcademyContent.WIND_MAIN_MENU.get() : AcademyContent.WIND_BASE_MENU.get(), id);
        this.owner = inv.player; this.pos = pos.immutable(); this.main = main; this.part = part; this.access = access;
        data = access == null ? new SimpleContainerData(WindMenuAccess.DATA_COUNT) : access.data();
        addSlot(new Slot(display, 0, main ? 78 : 42, main ? 9 : 80) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
            @Override public boolean mayPlace(ItemStack stack) { return access == null && supported(stack); }
            @Override public boolean mayPickup(Player player) { return access == null; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, 9 + row * 9 + col, 6 + col * 18, 105 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 6 + col * 18, 163));
        addDataSlots(data); refresh();
    }
    private boolean supported(ItemStack stack) {
        return main ? stack.is(AcademyContent.WIND_FAN.get()) : SolarGeneratorBlockEntity.chargeable(stack);
    }
    @Override public boolean stillValid(Player player) {
        return player == owner && player.containerMenu == this && player.isAlive() && !player.isSpectator()
                && (access == null ? player.level().isClientSide : access.stillValid(player));
    }
    private void refresh() {
        if (access == null) return;
        if (!access.refresh()) { display.setItem(0, ItemStack.EMPTY); return; }
        var snapshot = (main ? part.fanInventory() : part.chargeInventory()).getStackInSlot(0);
        display.setItem(0, access.stillValid(owner) ? snapshot : ItemStack.EMPTY);
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean canDragTo(Slot slot) { return slot.index != 0 && super.canDragTo(slot); }
    public int energyUnits() { return WindMenuAccess.energyUnits(data); }
    public int altitude() { return WindMenuAccess.altitude(data); }
    public int mainAltitude() { return WindMenuAccess.mainAltitude(data); }
    public int pillars() { return WindMenuAccess.pillars(data); }
    public boolean fanInstalled() { return WindMenuAccess.fanInstalled(data); }
    public WindStructureProbe.Status status() { return WindMenuAccess.status(data); }
    private static @Nullable CompoundTag tag(ItemStack stack) { return stack.getTag() == null ? null : stack.getTag().copy(); }
    private static boolean unchanged(ItemStack current, ItemStack original, int count, @Nullable CompoundTag tag) {
        return current == original && current.getCount() == count && Objects.equals(current.getTag(), tag);
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (access == null) { super.clicked(slot, button, type, player); return; }
        if (interacting || !stillValid(player)) return;
        interacting = true;
        try {
            if (type != ClickType.QUICK_CRAFT) resetQuickCraft();
            if (type == ClickType.QUICK_MOVE) { move(player, slot); return; }
            if (slot != 0) { super.clicked(slot, button, type, player); return; }
            if (type == ClickType.PICKUP && (button == 0 || button == 1)) {
                ItemStack source = getCarried(); int count = source.getCount(); CompoundTag before = tag(source);
                ItemStack incoming = count > 1 ? source.copyWithCount(1) : source;
                var result = access.exchange(player, incoming, count > 1,
                        () -> stillValid(player) && unchanged(getCarried(), source, count, before));
                if (result.accepted()) {
                    if (count > 1) { source.shrink(1); setCarried(source); }
                    else setCarried(result.previous());
                }
            } else if (type == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)) {
                var inventory = player.getInventory(); ItemStack source = inventory.getItem(button);
                int count = source.getCount(); CompoundTag before = tag(source);
                if (count > 1) return;
                var result = access.exchange(player, source,
                        () -> stillValid(player) && unchanged(inventory.getItem(button), source, count, before));
                if (result.accepted()) inventory.setItem(button, result.previous());
            } else if (type == ClickType.THROW && (button == 0 || button == 1) && getCarried().isEmpty()) {
                var result = access.exchange(player, ItemStack.EMPTY, () -> stillValid(player) && getCarried().isEmpty());
                if (result.accepted() && !result.previous().isEmpty()) player.drop(result.previous(), true);
            } else if (type == ClickType.CLONE && player.getAbilities().instabuild && getCarried().isEmpty()) {
                var copied = (main ? part.fanInventory() : part.chargeInventory()).getStackInSlot(0);
                if (stillValid(player) && getCarried().isEmpty() && !copied.isEmpty()) {
                    copied.setCount(copied.getMaxStackSize()); setCarried(copied);
                }
            }
        } finally { interacting = false; broadcastChanges(); }
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (access == null || interacting || !stillValid(player)) return ItemStack.EMPTY;
        interacting = true;
        try { return move(player, index); }
        finally { interacting = false; broadcastChanges(); }
    }
    private ItemStack move(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        if (index == 0) {
            // ホットバー・メインインベントリの空きスロットを優先する。行き先ができる前に取り出さない。インベントリが満杯なら機械は変わらない。
            for (int i = slots.size() - 1; i >= 1; i--) {
                Slot destination = slots.get(i);
                if (!destination.getItem().isEmpty()) continue;
                var result = access.exchange(player, ItemStack.EMPTY, () -> stillValid(player) && destination.getItem().isEmpty());
                if (!result.accepted() || result.previous().isEmpty()) return ItemStack.EMPTY;
                destination.set(result.previous()); destination.setChanged();
                return result.previous();
            }
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = slots.get(index); ItemStack source = sourceSlot.getItem();
        if (source.isEmpty()) return ItemStack.EMPTY;
        if (!supported(source)) {
            // 通常のインベントリのアイテムは、他の機械のメニューと同じく、プレイヤーのメインインベントリとホットバーの間で移る。
            ItemStack beforeMove = source.copy();
            if (!stillValid(player) || sourceSlot.getItem() != source) return ItemStack.EMPTY;
            boolean moved = index < 28 ? moveItemStackTo(source, 28, 37, false)
                    : moveItemStackTo(source, 1, 28, false);
            if (!moved) return ItemStack.EMPTY;
            if (source.isEmpty()) sourceSlot.set(ItemStack.EMPTY); else sourceSlot.setChanged();
            sourceSlot.onTake(player, source); return beforeMove;
        }
        int count = source.getCount(); CompoundTag before = tag(source);
        ItemStack incoming = source.copyWithCount(1);
        var result = access.exchange(player, incoming, true,
                () -> stillValid(player) && unchanged(sourceSlot.getItem(), source, count, before));
        if (!result.accepted()) return ItemStack.EMPTY;
        source.shrink(1); if (source.isEmpty()) sourceSlot.set(ItemStack.EMPTY); else sourceSlot.setChanged();
        return incoming;
    }
}
