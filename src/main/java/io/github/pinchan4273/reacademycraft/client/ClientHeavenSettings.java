package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.HeavenSettingEdit;
import io.github.pinchan4273.reacademycraft.network.HeavenSettingsSnapshot;
import io.github.pinchan4273.reacademycraft.skill.HeavenSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** サーバーが最後に送った天界発現の設定（v1.5.0）。画面はこの値だけを表示し、変更はサーバーへ送って返事を待つ。 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientHeavenSettings {
    private static HeavenSettings current = new HeavenSettings();
    private static boolean readable, received;
    private ClientHeavenSettings() { }
    public static void receive(HeavenSettingsSnapshot snapshot) {
        readable = snapshot.readable();
        received = true;
        current = HeavenSettings.load(snapshot.settings());
    }
    public static HeavenSettings current() { return current; }
    /** 値が届いていて変更できるか。 */
    public static boolean readable() { return received && readable; }
    public static void request(String key, String value) { net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HeavenSettingEdit(key, value)); }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { current = new HeavenSettings(); readable = received = false; }
}
