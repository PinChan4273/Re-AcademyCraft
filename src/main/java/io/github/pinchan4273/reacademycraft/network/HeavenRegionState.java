package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 領域の表示の状態（サーバー→周囲のプレイヤー、v1.5.0 §12）。動いている間は1秒ごとと範囲が変わった時に送るので、途中で近づいた人にも届く。onがfalseで薄れて消える。
 * shapeはチャンク数（1・5・9・25）、centerYは上下の範囲の中心。描画だけに使い、効果と費用には関わらない。
 */
public record HeavenRegionState(int ownerId, int chunkX, int chunkZ, int shape, float centerY, boolean on) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ownerId); buffer.writeVarInt(chunkX); buffer.writeVarInt(chunkZ); buffer.writeByte(shape); buffer.writeFloat(centerY); buffer.writeBoolean(on);
    }
    public static HeavenRegionState decode(FriendlyByteBuf buffer) {
        return new HeavenRegionState(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByte(), buffer.readFloat(), buffer.readBoolean());
    }
}
