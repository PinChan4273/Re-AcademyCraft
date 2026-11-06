package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** 範囲を限った接続ごとの順序・リースの状態。ゲームプレイや描画の決定権は持たない。 */
public final class ThunderClapPresentationLedger {
    public static final int LIMIT = 128, LEASE_TICKS = 40;
    public enum Decision { START, UPDATE, STOP, IGNORE }
    private record Entry(long session, int revision, int entityId, boolean active, boolean terminal,
                         long seen, Vec3 target) { }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public Decision accept(ThunderClapPresentationEffect effect, long now) {
        var old = entries.get(effect.player());
        if (old != null && (effect.session() < old.session
                || effect.session() == old.session && (old.terminal || effect.entityId() != old.entityId
                || effect.revision() <= old.revision || effect.action() == ThunderClapPresentationEffect.Action.START)))
            return Decision.IGNORE;
        if ((old == null || effect.session() > old.session)
                && effect.action() == ThunderClapPresentationEffect.Action.UPDATE) return Decision.IGNORE;
        if (old == null && entries.size() == LIMIT) {
            var iterator = entries.entrySet().iterator(); boolean removed = false;
            while (iterator.hasNext()) if (!iterator.next().getValue().active) { iterator.remove(); removed = true; break; }
            if (!removed) return Decision.IGNORE;
        }
        boolean active = effect.action() != ThunderClapPresentationEffect.Action.STOP;
        entries.put(effect.player(), new Entry(effect.session(), effect.revision(), effect.entityId(), active,
                !active, now, effect.target()));
        return switch (effect.action()) {
            case START -> Decision.START;
            case UPDATE -> Decision.UPDATE;
            case STOP -> Decision.STOP;
        };
    }

    public List<UUID> expire(long now) {
        var expired = new ArrayList<UUID>();
        entries.replaceAll((id, entry) -> {
            if (entry.active && (now < entry.seen || now - entry.seen >= LEASE_TICKS)) {
                expired.add(id); return new Entry(entry.session, entry.revision, entry.entityId,
                        false, true, now, entry.target);
            }
            return entry;
        });
        return List.copyOf(expired);
    }

    public boolean active(UUID player) { var entry = entries.get(player); return entry != null && entry.active; }
    public Vec3 target(UUID player) { var entry = entries.get(player); return entry == null ? null : entry.target; }
    public int size() { return entries.size(); }
    public void cancel(UUID player) {
        var entry = entries.get(player);
        if (entry != null && !entry.terminal) entries.put(player, new Entry(entry.session, entry.revision,
                entry.entityId, false, true, entry.seen, entry.target));
    }
    public void clear() { entries.clear(); }
}
