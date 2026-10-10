package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.config.SyncedAcademyRules;
import io.github.pinchan4273.reacademycraft.network.AcademyRulesSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 参加中のサーバーの規則をこのclientに保持する。離れたサーバーの規則は何も残さない。 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientAcademyRules {
    private ClientAcademyRules() { }

    public static void receive(AcademyRulesSnapshot rules) { SyncedAcademyRules.accept(rules); }
    /** サーバーの規則はログインのたびに送られる。それより前に残っていたものは、このサーバーのものではない。 */
    @SubscribeEvent public static void rulesOnLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) { SyncedAcademyRules.clear(); }
    @SubscribeEvent public static void rulesOnLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { SyncedAcademyRules.clear(); }
}
