package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AbilitySnapshot;
import net.minecraft.client.Minecraft;

/** clientのmain threadだけから呼ぶ。キューに入れた状態が、接続をまたいで残ることは無い。 */
public final class ClientAbilitySync {
    private ClientAbilitySync() {}

    public static void receive(AbilitySnapshot snapshot) {
        var player = Minecraft.getInstance().player;
        if (player != null && snapshot.belongsTo(player.getUUID(), player.level().dimension().location())) {
            player.getCapability(AbilityCapabilities.PLAYER_ABILITY).ifPresent(snapshot::applyTo);
        }
    }
}
