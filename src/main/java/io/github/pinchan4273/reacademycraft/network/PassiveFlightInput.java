package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.PassiveSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.5.2: パッシブにした飛行（ストームウィング・ジェット・エンジン）の離陸と停止の依頼（術者のクライアント→サーバー）。
 * 送るのは依頼の種類だけで、どの技能か・飛べるか・費用は、サーバーが自分の状態で決める（{@link PassiveSkills#takeoff}・{@link PassiveSkills#land}）。
 */
public record PassiveFlightInput(boolean takeoff) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(takeoff); }
    public static PassiveFlightInput decode(FriendlyByteBuf buffer) { return new PassiveFlightInput(buffer.readBoolean()); }
    public void handle(ServerPlayer player) {
        if (player == null) return;
        if (takeoff) PassiveSkills.takeoff(player);
        else PassiveSkills.land(player);
        AbilitySyncEvents.sync(player, true);
    }
}
