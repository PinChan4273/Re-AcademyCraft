package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * 六翼の動作（サーバー→術者と見ている人、v1.5.0）。攻撃の種類・刺突の側・防御の向きなど、変わったときだけ送る。描画側は補間するだけ。
 * valueは刺突なら翼のインデックス、防御なら向き。reachは刺突の届いた距離や光線の射程。
 */
public record DarkWingAction(int entityId, int kind, int value, float reach) {
    public static final int STAB = 0, RAY_ON = 1, RAY_OFF = 2, CANNON = 3, GUARD = 4, FLIGHT = 5;
    /** 飛行の状態（{@link #FLIGHT}のvalue）。 */
    public static final int FLIGHT_OFF = 0, FLIGHT_NORMAL = 1, FLIGHT_FAST = 2;
    public static final int GUARD_ALL = 0, GUARD_RIGHT = 1, GUARD_LEFT = 2, GUARD_DOWN = 3;
    /** 防御の動きの長さ（tick）と、閉じきるまでの tick（描画の{@code ClientDarkWings.State#guard}が使う{@link #guardCurve}と同じ）。 */
    public static final int GUARD_LENGTH = 18, GUARD_CLOSED = 5;
    /**
     * 防御を受けたときの、動きの新しい経過 tick（閉じたまま延ばす）。動いていなければ0から始める。
     * 動きの途中なら最初へ戻さない: 閉じている途中はそのまま続け、閉じた後（保持・開く途中）は閉じきった位置へ戻して保持を延ばす。
     */
    public static int guardTicksOnHit(int ticks) {
        return ticks < 0 || ticks > GUARD_LENGTH ? 0 : Math.min(ticks, GUARD_CLOSED);
    }
    /** 閉じきったまま保つ終わりの tick。ここから{@link #GUARD_LENGTH}まで開く。 */
    public static final int GUARD_HOLD_END = 10;
    /**
     * 防御の構えの曲線（0 = 開いている、1 = 閉じきっている）。t は防御を受けてからの経過 tick。
     * 0〜5 で閉じ、5〜10 は閉じきったまま保ち、10〜18 で開く。両端をなめらかにする。
     * 1.5.2 の修正: 以前は保持の間の値に上限が無く、なめらかにする式が 1 を超えた値で負へ跳ねて、
     * 1 回の防御の中で「閉じる → 一瞬開く → もう一度閉じる → 開く」と動いていた。
     */
    public static float guardCurve(float t) {
        if (t < 0 || t >= GUARD_LENGTH) return 0;
        float k = t < GUARD_CLOSED ? t / GUARD_CLOSED
            : t <= GUARD_HOLD_END ? 1 : 1 - (t - GUARD_HOLD_END) / (GUARD_LENGTH - GUARD_HOLD_END);
        k = Math.max(0, Math.min(1, k));
        return k * k * (3 - 2 * k);
    }
    /** 未元物質人間の形態（valueが1なら未元物質人間。頭上の輪を出す）と、変化の瞬間（白いシルエットを1回）。翼の状態とは別に持つ。 */
    public static final int BODY = 6, BODY_FLASH = 7;

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId); buffer.writeByte(kind); buffer.writeByte(value); buffer.writeFloat(reach);
    }
    public static DarkWingAction decode(FriendlyByteBuf buffer) {
        return new DarkWingAction(buffer.readVarInt(), buffer.readByte(), buffer.readByte(), buffer.readFloat());
    }
    public static void send(ServerPlayer player, int kind, int value, float reach) {
        if (player.connection != null) AcademyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new DarkWingAction(player.getId(), kind, value, reach));
    }
}
