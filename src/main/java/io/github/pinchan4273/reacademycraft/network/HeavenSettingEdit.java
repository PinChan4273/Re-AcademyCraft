package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.skill.HeavenManifest;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 天界発現の設定の画面からの変更（クライアント→サーバー、v1.5.0 §10〜§12）。送るのはキーと値の文字列だけで、サーバーが検証して適用し、受け入れても断っても今の値を送り返す
 * （{@link HeavenSettingsSnapshot}）。画面は送り返された値だけを表示する。
 * <ul>
 * <li>{@link #SYNC}: 変更せず、今の値を送り返す（画面を開いた時）。</li>
 * <li>{@link #STOP}: 領域を解除する（§13: プリセットから外れた領域の個別停止）。</li>
 * <li>{@code player.<UUID>}: 個別プレイヤーの登録。値はauto・ally・exclude。</li>
 * <li>それ以外は{@link HeavenSettings#apply}。</li>
 * </ul>
 * 能力OFF・過負荷・干渉の間も設定は変えられる。死んでいる・観戦者の変更と、1tickの上限を超えた変更は適用しない。
 */
public record HeavenSettingEdit(String key, String value) implements AcademyPayload {
    public static final String SYNC = "sync", STOP = "stop";
    private static final int MAX_KEY = 64, MAX_VALUE = 16;
    /** 1tickに受ける変更の数の上限。 */
    public static final int MAX_PER_TICK = 16;
    private static final Map<ServerPlayer, long[]> BUDGET = new WeakHashMap<>();

    public HeavenSettingEdit {
        if (key == null || key.isEmpty() || key.length() > MAX_KEY) throw new IllegalArgumentException("Invalid heaven setting key");
        if (value == null || value.length() > MAX_VALUE) throw new IllegalArgumentException("Invalid heaven setting value");
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeUtf(key, MAX_KEY); buffer.writeUtf(value, MAX_VALUE); }
    public static HeavenSettingEdit decode(FriendlyByteBuf buffer) { return new HeavenSettingEdit(buffer.readUtf(MAX_KEY), buffer.readUtf(MAX_VALUE)); }

    /** 受け取った変更を適用する。変わったらtrue（試験のため）。 */
    public boolean handle(@Nullable ServerPlayer player) {
        if (player == null) return false;
        long[] budget = BUDGET.computeIfAbsent(player, ignored -> new long[]{Long.MIN_VALUE, 0});
        long now = player.serverLevel().getGameTime();
        if (budget[0] != now) { budget[0] = now; budget[1] = 0; }
        // 上限を超えた分へは返事もしない（最初に超えた1回だけ、今の値を返す）。
        if (++budget[1] > MAX_PER_TICK) { if (budget[1] == MAX_PER_TICK + 1) HeavenSettingsSnapshot.send(player); return false; }
        boolean changed = player.isAlive() && !player.isSpectator() && apply(player);
        if (changed) HeavenManifest.markDirty();
        HeavenSettingsSnapshot.send(player);
        return changed;
    }
    private boolean apply(ServerPlayer player) {
        if (SYNC.equals(key)) return false;
        if (STOP.equals(key)) { boolean was = HeavenManifest.active(player); HeavenManifest.stop(player); return was; }
        var data = io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities.of(player).orElse(null);
        if (data == null || data.isReadOnly()) return false;
        var settings = data.getHeavenSettings();
        if (key.startsWith(HeavenSettings.KEY_PLAYER)) {
            var mode = HeavenSettings.PlayerMode.parse(value);
            UUID id;
            try { id = UUID.fromString(key.substring(HeavenSettings.KEY_PLAYER.length())); } catch (IllegalArgumentException e) { return false; }
            if (mode == null || id.equals(player.getUUID())) return false;
            // 名前はサーバーが知っているものだけを使う（クライアントの文字列を保存しない）。
            var server = player.getServer();
            var other = server == null ? null : server.getPlayerList().getPlayer(id);
            var old = settings.players().get(id);
            if (other == null && old == null && mode != HeavenSettings.PlayerMode.AUTO) return false;
            return settings.setPlayer(id, mode, other != null ? other.getGameProfile().getName() : old != null ? old.name() : "");
        }
        return settings.apply(key, value);
    }
}
