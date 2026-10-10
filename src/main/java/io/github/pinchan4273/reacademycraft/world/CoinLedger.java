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
    public CoinLedger() {
        data = new CompoundTag(); data.putInt("schema_version", 1); data.put("entries", new CompoundTag());
    }
    private CoinLedger(CompoundTag tag) { data = tag.copy(); }
    public static CoinLedger load(CompoundTag tag) { return new CoinLedger(tag); }
    public static CoinLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(CoinLedger::load, CoinLedger::new, "academy_coin_ledger");
    }
    @Override public CompoundTag save(CompoundTag tag) {
        for (String key : data.getAllKeys()) tag.put(key, data.get(key).copy()); return tag;
    }
    public boolean isWritable() {
        return data.contains("schema_version", Tag.TAG_INT) && data.getInt("schema_version") == 1
                && data.contains("entries", Tag.TAG_COMPOUND);
    }
    public boolean hasReservation(UUID owner) { return !isWritable() || data.getCompound("entries").contains(owner.toString()); }
    /** スポーンのコールバックの前に予約し、スポーンが受け入れられ手を確かめ直した後でだけ確定する。 */
    public boolean reserve(ServerPlayer player, ItemStack stack, UUID token) {
        if (!isWritable() || hasReservation(player.getUUID()) || !player.isAlive() || player.isSpectator()
                || !stack.is(AcademyContent.COIN.get()) || stack.isEmpty()) return false;
        var entry = new CompoundTag(); entry.putUUID("token", token); entry.putInt("phase", 0);
        entry.putBoolean("debited", !player.getAbilities().instabuild);
        var one = stack.copy(); one.setCount(1); entry.put("item", one.save(new CompoundTag()));
        entry.putLong("deadline", player.serverLevel().getServer().overworld().getGameTime() + 121);
        data.getCompound("entries").put(player.getUUID().toString(), entry); setDirty(); return true;
    }
    private Optional<CompoundTag> record(UUID owner, UUID token) {
        if (!isWritable()) return Optional.empty();
        var entries = data.getCompound("entries"); var key = owner.toString();
        if (!entries.contains(key, Tag.TAG_COMPOUND)) return Optional.empty();
        var entry = entries.getCompound(key);
        if (!entry.hasUUID("token") || (token != null && !entry.getUUID("token").equals(token))
                || !entry.contains("phase", Tag.TAG_INT) || entry.getInt("phase") < 0 || entry.getInt("phase") > 2
                || !entry.contains("debited", Tag.TAG_BYTE) || (entry.getByte("debited") != 0 && entry.getByte("debited") != 1)
                || !entry.contains("deadline", Tag.TAG_LONG) || entry.getLong("deadline") < 0
                || !entry.contains("item", Tag.TAG_COMPOUND)) return Optional.empty();
        var item = ItemStack.of(entry.getCompound("item"));
        return item.is(AcademyContent.COIN.get()) && item.getCount() == 1 ? Optional.of(entry) : Optional.empty();
    }
    public boolean commit(UUID owner, UUID token) {
        var entry = record(owner, token); if (entry.isEmpty() || entry.get().getInt("phase") != 0) return false;
        entry.get().putInt("phase", 1); setDirty(); return true;
    }
    public void cancelReservation(UUID owner, UUID token) {
        var entry = record(owner, token); if (entry.isPresent() && entry.get().getInt("phase") == 0) remove(owner);
    }
    public boolean isActive(UUID owner, UUID token) {
        return record(owner, token).filter(e -> e.getInt("phase") == 1).isPresent();
    }
    public Optional<UUID> token(UUID owner) { return record(owner, null).map(e -> e.getUUID("token")); }
    public boolean requestReturn(UUID owner, UUID token) {
        var entry = record(owner, token); if (entry.isEmpty() || entry.get().getInt("phase") == 0) return false;
        entry.get().putInt("phase", 2); setDirty(); return true;
    }
    /** Railgunは生きているコインをちょうど1回だけ引き換えられる。読み込まれていない、または期限切れの投擲では撃てない。 */
    public boolean consume(UUID owner, UUID token, long now) {
        var entry = record(owner, token);
        if (entry.isEmpty() || entry.get().getInt("phase") != 1 || now >= entry.get().getLong("deadline")) return false;
        remove(owner); return true;
    }
    public boolean refundDue(ServerPlayer player) {
        var owner = player.getUUID(); var entry = record(owner, null);
        if (entry.isEmpty() || !player.isAlive() || player.isSpectator() || !refundInProgress.add(owner)) return false;
        try {
            var record = entry.get(); long now = player.serverLevel().getServer().overworld().getGameTime();
            if (record.getInt("phase") != 2 && now < record.getLong("deadline")) return false;
            // 取り消された、または再入したスポーンの後で確定しなかった予約は、アイテムを持たない。
            if (record.getInt("phase") == 0 || !record.getBoolean("debited")) { remove(owner); return true; }
            var item = ItemStack.of(record.getCompound("item")); var inventory = player.getInventory();
            int selected = inventory.selected;
            if (inventory.getItem(selected).isEmpty()) inventory.setItem(selected, item);
            else {
                int merge = -1;
                for (int i = 0; i < 36; i++) { var target = inventory.getItem(i);
                    if (ItemStack.isSameItemSameTags(target, item) && target.getCount() < target.getMaxStackSize()) { merge = i; break; }
                }
                if (merge >= 0) inventory.getItem(merge).grow(1);
                else if (inventory.getFreeSlot() >= 0) inventory.setItem(inventory.getFreeSlot(), item);
                else {
                    var drop = new ItemEntity(player.level(), player.getX(), player.getY() + .6, player.getZ(), item);
                    drop.setTarget(owner); drop.setDefaultPickUpDelay();
                    if (!player.serverLevel().addFreshEntity(drop) || drop.isRemoved()) return false;
                }
            }
            remove(owner); inventory.setChanged(); player.inventoryMenu.broadcastChanges(); return true;
        } finally { refundInProgress.remove(owner); }
    }
    private void remove(UUID owner) { data.getCompound("entries").remove(owner.toString()); setDirty(); }
}
