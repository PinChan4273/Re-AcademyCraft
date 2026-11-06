package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.ability.AbilityRegistry;
import io.github.pinchan4273.reacademycraft.ability.RegistryCheck;
import io.github.pinchan4273.reacademycraft.network.AbilityRegistryCheck;
import io.github.pinchan4273.reacademycraft.network.AbilityRegistryVerdict;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;

/** サーバーの能力の登録簿の要約を、自分の登録簿と比べて答える（1.4.0のS4、司令室11の裁定D7）。 */
public final class ClientRegistryCheck {
    private ClientRegistryCheck() { }
    private static int answered;

    public static void receive(AbilityRegistryCheck check) {
        var problems = RegistryCheck.problems(check.entries(), RegistryCheck.entries(AbilityRegistry.INSTANCE));
        answered++;
        AcademyNetwork.sendToServer(AbilityRegistryVerdict.of(problems));
    }
    /** 答えた回数（client smokeの確かめのため）。 */
    public static int answered() { return answered; }
}
