package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.LightShieldMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Light Shieldの盾面の状態（工程2、サーバー→クライアント、通信の版63で追加）。術者自身と、術者を追跡している各クライアントへ送る。
 * 盾の正本はサーバーにあり、クライアントは受け取った法線・半径をそのまま使って、術者の今の位置に盾を置く（自分では標的を選ばない）。
 * <p>
 * tokenは盾のセッションごとの識別、sequenceはそのセッションの中で単調に増える番号。クライアントは、同じtokenの古いsequence、
 * 終わったtoken、別のdimensionの状態を捨てる。aliveがfalseなら、そのtokenの盾を消す（法線などは送らない）。
 * 状態は、向きか大きさが変わったtickと、10tickおきの再送と、新しく追跡を始めたクライアント（途中から範囲に入った観測者）へ送る。
 */
public record LightShieldState(ResourceLocation dimension, int entityId, long token, int sequence, boolean alive,
                               float nx, float ny, float nz, float radius, LightShieldMode mode) implements AcademyPayload {
    public void encode(FriendlyByteBuf b) {
        b.writeResourceLocation(dimension); b.writeVarInt(entityId); b.writeLong(token); b.writeVarInt(sequence); b.writeBoolean(alive);
        if (!alive) return;
        b.writeFloat(nx); b.writeFloat(ny); b.writeFloat(nz); b.writeFloat(radius); b.writeByte(mode.ordinal());
    }
    public static LightShieldState decode(FriendlyByteBuf b) {
        var dimension = b.readResourceLocation(); int entity = b.readVarInt(); long token = b.readLong(); int sequence = b.readVarInt();
        if (!b.readBoolean()) return new LightShieldState(dimension, entity, token, sequence, false, 0, 0, 1, 0, LightShieldMode.DEFAULT);
        float x = b.readFloat(), y = b.readFloat(), z = b.readFloat(), radius = b.readFloat();
        int mode = b.readByte();
        var modes = LightShieldMode.values();
        // 範囲外・有限でない値は、壊れた盾を描かないよう正規化する（向きは単位ベクトルへ、半径は0以上へ）。
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        if (!(length > 1e-6) || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) { x = 0; y = 0; z = 1; length = 1; }
        // ほぼ単位の長さ（送ったとおりの値）は、そのまま使う（送った値と受け取った値を同じにする）。
        if (Math.abs(length - 1) < 1e-3) length = 1;
        return new LightShieldState(dimension, entity, token, sequence, true, (float) (x / length), (float) (y / length), (float) (z / length),
                Float.isFinite(radius) ? Math.max(0, Math.min(8, radius)) : 0, modes[Math.max(0, Math.min(modes.length - 1, mode))]);
    }
}
