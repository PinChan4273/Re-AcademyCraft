package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.HeavenMark;
import io.github.pinchan4273.reacademycraft.network.HeavenRegionState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/**
 * 天界発現の演出の通知（§12）。効果と費用はここに依存しない。怪現象の演出は、使用者・対象本人・周囲で対象が見えているプレイヤーへだけ送り（遮られた人には送らない）、
 * 描画も深度の判定があるので壁越しには見えない。
 */
final class HeavenVisuals {
    /** 演出を送る最大の距離（ブロック）。 */
    private static final double MARK_RANGE = 64, REGION_RANGE = 160;
    /** 発光の通知の有効な長さ（1秒ごとに送り直す）。 */
    private static final int GLOW_TICKS = 25;

    private HeavenVisuals() { }

    private static void send(ServerPlayer player, Object message) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
    /** 領域の状態を周囲へ送る。forceでなければ1秒ごとか範囲が変わった時だけ。 */
    static void region(HeavenManifest.Session session, boolean force) {
        var area = session.area();
        var sent = session.sentArea;
        boolean changed = sent == null || sent.chunkX() != area.chunkX() || sent.chunkZ() != area.chunkZ() || sent.shape() != area.shape();
        if (!force && !changed && session.age % 20 != 0) return;
        session.sentArea = area;
        broadcast(session.level, session.player(), new HeavenRegionState(session.player().getId(), area.chunkX(), area.chunkZ(), area.shape(), (float) area.centerY(), true));
    }
    /** 領域の解除を周囲へ送る（描画は薄れて消える）。 */
    static void regionOff(HeavenManifest.Session session) {
        var area = session.area();
        broadcast(session.level, session.player(), new HeavenRegionState(session.player().getId(), area.chunkX(), area.chunkZ(), area.shape(), (float) area.centerY(), false));
    }
    private static void broadcast(Level level, ServerPlayer owner, HeavenRegionState state) {
        if (!(level instanceof ServerLevel server)) return;
        send(owner, state);
        for (var player : server.players())
            if (player != owner && player.distanceToSqr(owner) <= REGION_RANGE * REGION_RANGE) send(player, state);
    }
    /** 対象が見えているプレイヤーへ送る。使用者と対象本人には必ず送る。 */
    private static void mark(ServerPlayer owner, LivingEntity target, HeavenMark mark) {
        send(owner, mark);
        if (target instanceof ServerPlayer player && player != owner) send(player, mark);
        if (!(target.level() instanceof ServerLevel server)) return;
        for (var player : server.players())
            if (player != owner && player != target && player.distanceToSqr(target) <= MARK_RANGE * MARK_RANGE && player.hasLineOfSight(target)) send(player, mark);
    }
    static void telegraph(HeavenManifest.Session session, LivingEntity target) {
        mark(session.player(), target, new HeavenMark(target.getId(), HeavenMark.TELEGRAPH, HeavenPhenomena.TELEGRAPH));
    }
    /** 現象の発生の瞬間。変異は{@link #mutated}が新しい実体で送る。 */
    static void occurred(HeavenManifest.Session session, LivingEntity target, HeavenSettings.Phenomenon phenomenon) {
        if (phenomenon == HeavenSettings.Phenomenon.MUTATION) return;
        if (phenomenon == HeavenSettings.Phenomenon.GLOW) { mark(session.player(), target, new HeavenMark(target.getId(), HeavenMark.GLOW, GLOW_TICKS)); return; }
        int kind = phenomenon == HeavenSettings.Phenomenon.LIGHTNING ? HeavenMark.LIGHTNING : HeavenMark.STATUS;
        mark(session.player(), target, new HeavenMark(target.getId(), kind, 0));
    }
    static void mutated(HeavenManifest.Session session, LivingEntity body) {
        mark(session.player(), body, new HeavenMark(body.getId(), HeavenMark.MUTATE, 16));
    }
    static void glowRefresh(LivingEntity entity, ServerPlayer owner) { mark(owner, entity, new HeavenMark(entity.getId(), HeavenMark.GLOW, GLOW_TICKS)); }
    /** 発光の終わりは見えているかに関わらず周囲へ送る（消し忘れないため）。 */
    static void glowEnded(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel server)) return;
        var message = new HeavenMark(entity.getId(), HeavenMark.GLOW_END, 0);
        for (var player : server.players()) if (player.distanceToSqr(entity) <= REGION_RANGE * REGION_RANGE) send(player, message);
    }
}
