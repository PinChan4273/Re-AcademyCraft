package io.github.pinchan4273.reacademycraft.visual;

import io.github.pinchan4273.reacademycraft.api.client.BeamResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 外部の演出の光線の帳簿（演出API版1）: 持ち主（MOD ID）ごとの描いている光線、終わった効果IDの記憶、受け付けと拒否の数。
 * 自然能力の光線・球の一覧とは別で、上限も持ち主ごと。描画とMinecraftのクライアントには依存しない（GameTestで確かめる）。
 * 時刻は呼び出し側が渡すtickの数（クライアントの世界の時刻ではなく、帳簿を進めた回数）。
 *
 * @param <T> 光線の描画の状態（クライアントの側が持つ）
 */
public final class ExternalBeamBook<T> {
    /** 終わった効果IDを重複として扱う時間（tick）。再送や遅れて届いた通知を、もう一度描かないため。 */
    public static final int RECENT_TICKS = 200;

    private static final class Owner<T> {
        final Map<Long, T> active = new LinkedHashMap<>();
        final Map<Long, Long> recent = new HashMap<>();
        final long[] counts = new long[BeamResult.values().length];
    }
    private final Map<String, Owner<T>> owners = new HashMap<>();
    private long now;

    /** 帳簿の時刻を1つ進め、重複の記憶の期限が切れたものを捨てる。 */
    public void advance() {
        now++;
        for (var owner : owners.values()) owner.recent.values().removeIf(until -> until <= now);
    }
    public long now() { return now; }

    /** 規則の検査で断った結果を数える（受け付けの前に断ったもの）。 */
    public void refuse(String ownerModId, BeamResult result) { owner(ownerModId).counts[result.ordinal()]++; }

    /**
     * 足す。同じ効果IDが描かれているか記憶の中なら{@link BeamResult#DUPLICATE}、本数が上限なら{@link BeamResult#FULL}
     * （押し出さない）。足したら{@link BeamResult#ACCEPTED}。
     */
    public BeamResult offer(String ownerModId, long effectId, int limit, Supplier<T> make) {
        var owner = owner(ownerModId);
        BeamResult result;
        if (owner.active.containsKey(effectId) || owner.recent.containsKey(effectId)) result = BeamResult.DUPLICATE;
        else if (owner.active.size() >= limit) result = BeamResult.FULL;
        else { owner.active.put(effectId, make.get()); result = BeamResult.ACCEPTED; }
        owner.counts[result.ordinal()]++;
        return result;
    }

    /** 終わった光線（finishedがtrue）を一覧から外し、効果IDを記憶する。外した本数。 */
    public int retire(Predicate<T> finished) {
        int removed = 0;
        for (var owner : owners.values()) {
            var it = owner.active.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (finished.test(entry.getValue())) { owner.recent.put(entry.getKey(), now + RECENT_TICKS); it.remove(); removed++; }
            }
        }
        return removed;
    }

    /** 1本を早めに終える。描いていればtrue。 */
    public boolean end(String ownerModId, long effectId) {
        var owner = owners.get(ownerModId);
        if (owner == null || owner.active.remove(effectId) == null) return false;
        owner.recent.put(effectId, now + RECENT_TICKS);
        return true;
    }

    /** 持ち主の光線をすべて終える。終えた本数。 */
    public int clear(String ownerModId) {
        var owner = owners.get(ownerModId);
        if (owner == null) return 0;
        int n = owner.active.size();
        for (var id : owner.active.keySet()) owner.recent.put(id, now + RECENT_TICKS);
        owner.active.clear();
        return n;
    }

    /** 持ち主の、条件に合う光線だけを一覧から外し、効果IDを記憶する。外した本数。 */
    public int clearIf(String ownerModId, Predicate<T> remove) {
        var owner = owners.get(ownerModId);
        if (owner == null) return 0;
        int removed = 0;
        var it = owner.active.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (remove.test(entry.getValue())) { owner.recent.put(entry.getKey(), now + RECENT_TICKS); it.remove(); removed++; }
        }
        return removed;
    }

    /** すべての持ち主の光線を、記憶ごと消す（ログアウト・世界の切り替え・資源の読み直し）。数は残す。 */
    public void cancelAll() { for (var owner : owners.values()) { owner.active.clear(); owner.recent.clear(); } }

    /** 数も含めて、すべて捨てる。 */
    public void reset() { owners.clear(); now = 0; }

    public int active(String ownerModId) { var owner = owners.get(ownerModId); return owner == null ? 0 : owner.active.size(); }
    public int totalActive() { return owners.values().stream().mapToInt(owner -> owner.active.size()).sum(); }
    public long count(String ownerModId, BeamResult result) { var owner = owners.get(ownerModId); return owner == null ? 0 : owner.counts[result.ordinal()]; }

    /** 持ち主のその光線の描画の状態。無ければnull。 */
    public T get(String ownerModId, long effectId) { var owner = owners.get(ownerModId); return owner == null ? null : owner.active.get(effectId); }
    /** 持ち主の描いている光線（足した順）。 */
    public List<T> owned(String ownerModId) { var owner = owners.get(ownerModId); return owner == null ? List.of() : new ArrayList<>(owner.active.values()); }

    /** 描いている光線（描く順。持ち主ごとに、足した順）。 */
    public List<T> all() {
        var out = new ArrayList<T>();
        for (var owner : owners.values()) out.addAll(owner.active.values());
        return out;
    }

    private Owner<T> owner(String ownerModId) { return owners.computeIfAbsent(ownerModId, ignored -> new Owner<>()); }
}
