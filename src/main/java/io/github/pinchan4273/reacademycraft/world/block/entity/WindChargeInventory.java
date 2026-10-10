package io.github.pinchan4273.reacademycraft.world.block.entity;

import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

/**
 * 原作の風力の土台の1スロットのインベントリ（WeAthFolDのContainerWindGenBase/SlotIFItem）。
 * サーバー専用の充電の取引は、受け口のコールバックを呼ぶ前にエネルギーを予約する。
 */
final class WindChargeInventory {
    static final String KEY = "academy_wind_charge";
    private ItemStack stack = ItemStack.EMPTY;
    private @Nullable Tag original;
    private CompoundTag preserved = new CompoundTag();
    private @Nullable CompoundTag savedStack;
    private boolean readOnly, busy, charging, dropPending;
    private final Runnable changed;

    WindChargeInventory(Runnable changed) { this.changed = changed; }
    boolean readOnly() { return readOnly; }
    boolean busy() { return busy; }

    void load(CompoundTag tag, boolean root) {
        busy = true;
        try { loadData(tag, root); }
        finally { busy = false; }
    }
    private void loadData(CompoundTag tag, boolean root) {
        stack = ItemStack.EMPTY; original = null; preserved = new CompoundTag();
        savedStack = null; readOnly = charging = dropPending = false;
        if (!tag.contains(KEY)) return;
        original = tag.get(KEY).copy();
        if (!root || !(original instanceof CompoundTag saved)
                || !saved.contains("schema_version", Tag.TAG_INT) || saved.getInt("schema_version") != 1) {
            readOnly = true; return;
        }
        preserved = saved.copy();
        if (!saved.contains("item")) return;
        if (!saved.contains("item", Tag.TAG_COMPOUND)) { readOnly = true; return; }
        CompoundTag item = saved.getCompound("item");
        // ItemStack.ofはCountをbyteとして読む: そうしないとintの257が1に狭まって取り出せるようになり、未対応の元の個数を黙って失う。
        if (!item.contains("id", Tag.TAG_STRING) || !item.contains("Count", Tag.TAG_BYTE)
                || item.getByte("Count") != 1) { readOnly = true; return; }
        // 読み込み中に受け口のコールバックを呼ばない。以前対応していたアイテムは、エネルギーのcapabilityが無くなっても回収できなければならない。
        ItemStack decoded = ItemStack.of(item);
        if (decoded.isEmpty() || decoded.getCount() != 1) { readOnly = true; return; }
        stack = decoded; savedStack = item.copy();
    }

    void save(CompoundTag tag, boolean root, boolean frozenBody) {
        if (readOnly || frozenBody || !root) {
            if (original != null) tag.put(KEY, original.copy());
            return;
        }
        CompoundTag saved = preserved.copy(); saved.putInt("schema_version", 1); saved.remove("item");
        // 確定した不変の直列化により、保存中に他のmodを呼ばない（検証・写しのコールバック中の再帰的な保存も含む）。
        if (savedStack != null) saved.put("item", savedStack.copy());
        tag.put(KEY, saved);
    }

    ItemStack detach() {
        ItemStack result = stack; stack = ItemStack.EMPTY; savedStack = null; changed.run(); return result;
    }

    ItemStack requestDrop() {
        // コールバックが戻るまで変更したスタックを生かしておく必要があるのはreceiveEnergyだけ。通常の写し・検証のコールバックは従来の規則を保つ:
        // すぐ切り離し、取り除かれた所有者が唯一のアイテムを置き去りにできないようにする。
        if (charging) { dropPending = true; return ItemStack.EMPTY; }
        return detach();
    }

    /** 発電は既に済んでいる。receiveEnergyの前に予約し、出力の予算は独立に保ち、コールバックが引き起こしたブロックのドロップはコールバックの終了まで遅らせる。 */
    int charge(WindBaseEnergy energy, BooleanSupplier liveOwner, Consumer<ItemStack> deferredDrop) {
        if (readOnly || busy || stack.isEmpty()) return 0;
        busy = charging = true; ItemStack owned = stack; int offered = 0, accepted = 0; boolean settled = false;
        try {
            if (!liveOwner.getAsBoolean()) return 0;
            var target = owned.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (target == null || stack != owned || !liveOwner.getAsBoolean() || !target.canReceive()
                    || stack != owned || !liveOwner.getAsBoolean()) return 0;
            offered = energy.reserveChargeFE();
            if (offered <= 0) return 0;
            accepted = Math.max(0, Math.min(offered, target.receiveEnergy(offered, false)));
            boolean refund = stack == owned && !dropPending && liveOwner.getAsBoolean();
            energy.settleCharge(offered, accepted, refund); settled = true;
            // 変更済みの所有するスタックを、まだロックしている間に直列化する。このコールバックが機械を取り除いたら、実際のスタックは後でドロップする。
            CompoundTag serialized = owned.save(new CompoundTag());
            if (stack == owned && !readOnly) savedStack = serialized;
            if (refund) changed.run();
            return accepted;
        } finally {
            if (offered > 0 && !settled) {
                boolean refund = stack == owned && !dropPending && liveOwner.getAsBoolean();
                energy.settleCharge(offered, 0, refund);
            }
            charging = false; busy = false;
            if (dropPending) {
                dropPending = false;
                ItemStack dropped = detach();
                if (!dropped.isEmpty()) deferredDrop.accept(dropped);
            }
        }
    }

