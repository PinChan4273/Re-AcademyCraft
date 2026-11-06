package io.github.pinchan4273.reacademycraft.world;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * サーバーが持つコインの保管。エンティティ・chunk・ディメンションの寿命から独立している。
 * 投げたコインの返却は原作のとおりで、判定はサーバーが持つ。ログアウト時の消失、クリエイティブの変更、NBTの消失は起きない。飛んでいるエンティティはこのトークンの見た目であり、返却可能な2つ目の所有者ではない。
 */
public final class CoinLedger extends SavedData {
    private final CompoundTag data;
    private final Set<UUID> refundInProgress = new HashSet<>();
    /** 預かったコインのアイテムの読み書きに使う（1.21のItemStackはレジストリが要る）。loadとgetで設定する。無ければ動いているサーバーのもの。 */
    private net.minecraft.core.HolderLookup.Provider registries;
    public CoinLedger() {
        data = new CompoundTag(); data.putInt("schema_version", 1); data.put("entries", new CompoundTag());
    }
    private CoinLedger(CompoundTag tag) { data = tag.copy(); }
    public static CoinLedger load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var ledger = new CoinLedger(tag); ledger.registries = registries; return ledger;
    }
    private net.minecraft.core.HolderLookup.Provider registries() {
        if (registries == null) registries = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess();
        return registries;
    }
    /** 26.2: 保存の形は 1.21.1 と同じ。ファイルは data/academy/academy_coin_ledger.dat（1.21.1 は data/academy_coin_ledger.dat）。 */
    public static final net.minecraft.world.level.saveddata.SavedDataType<CoinLedger> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("academy", "academy_coin_ledger"), level -> new CoinLedger(),
            level -> CompoundTag.CODEC.xmap(tag -> load(tag, null), ledger -> ledger.save(new CompoundTag(), null)));

    private static final String NAME = "academy_coin_ledger";
    private static final java.util.Set<net.minecraft.world.level.storage.SavedDataStorage> SEEN = LegacySavedData.seen();
    /** 旧形式と新形式の中身の確かめ。未知の版や壊れた物は移さず、元のファイルを守る。 */
    public static @org.jspecify.annotations.Nullable String problem(CompoundTag tag) {
        return new CoinLedger(tag).isWritable() ? null : "unknown coin ledger: " + tag.getIntOr("schema_version", -1);
    }
    /** 移行できなかった時の、書けないデータ（{@link #isWritable()} が偽なので、投げも返却もしない。二重の返却を出さない）。 */
    public static CoinLedger blockedData() { return new CoinLedger(new CompoundTag()); }
    @Override public boolean isDirty() { return isWritable() && super.isDirty(); }

    public static CoinLedger get(ServerLevel level) {
        var ledger = LegacySavedData.open(level.getServer().overworld(), TYPE, NAME, CoinLedger::problem, CoinLedger::blockedData, SEEN);
        ledger.registries = level.registryAccess();
        return ledger;
    }
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        for (String key : data.keySet()) tag.put(key, data.get(key).copy()); return tag;
    }
    public boolean isWritable() {
        return io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(data, "schema_version", Tag.TAG_INT) && data.getIntOr("schema_version", 0) == 1
                && io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(data, "entries", Tag.TAG_COMPOUND);
    }
    public boolean hasReservation(UUID owner) { return !isWritable() || data.getCompoundOrEmpty("entries").contains(owner.toString()); }
    /** スポーンのコールバックの前に予約し、スポーンが受け入れられ手を確かめ直した後でだけ確定する。 */
    public boolean reserve(ServerPlayer player, ItemStack stack, UUID token) {
        if (!isWritable() || hasReservation(player.getUUID()) || !player.isAlive() || player.isSpectator()
                || !stack.is(AcademyContent.COIN.get()) || stack.isEmpty()) return false;
        var entry = new CompoundTag(); entry.store("token", net.minecraft.core.UUIDUtil.CODEC, token); entry.putInt("phase", 0);
        entry.putBoolean("debited", !player.getAbilities().instabuild);
        var one = stack.copy(); one.setCount(1); entry.put("item", io.github.pinchan4273.reacademycraft.world.NbtCompat.saveStack(one, player.registryAccess()));
        entry.putLong("deadline", player.level().getServer().overworld().getGameTime() + 121);
        data.getCompoundOrEmpty("entries").put(player.getUUID().toString(), entry); setDirty(); return true;
    }
    private Optional<CompoundTag> record(UUID owner, UUID token) {
        if (!isWritable()) return Optional.empty();
        var entries = data.getCompoundOrEmpty("entries"); var key = owner.toString();
        if (!io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(entries, key, Tag.TAG_COMPOUND)) return Optional.empty();
        var entry = entries.getCompoundOrEmpty(key);
        if (!entry.read("token", net.minecraft.core.UUIDUtil.CODEC).isPresent() || (token != null && !entry.read("token", net.minecraft.core.UUIDUtil.CODEC).orElseThrow().equals(token))
                || !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(entry, "phase", Tag.TAG_INT) || entry.getIntOr("phase", 0) < 0 || entry.getIntOr("phase", 0) > 2
                || !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(entry, "debited", Tag.TAG_BYTE) || (entry.getByteOr("debited", (byte) 0) != 0 && entry.getByteOr("debited", (byte) 0) != 1)
                || !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(entry, "deadline", Tag.TAG_LONG) || entry.getLongOr("deadline", 0L) < 0
                || !io.github.pinchan4273.reacademycraft.world.NbtCompat.contains(entry, "item", Tag.TAG_COMPOUND)) return Optional.empty();
        var item = io.github.pinchan4273.reacademycraft.world.NbtCompat.parseStack(registries(), entry.getCompoundOrEmpty("item"));
        return item.is(AcademyContent.COIN.get()) && item.getCount() == 1 ? Optional.of(entry) : Optional.empty();
    }
    public boolean commit(UUID owner, UUID token) {
        var entry = record(owner, token); if (entry.isEmpty() || entry.get().getIntOr("phase", 0) != 0) return false;
        entry.get().putInt("phase", 1); setDirty(); return true;
    }
    public void cancelReservation(UUID owner, UUID token) {
        var entry = record(owner, token); if (entry.isPresent() && entry.get().getIntOr("phase", 0) == 0) remove(owner);
    }
    public boolean isActive(UUID owner, UUID token) {
        return record(owner, token).filter(e -> e.getIntOr("phase", 0) == 1).isPresent();
    }
    public Optional<UUID> token(UUID owner) { return record(owner, null).map(e -> e.read("token", net.minecraft.core.UUIDUtil.CODEC).orElseThrow()); }
    public boolean requestReturn(UUID owner, UUID token) {
        var entry = record(owner, token); if (entry.isEmpty() || entry.get().getIntOr("phase", 0) == 0) return false;
        entry.get().putInt("phase", 2); setDirty(); return true;
    }
    /** Railgunは生きているコインをちょうど1回だけ引き換えられる。読み込まれていない、または期限切れの投擲では撃てない。 */
    public boolean consume(UUID owner, UUID token, long now) {
        var entry = record(owner, token);
        if (entry.isEmpty() || entry.get().getIntOr("phase", 0) != 1 || now >= entry.get().getLongOr("deadline", 0L)) return false;
        remove(owner); return true;
    }
    public boolean refundDue(ServerPlayer player) {
        var owner = player.getUUID(); var entry = record(owner, null);
        if (entry.isEmpty() || !player.isAlive() || player.isSpectator() || !refundInProgress.add(owner)) return false;
        try {
            var record = entry.get(); long now = player.level().getServer().overworld().getGameTime();
            if (record.getIntOr("phase", 0) != 2 && now < record.getLongOr("deadline", 0L)) return false;
            // 取り消された、または再入したスポーンの後で確定しなかった予約は、アイテムを持たない。
            if (record.getIntOr("phase", 0) == 0 || !record.getBooleanOr("debited", false)) { remove(owner); return true; }
            var item = io.github.pinchan4273.reacademycraft.world.NbtCompat.parseStack(registries(), record.getCompoundOrEmpty("item")); var inventory = player.getInventory();
            int selected = inventory.getSelectedSlot();
            if (inventory.getItem(selected).isEmpty()) inventory.setItem(selected, item);
            else {
                int merge = -1;
                for (int i = 0; i < 36; i++) { var target = inventory.getItem(i);
                    if (ItemStack.isSameItemSameComponents(target, item) && target.getCount() < target.getMaxStackSize()) { merge = i; break; }
                }
                if (merge >= 0) inventory.getItem(merge).grow(1);
                else if (inventory.getFreeSlot() >= 0) inventory.setItem(inventory.getFreeSlot(), item);
                else {
                    var drop = new ItemEntity(player.level(), player.getX(), player.getY() + .6, player.getZ(), item);
                    drop.setTarget(owner); drop.setDefaultPickUpDelay();
                    if (!player.level().addFreshEntity(drop) || drop.isRemoved()) return false;
                }
            }
            remove(owner); inventory.setChanged(); player.inventoryMenu.broadcastChanges(); return true;
        } finally { refundInProgress.remove(owner); }
    }
    private void remove(UUID owner) { data.getCompoundOrEmpty("entries").remove(owner.toString()); setDirty(); }
}
