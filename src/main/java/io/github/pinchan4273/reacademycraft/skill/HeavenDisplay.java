package io.github.pinchan4273.reacademycraft.skill;

import io.github.pinchan4273.reacademycraft.world.AcademyEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 天界発現の表示（v1.5.0、ユーザーの決定）: 領域の範囲の中のプレイヤー全員（使用者本人、個別の強化・弱体を受けていない人も）へ、表示専用の中立の効果
 * 「天界」（{@link AcademyEffects#HEAVEN}）を1件だけ付ける。標準のHUD・インベントリの効果の一覧に出るので、同じ一覧を読む表示改善MODにも見える。
 * <p>
 * 天界由来の強化・弱体・状態異常は今どおりサーバーが直接処理し（{@link HeavenEffects}・{@link HeavenPhenomena}・{@link DirectEffects}）、バニラの状態効果を
 * 付けないので、個別の名前や残り時間は出ない。表示の効果は何もしないので、外部から消されても直接の作用は続き（次の走査で付け直す）、コマンドで表示だけを
 * 付けても領域や怪現象は起きない。
 * <p>
 * 付け外しは{@link HeavenManifest}の走査（{@link HeavenManifest#SCAN_TICKS}ごと、変更・解除の直後）に載せ、走査が見つけた範囲の中の実体だけを見る。
 * 領域が重なっても「天界」の表示は1件。最後の有効な領域から出たことを検出した時点で解除する。付与する効果は無期限・粒子なし・アイコンあり。
 * <p>
 * こちらが付けた物の見分け: 付けたプレイヤーの永続データに{@link #MARK}を置く。領域は再起動・再ログインで残らないので、印のあるまま接続したプレイヤーからは
 * 外す（範囲の中なら次の走査で付け直す）。印の無い「天界」（コマンドで付けた物）は勝手に消さない。外す時も、こちらの付け方（無期限）の物だけを外す。
 */
@Mod.EventBusSubscriber(modid = "academy")
public final class HeavenDisplay {
    /** こちらが付けたことの印（プレイヤーの永続データのキー）。 */
    public static final String MARK = "academy_heaven_display";
    /** 今こちらが表示を付けているプレイヤー（UUIDと、最後に見た実体）。 */
    private static final Map<UUID, ServerPlayer> SHOWN = new HashMap<>();
    /** 試験用: 世界に入れないFakePlayerを、世界の中で範囲を判定されるプレイヤーとして扱う。 */
    private static final List<ServerPlayer> BYSTANDERS = new ArrayList<>();

    private HeavenDisplay() { }

    /** 外す可能性のある表示があるか（あれば領域が無くても走査を回す）。 */
    static boolean busy() { return !SHOWN.isEmpty(); }

    /** 走査の後に呼ぶ: 今の有効な領域のどれかの範囲にいるプレイヤーへ付け、どの領域にもいなくなったプレイヤーから外す。 */
    static void update() {
        var now = new HashMap<UUID, ServerPlayer>();
        for (var session : HeavenManifest.sessions()) {
            for (var entity : session.inside) if (entity instanceof ServerPlayer player && eligible(player)) now.put(player.getUUID(), player);
            for (var player : BYSTANDERS)
                if (player.level() == session.level && session.area.contains(player) && eligible(player)) now.put(player.getUUID(), player);
        }
        for (var player : now.values()) show(player);
        for (var entry : SHOWN.entrySet()) if (!now.containsKey(entry.getKey())) hide(current(entry.getKey(), entry.getValue()));
        SHOWN.clear();
        SHOWN.putAll(now);
    }
    /** 表示の対象か。観戦者は世界の中の対象ではない（領域の区分{@link HeavenManifest#classify}と同じ）ので付けない。 */
    private static boolean eligible(ServerPlayer player) { return player.isAlive() && !player.isSpectator(); }
    /** 同じUUIDの今の実体（死亡・エンドからの帰還で入れ替わる）。一覧に無ければ最後に見た実体。 */
    private static ServerPlayer current(UUID id, ServerPlayer last) {
        var live = last.server.getPlayerList().getPlayer(id);
        return live != null ? live : last;
    }
    /** 無期限・粒子なし・アイコンありで付ける。既に無期限の物があれば何もしない（他の誰かが外していたら付け直す）。 */
    private static void show(ServerPlayer player) {
        var effect = AcademyEffects.HEAVEN.get();
        var current = player.getEffect(effect);
        if (current == null || !current.isInfiniteDuration())
            player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        if (!player.getPersistentData().getBoolean(MARK)) player.getPersistentData().putBoolean(MARK, true);
    }
    /** こちらが付けた表示を外す。時間制の物（コマンドで付けた物）は残す。 */
    private static void hide(@Nullable ServerPlayer player) {
        if (player == null) return;
        player.getPersistentData().remove(MARK);
        var effect = AcademyEffects.HEAVEN.get();
        var current = player.getEffect(effect);
        if (current != null && current.isInfiniteDuration()) player.removeEffect(effect);
    }

    // ---- 接続・実体の入れ替わり ----

    /** 再起動・再ログインで残った表示を外す（領域は残らない）。今も他人の領域の範囲の中なら、次の走査で付け直す。 */
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && player.getPersistentData().getBoolean(MARK)) hide(player);
    }
    /** ログアウトでは保存の前に外す（Forgeのログアウトのイベントはプレイヤーの保存より先に来る）。 */
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && SHOWN.remove(player.getUUID()) != null) hide(player);
    }
    /** 実体が入れ替わった（死亡・エンドからの帰還）: 新しい実体を覚え、効果が写っていれば印も写す。範囲の外なら次の走査で外れる。 */
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !SHOWN.containsKey(player.getUUID())) return;
        SHOWN.put(player.getUUID(), player);
        if (player.hasEffect(AcademyEffects.HEAVEN.get())) player.getPersistentData().putBoolean(MARK, true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { SHOWN.clear(); BYSTANDERS.clear(); }

    // ---- 試験用の入口 ----

    /** 試験用: このプレイヤーを世界の中にいるものとして範囲の判定に加える（{@link #clearBystandersForTest}で外す）。 */
    public static void addBystanderForTest(ServerPlayer player) { BYSTANDERS.add(player); }
    public static void clearBystandersForTest() { BYSTANDERS.clear(); }
    /** 試験用: こちらが今このプレイヤーへ表示を付けているか。 */
    public static boolean shownForTest(ServerPlayer player) { return SHOWN.containsKey(player.getUUID()); }
}
