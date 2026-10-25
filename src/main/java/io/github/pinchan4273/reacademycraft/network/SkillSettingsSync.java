package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.AbilitySettings;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * 能力設定の値をクライアントへ送る（1.4.0のS1）。{@link AbilitySyncEvents#sync}から一緒に呼ばれる。ログイン・リスポーン・
 * 次元の移動では前に送った値を忘れてから呼ばれるので必ず送り、それ以外（5tickごとの心拍や技能のキーのたびの同期）では値が変わったときだけ送る
 * （コマンドで変えた値も、遅くとも次の心拍で画面に届く）。
 * 画面からの変更（{@link SkillSettingEdit}）の後は、受け入れても断っても、すぐに送る。
 */
public final class SkillSettingsSync {
    // このキャッシュに触れるのは論理サーバーのスレッドだけ。キーは去ったプレイヤーを保持しない。
    private static final Map<ServerPlayer, SkillSettingsSnapshot> LAST_SENT = new WeakHashMap<>();
    /** 送った回数（試験で、断った変更にも返事が出ること・返事が上限の内であることを数えるため）。 */
    private static final Map<ServerPlayer, Integer> SENT = new WeakHashMap<>();

    private SkillSettingsSync() { }

    public static SkillSettingsSnapshot capture(ServerPlayer player) {
        var values = AbilitySettings.values(player);
        return values == null ? new SkillSettingsSnapshot(false, Map.of()) : new SkillSettingsSnapshot(true, values);
    }

    public static void sync(ServerPlayer player, boolean force) {
        if (player.connection == null) return;
        var snapshot = capture(player);
        if (force || !snapshot.equals(LAST_SENT.get(player))) {
            AcademyNetwork.send(player, snapshot);
            LAST_SENT.put(player, snapshot);
            SENT.merge(player, 1, Integer::sum);
        }
    }

    static void forget(ServerPlayer player) { LAST_SENT.remove(player); }
    /** 試験用: このプレイヤーへ送った回数。 */
    public static int sentForTest(ServerPlayer player) { return SENT.getOrDefault(player, 0); }
    static void clear() { LAST_SENT.clear(); }
}
