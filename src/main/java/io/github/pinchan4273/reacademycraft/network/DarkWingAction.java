package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 六翼の動作（サーバー→術者と見ている人、v1.5.0）。攻撃の種類・刺突の側・防御の向きなど、変わったときだけ送る。描画側は補間するだけ。
 * valueは刺突なら翼の番号、防御なら向き。reachは刺突の届いた距離や光線の射程。
 */
public record DarkWingAction(int entityId, int kind, int value, float reach) implements AcademyPayload {
    public static final int STAB = 0, RAY_ON = 1, RAY_OFF = 2, CANNON = 3, GUARD = 4, FLIGHT = 5;
    /** 飛行の状態（{@link #FLIGHT}のvalue）。 */
    public static final int FLIGHT_OFF = 0, FLIGHT_NORMAL = 1, FLIGHT_FAST = 2;
    public static final int GUARD_ALL = 0, GUARD_RIGHT = 1, GUARD_LEFT = 2, GUARD_DOWN = 3;
    /** 未元物質人間の形態（valueが1なら未元物質人間。頭上の輪を出す）と、変化の瞬間（白いシルエットを1回）。翼の状態とは別に持つ。 */
    public static final int BODY = 6, BODY_FLASH = 7;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId); buffer.writeByte(kind); buffer.writeByte(value); buffer.writeFloat(reach);
    }
    public static DarkWingAction decode(FriendlyByteBuf buffer) {
        return new DarkWingAction(buffer.readVarInt(), buffer.readByte(), buffer.readByte(), buffer.readFloat());
    }
    public static void send(ServerPlayer player, int kind, int value, float reach) {
        if (player.connection != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkWingAction(player.getId(), kind, value, reach));
    }
}
