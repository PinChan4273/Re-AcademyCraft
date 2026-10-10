package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.config.AcademyConfig;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 各クライアントへサーバーのAcademyRulesSnapshotを送る: ログイン時と、サーバーの設定が再読込されたとき（ファイルの編集、
 * または端末のスイッチの保存）に全員へ。専用サーバーと統合サーバーで同じ経路を使う。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class AcademyRulesSync {
    private AcademyRulesSync() { }

    public static void send(ServerPlayer player) { send(player, AcademyRulesSnapshot.capture()); }
    private static void send(ServerPlayer player, AcademyRulesSnapshot rules) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), rules);
    }
    private static int broadcasts;
    public static void sendAll(MinecraftServer server) { broadcasts++; sendTo(server.getPlayerList().getPlayers()); }
    public static void sendTo(java.util.Collection<ServerPlayer> players) {
        var rules = AcademyRulesSnapshot.capture();
        for (var player : List.copyOf(players)) send(player, rules);
    }
    /** サーバー上の全員へ規則を送った回数（再読込の経路のテスト用）。 */
    public static int broadcasts() { return broadcasts; }

    @SubscribeEvent public static void rulesOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }
    /** modバス。Forgeは設定をファイル監視のスレッドで再読込する。規則はサーバーのスレッドで読んで送る。 */
    public static void rulesOnConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != AcademyConfig.SPEC) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> sendAll(server));
    }
}