    private static boolean accepts(ItemStack candidate, BooleanSupplier live) {
        if (candidate.isEmpty()) return false;
        var receiver = candidate.getCapability(ForgeCapabilities.ENERGY).orElse(null);
        return live.getAsBoolean() && receiver != null && receiver.canReceive() && live.getAsBoolean();
    }

    /**
     * どちらかのアイテムを移す前に、他のすべてのコールバックを準備する。メニューや元の確認に失敗したら、入ってくるアイテムは呼び出し元に残る。
     * 準備中の破壊は、古いアイテムについて通常の切り離し・ドロップの規則に従う。
     */
    WindItemExchange exchange(ItemStack incoming, boolean onlyIfEmpty, BooleanSupplier available) {
        if (readOnly || busy || !incoming.isEmpty() && incoming.getCount() != 1)
            return WindItemExchange.rejected();
        busy = true;
        try {
            ItemStack previous = stack;
            if (onlyIfEmpty && !previous.isEmpty()) return WindItemExchange.rejected();
            boolean clearing = incoming.isEmpty();
            BooleanSupplier live = () -> stack == previous && !readOnly
                    && available.getAsBoolean() && stack == previous && !readOnly;
            if (!live.getAsBoolean() || !incoming.isEmpty() && !accepts(incoming, live))
                return WindItemExchange.rejected();
            ItemStack prepared = incoming.copy();
            if (!live.getAsBoolean() || !prepared.isEmpty()
                    && (prepared.getCount() != 1 || !accepts(prepared, live)))
                return WindItemExchange.rejected();
            CompoundTag serialized = prepared.isEmpty() ? null : prepared.save(new CompoundTag());
            if (!live.getAsBoolean() || (clearing ? !prepared.isEmpty()
                    : prepared.isEmpty() || prepared.getCount() != 1)) return WindItemExchange.rejected();
            stack = prepared; savedStack = serialized; changed.run();
            return new WindItemExchange(true, previous);
        } finally { busy = false; }
    }

    /** 代理からキャッシュしたアダプタでも、生きている正確なルートを改めて解決する。 */
    static IItemHandlerModifiable view(Supplier<WindChargeInventory> accessible) {
        return new IItemHandlerModifiable() {
            private void check(int slot) { if (slot != 0) throw new IndexOutOfBoundsException(slot); }
            private boolean live(WindChargeInventory inv, ItemStack previous) {
                return accessible.get() == inv && inv.stack == previous && !inv.readOnly;
            }
            @Override public int getSlots() { return 1; }
            @Override public int getSlotLimit(int slot) { check(slot); return 1; }
            @Override public ItemStack getStackInSlot(int slot) {
                check(slot); WindChargeInventory inv = accessible.get();
                if (inv == null || inv.busy || inv.stack.isEmpty()) return ItemStack.EMPTY;
                inv.busy = true;
                try {
                    ItemStack previous = inv.stack, copy = previous.copy();
                    return live(inv, previous) ? copy : ItemStack.EMPTY;
                } finally { inv.busy = false; }
            }
            @Override public boolean isItemValid(int slot, ItemStack candidate) {
                check(slot); WindChargeInventory inv = accessible.get();
                if (inv == null || inv.busy) return false;
                inv.busy = true;
                try {
                    ItemStack previous = inv.stack;
                    return accepts(candidate, () -> live(inv, previous));
                } finally { inv.busy = false; }
            }
            @Override public ItemStack insertItem(int slot, ItemStack candidate, boolean simulate) {
                check(slot); WindChargeInventory inv = accessible.get();
                if (inv == null || inv.busy || !inv.stack.isEmpty() || candidate.isEmpty()) return candidate;
                inv.busy = true;
                try {
                    ItemStack previous = inv.stack;
                    if (!accepts(candidate, () -> live(inv, previous))) return candidate;
                    ItemStack inserted = candidate.copyWithCount(1);
                    if (!live(inv, previous) || !accepts(inserted, () -> live(inv, previous))) return candidate;
                    CompoundTag serialized = inserted.save(new CompoundTag());
                    if (!live(inv, previous)) return candidate;
                    ItemStack rest = candidate.copy(); rest.shrink(1);
                    if (!live(inv, previous)) return candidate;
                    if (!simulate) {
                        inv.stack = inserted; inv.savedStack = serialized; inv.changed.run();
                    }
                    return rest;
                } finally { inv.busy = false; }
            }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                check(slot); WindChargeInventory inv = accessible.get();
                if (inv == null || inv.busy || amount <= 0 || inv.stack.isEmpty()) return ItemStack.EMPTY;
                if (!simulate) return inv.detach(); // 唯一の所有権が移る前に写しのコールバックは無い。
                return getStackInSlot(slot);
            }
            @Override public void setStackInSlot(int slot, ItemStack candidate) {
                check(slot); WindChargeInventory inv = accessible.get();
                if (inv == null || inv.busy || !candidate.isEmpty() && candidate.getCount() != 1) return;
                inv.busy = true;
                try {
                    ItemStack previous = inv.stack;
                    if (!candidate.isEmpty() && !accepts(candidate, () -> live(inv, previous)) || !live(inv, previous)) return;
                    ItemStack copied = candidate.copy();
                    if (!live(inv, previous)) return;
                    if (!copied.isEmpty() && !accepts(copied, () -> live(inv, previous)) || !live(inv, previous)) return;
                    CompoundTag serialized = copied.isEmpty() ? null : copied.save(new CompoundTag());
                    if (live(inv, previous)) {
                        inv.stack = copied; inv.savedStack = serialized; inv.changed.run();
                    }
                } finally { inv.busy = false; }
            }
        };
    }
}
