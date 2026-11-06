package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 範囲を限った接続ごとの順序とリースの状態。ゲームプレイ・NBTやクライアントのクラスは含まない。 */
public final class ChargingLoopLedger {
    public static final int LIMIT = 128, LEASE_TICKS = 40;
    public enum Decision { START, REFRESH, STOP, IGNORE }
    private record Entry(long session, int entityId, boolean itemMode, boolean active, boolean terminal, long seen) { }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public Decision accept(ChargingLoopEffect packet, long now) {
        Entry old = entries.get(packet.player());
        if (old != null && (packet.session() < old.session
                || packet.session() == old.session && (old.terminal || packet.entityId() != old.entityId
                || packet.itemMode() != old.itemMode))) return Decision.IGNORE;
        if (old == null && entries.size() == LIMIT) {
            var iterator = entries.entrySet().iterator(); boolean removed = false;
            while (iterator.hasNext()) if (!iterator.next().getValue().active) { iterator.remove(); removed = true; break; }
            if (!removed) return Decision.IGNORE; // 新しい音源を確保するために、再生中の別の音源を追い出さない。
        }
        entries.put(packet.player(), new Entry(packet.session(), packet.entityId(), packet.itemMode(), packet.playing(), !packet.playing(), now));
        if (!packet.playing()) return Decision.STOP;
        return old != null && old.session == packet.session() && old.active ? Decision.REFRESH : Decision.START;
    }
    public List<UUID> expire(long now) {
        var expired = new ArrayList<UUID>();
        entries.replaceAll((id, e) -> {
            if (e.active && (now < e.seen || now - e.seen >= LEASE_TICKS)) {
                expired.add(id); return new Entry(e.session, e.entityId, e.itemMode, false, false, now);
            }
            return e;
        });
        return List.copyOf(expired);
    }
    /** ローカルで解放したものを、遅れて届いた更新で同じ受付済みセッションとして復活させない。 */
    public void cancel(UUID player) {
        entries.computeIfPresent(player, (id, e) -> new Entry(e.session, e.entityId, e.itemMode, false, true, e.seen));
    }
    public boolean active(UUID player) { Entry e = entries.get(player); return e != null && e.active; }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }
}
