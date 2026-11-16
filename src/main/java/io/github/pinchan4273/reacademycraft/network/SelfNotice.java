package io.github.pinchan4273.reacademycraft.network;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 術者自身のクライアントだけへ送る、技能の状態の通知（そのプレイヤーへのpayload）。エンティティIDを含まない通知だけをここから送るので、
 * 作り直し（{@code PlayerEvent.Clone}）の最中の新しいプレイヤー（IDがまだ仮のもの）へも送ってよい（1.5.2）。
 * <p>
 * テストのため、プレイヤーごと・通知の型ごとに最後に送った通知を覚える（キーは去ったプレイヤーを保持しない）。触れるのは論理サーバーのスレッドだけ。
 */
public final class SelfNotice {
    private static final Map<ServerPlayer, Map<Class<?>, Object>> LAST = new WeakHashMap<>();

    private SelfNotice() { }

    /** 本人のクライアントへ送る（接続の無いプレイヤーには送らず、記録だけする）。 */
    public static void send(ServerPlayer player, AcademyPayload packet) {
        LAST.computeIfAbsent(player, ignored -> new HashMap<>()).put(packet.getClass(), packet);
        if (player.connection != null) PacketDistributor.sendToPlayer(player, packet);
    }
    /** テスト用: そのプレイヤーへ最後に送った、その型の通知（無ければnull）。 */
    @Nullable public static <T> T lastForTest(ServerPlayer player, Class<T> type) {
        var sent = LAST.get(player);
        return sent == null ? null : type.cast(sent.get(type));
    }
}
