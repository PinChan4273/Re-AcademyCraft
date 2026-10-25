package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.PendingSettingEdits;
import io.github.pinchan4273.reacademycraft.network.SkillSettingEdit;
import io.github.pinchan4273.reacademycraft.network.SkillSettingsSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * クライアントが知る能力設定の値（1.4.0のS1）。サーバーから届いた値（{@link SkillSettingsSnapshot}）だけを持ち、画面はこれを表示する。
 * 変更を送った設定は、次の値が届くまで「送信中」として数える（受け入れられても断られても、サーバーは値を送り返す）。
 * 返事が{@link PendingSettingEdits#TIMEOUT_TICKS}tick届かなければ、その保留は終える（WORK-143 F140-S1-F01の念のための守り）。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientSkillSettings {
    private static Map<ResourceLocation, String> values = Map.of();
    private static boolean readable, received;
    private static final PendingSettingEdits PENDING = new PendingSettingEdits();
    /** クライアントのtickの数（保留の期限を測る。ワールドの時刻ではないので、ワールドの外でも進む）。 */
    private static long ticks;
    /** 届いた値の数（試験で、届いたことを待つため）。 */
    private static int snapshots;

    private ClientSkillSettings() { }

    public static void receive(SkillSettingsSnapshot snapshot) {
        values = new LinkedHashMap<>(snapshot.values());
        readable = snapshot.readable();
        received = true;
        snapshots++;
        PENDING.answered();
    }

    /** 設定の今の値（サーバーの値）。まだ届いていない・知らない設定ならnull。 */
    public static @Nullable String value(ResourceLocation setting) { return values.get(setting); }
    public static boolean readable() { return received && readable; }
    public static boolean received() { return received; }
    public static boolean pending() { return !PENDING.isEmpty(); }
    public static boolean pending(ResourceLocation setting) { return PENDING.contains(setting); }
    public static int snapshots() { return snapshots; }

    /** 1つの設定の変更をサーバーへ送る。値は、届いた値を書き換えない（サーバーの返事で変わる）。 */
    public static void request(ResourceLocation setting, String value) {
        if (!readable()) return;
        PENDING.sent(setting, ticks);
        AcademyNetwork.CHANNEL.sendToServer(new SkillSettingEdit(setting, value));
    }

    /** 値はログインのたびに送られる。それより前に残っていたものは、このサーバーのものではない（ClientAcademyRulesと同じ）。 */
    @SubscribeEvent public static void skillSettingsOnLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) { reset(); }
    @SubscribeEvent public static void skillSettingsOnLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    @SubscribeEvent public static void skillSettingsOnClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        PENDING.expire(ticks);
    }

    /** ワールドを離れたときに、前のサーバーの値を捨てる。 */
    public static void reset() {
        values = Map.of();
        readable = received = false;
        PENDING.answered();
    }
}
