package io.github.pinchan4273.reacademycraft.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 接続ごとの、ブロックへの電弧の許可と順序付きの対象。対象のパケットが許可を作ったり更新したりすることはない。 */
public final class ChargingArcLedger {
    public static final int LIMIT = 128, GRANT_TICKS = 40, TARGET_TICKS = 10;
    private record Entry(ChargingLoopEffect grant, boolean active, boolean terminal, long seen,
                         long revision, ChargingArcTarget target, long targetSeen) { }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public ChargingLoopLedger.Decision accept(ChargingLoopEffect grant, long now) {
        var old = entries.get(grant.player());
        boolean same = old != null && grant.session() == old.grant.session();
        if (old != null && (grant.session() < old.grant.session() || same && (old.terminal
                || grant.entityId() != old.grant.entityId() || grant.itemMode() != old.grant.itemMode()
                || !grant.dimension().equals(old.grant.dimension())))) return ChargingLoopLedger.Decision.IGNORE;
        if (old == null && entries.size() == LIMIT) {
            var iterator = entries.entrySet().iterator(); boolean removed = false;
            while (iterator.hasNext()) if (!iterator.next().getValue().active) { iterator.remove(); removed = true; break; }
            if (!removed) return ChargingLoopLedger.Decision.IGNORE;
        }
        boolean retain = same && old.active && grant.playing();
        entries.put(grant.player(), new Entry(grant, grant.playing(), !grant.playing(), now,
                same ? old.revision : 0, retain ? old.target : null, retain ? old.targetSeen : 0));
        if (!grant.playing()) return ChargingLoopLedger.Decision.STOP;
        return retain ? ChargingLoopLedger.Decision.REFRESH : ChargingLoopLedger.Decision.START;
    }
    public boolean accept(ChargingArcTarget packet, long now) {
        var e = entries.get(packet.player());
        if (e == null || !live(e, now) || e.grant.itemMode() || packet.session() != e.grant.session()
                || packet.entityId() != e.grant.entityId() || !packet.dimension().equals(e.grant.dimension())
                || packet.revision() <= e.revision) return false;
        entries.put(packet.player(), new Entry(e.grant, true, false, e.seen, packet.revision(), packet, now));
        return true;
    }
    private static boolean live(Entry e, long now) { return e.active && now >= e.seen && now - e.seen < GRANT_TICKS; }
    public void expire(long now) {
        entries.replaceAll((id, e) -> e.active && !live(e, now)
                ? new Entry(e.grant, false, false, now, e.revision, null, 0) : e);
    }
    public ChargingArcTarget target(UUID player, long now) {
        var e = entries.get(player);
        return e != null && live(e, now) && e.target != null && now >= e.targetSeen && now - e.targetSeen < TARGET_TICKS ? e.target : null;
    }
    public boolean active(UUID player, long now) { var e = entries.get(player); return e != null && live(e, now) && !e.grant.itemMode(); }
    public void cancel(UUID player) {
        entries.computeIfPresent(player, (id, e) -> new Entry(e.grant, false, true, e.seen, e.revision, null, 0));
    }
    public int size() { return entries.size(); }
    public void clear() { entries.clear(); }
}
