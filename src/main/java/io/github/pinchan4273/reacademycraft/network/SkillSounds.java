package io.github.pinchan4273.reacademycraft.network;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * サーバーから動かす、技能用の原作FollowEntitySound: 開始時に十分近い各クライアント（術者を含む）で、技能が続くと言う間、
 * 術者に付いて行く音。各音はその条件と共に保持し、サーバーtickごとに確かめる。条件が成り立たなくなったtickに、開始を伝えた
 * すべてのクライアントへ停止を伝える。したがって技能は、終わり方ごとに自分の音を止めることを覚えておく必要がない。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class SkillSounds {
    /**
     * 付いて行く音を誰が聞くか。原作ContextManager.hBeginLinkは、技能の開始時に25ブロック以内に居たプレイヤーをコンテキストの
     * 聞き手に固定した（Context.getRangeの50が選ぶのではない）。ここでは音の開始時に50ブロック以内の全員へ送り、それらをすべて含む。
     */
    public static final double RANGE = 50;
    private static final Map<Long, Playing> PLAYING = new LinkedHashMap<>();
    private static long nextToken;

    private record Playing(ServerPlayer caster, FollowSound stop, List<ServerPlayer> listeners, BooleanSupplier lasts) { }

    private SkillSounds() { }

    /** 術者と範囲内の全員で音を開始する。lastsがfalseになると止まる。 */
    public static long follow(ServerPlayer caster, SoundEvent sound, SoundSource source, float volume, boolean loop, BooleanSupplier lasts) {
        long token = ++nextToken;
        var dimension = caster.level().dimension().location();
        var start = new FollowSound(dimension, caster.getId(), token, sound.getLocation(), source, volume, loop);
        var listeners = new ArrayList<ServerPlayer>();
        listeners.add(caster);
        for (var player : caster.serverLevel().players())
            if (player != caster && player.distanceToSqr(caster) <= RANGE * RANGE) listeners.add(player);
        for (var listener : listeners) send(listener, start);
        PLAYING.put(token, new Playing(caster, FollowSound.stop(dimension, caster.getId(), token), listeners, lasts));
        return token;
    }
    public static boolean playing(long token) { return PLAYING.containsKey(token); }

    private static void send(ServerPlayer player, FollowSound packet) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    private static void check() {
        for (Iterator<Playing> it = PLAYING.values().iterator(); it.hasNext(); ) {
            var playing = it.next();
            if (playing.caster.isRemoved() || !playing.lasts.getAsBoolean()) {
                it.remove();
                for (var listener : playing.listeners) send(listener, playing.stop);
            }
        }
    }
    /** テスト用の入口: サーバー1tick分の確認。 */
    public static void tickForTest() { check(); }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) { if (event.phase == TickEvent.Phase.END) check(); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { PLAYING.clear(); }
}
