package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.AbilitySettings;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 能力設定の画面からの、設定1つの変更（クライアント→サーバー、1.4.0のS1）。送るのは設定の名前と値の文字列だけで、
 * サーバーは受け取った時点の自分の状態へ、コマンドと同じ入口（{@link AbilitySettings#set}）で適用する。
 * 知らない名前・許されない値・読み取り専用のデータは何も変えない。受け入れても断っても、設定の今の値を送り返す
 * （{@link SkillSettingsSync}）。画面は送り返された値だけを表示するので、断られた変更は元の表示へ戻る。
 * 死んでいる・観戦者・1tickの上限を超えた変更も、適用せずに今の値を返す（WORK-143 F140-S1-F01: 返さないと、値が変わらないので
 * 心拍でも送られず、画面の「送信中」が残った）。上限を超えた分への返事は、そのtickの最初の1回だけ（返事は1tickに上限+1まで）。
 */
public record SkillSettingEdit(ResourceLocation setting, String value) {
    private static final int MAX_ID = 64;
    /** 1tickに受ける変更の数の上限。画面の操作を十分に上回り、大量の通知だけを捨てる。 */
    public static final int MAX_PER_TICK = 16;
    private static final Map<ServerPlayer, long[]> BUDGET = new WeakHashMap<>();

    public SkillSettingEdit {
        if (setting == null || setting.toString().length() > MAX_ID) throw new IllegalArgumentException("Invalid setting");
        if (value == null || value.isEmpty() || value.length() > AbilitySettings.MAX_VALUE) throw new IllegalArgumentException("Invalid setting value");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(setting.toString(), MAX_ID);
        buffer.writeUtf(value, AbilitySettings.MAX_VALUE);
    }

    public static SkillSettingEdit decode(FriendlyByteBuf buffer) {
        var id = ResourceLocation.tryParse(buffer.readUtf(MAX_ID));
        if (id == null) throw new IllegalArgumentException("Invalid setting identifier");
        return new SkillSettingEdit(id, buffer.readUtf(AbilitySettings.MAX_VALUE));
    }

    /** 同じtickのうちに受けた変更を数え、このtickで何番目かを返す。tickが変われば数え直す。 */
    private static long countThisTick(ServerPlayer player) {
        long[] budget = BUDGET.computeIfAbsent(player, ignored -> new long[]{Long.MIN_VALUE, 0});
        long now = player.serverLevel().getGameTime();
        if (budget[0] != now) { budget[0] = now; budget[1] = 0; }
        return ++budget[1];
    }

    /** 受け取った変更を適用した結果（試験のため）。送り手が分からなければnull。 */
    public @Nullable AbilitySettings.Outcome handle(@Nullable ServerPlayer player) {
        if (player == null) return null;
        long n = countThisTick(player);
        if (n > MAX_PER_TICK) {
            // 上限を超えた分は適用しない。送った側の保留が残らないよう、このtickの最初の1回だけ今の値を返す。
            if (n == MAX_PER_TICK + 1) SkillSettingsSync.sync(player, true);
            return AbilitySettings.Outcome.OVER_BUDGET;
        }
        if (!player.isAlive() || player.isSpectator()) {
            // 死んでいる・観戦者の変更は適用しない。断ったことが画面に分かるよう、今の値を返す。
            SkillSettingsSync.sync(player, true);
            return AbilitySettings.Outcome.REFUSED_STATE;
        }
        var outcome = AbilitySettings.set(player, setting, value);
        SkillSettingsSync.sync(player, true);
        return outcome;
    }
}
