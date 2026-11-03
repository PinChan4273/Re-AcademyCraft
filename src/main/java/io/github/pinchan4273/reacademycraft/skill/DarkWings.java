package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.DarkWingState;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 未元物質の六翼の展開（§1）。系統を取得していれば、技能が未習得でもVキーの能力使用モードで翼が出て、OFFで収納する。
 * 見た目だけで費用は無い。ここはサーバーの状態を持ち、変化を術者と見ている人へ伝える。描画は {@code client/ClientDarkWings}。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class DarkWings {
    private static final Set<UUID> DEPLOYED = new HashSet<>();

    private DarkWings() { }

    /** 翼を出すべきか。 */
    public static boolean shouldDeploy(ServerPlayer player) {
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        return data != null && player.isAlive() && !player.isSpectator() && data.isActive() && DarkMatter.CATEGORY.equals(data.getAbility());
    }
    public static boolean deployed(ServerPlayer player) { return DEPLOYED.contains(player.getUUID()); }

    private static void update(ServerPlayer player) {
        boolean want = shouldDeploy(player);
        if (want == DEPLOYED.contains(player.getUUID())) return;
        if (want) DEPLOYED.add(player.getUUID()); else DEPLOYED.remove(player.getUUID());
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new DarkWingState(player.getId(), want));
    }
    /** テスト用の入口。 */
    public static void tickForTest(ServerPlayer player) { update(player); }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) update(p);
    }
    /** 途中から見え始めた人にも今の状態を送る。 */
    @SubscribeEvent public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer watcher && DEPLOYED.contains(target.getUUID()) && watcher.connection != null)
            AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new DarkWingState(target.getId(), true));
        // 途中から見え始めた人にも飛行の姿勢を伝える。
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer watcher && watcher.connection != null && WhiteWingFlight.active(target))
            AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new io.github.pinchan4273.reacademycraft.network.DarkWingAction(target.getId(), io.github.pinchan4273.reacademycraft.network.DarkWingAction.FLIGHT, WhiteWingFlight.mode(target), 0));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { DEPLOYED.remove(e.getEntity().getUUID()); }
    // 次元の移動と復活ではクライアントの実体が作り直されるので、次のtickで送り直す。
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { DEPLOYED.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { DEPLOYED.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { DEPLOYED.clear(); }
}
