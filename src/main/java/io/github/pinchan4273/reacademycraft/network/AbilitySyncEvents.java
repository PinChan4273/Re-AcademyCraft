package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "academy")
public final class AbilitySyncEvents {
    // このキャッシュに触れるのは論理サーバーのスレッドだけ。キーは去ったプレイヤーを保持しない。
    private static final Map<ServerPlayer, AbilitySnapshot> LAST_SENT = new WeakHashMap<>();

    private AbilitySyncEvents() {}

    public static void sync(ServerPlayer player, boolean force) {
        if (player.connection == null) return;
        player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(data -> {
            var snapshot = AbilitySnapshot.capture(player.getUUID(), player.level().dimension().location(), data);
            if (force || !snapshot.equals(LAST_SENT.get(player))) {
                AcademyNetwork.send(player, snapshot);
                LAST_SENT.put(player, snapshot);
            }
        });
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, true);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, true);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LAST_SENT.remove(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { LAST_SENT.clear(); }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        // 原作は毎tick妨害の発生源を確かめ、印が変わるとデータをdirtyにするので、クライアントは次の心拍を待たずにすぐ知る。
        boolean moved = player.getCapability(io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.PLAYER_ABILITY)
                .map(io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData::tickInterference).orElse(false);
        if (moved) { sync(player, true); return; }
        if (player.tickCount % 5 == 0) {
            // ローカルのプレイヤーやワールドの準備前に届いたスナップショットは、心拍で取り戻す。
            sync(player, player.tickCount % 100 == 0);
        }
    }
}
