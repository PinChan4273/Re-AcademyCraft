package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 領域の表示の状態（サーバー→周囲のプレイヤー、v1.5.0）。動いている間は1秒ごとと範囲が変わった時に送るので、途中で近づいた人にも届く。onがfalseで薄れて消える。
 * 1.5.2から、centerX・centerZ は範囲の中心（使用者の位置）、shape は広さの段（1・5・9・25。{@link io.github.pinchan4273.reacademycraft.skill.HeavenManifest.Area}）、
 * centerY は上下の範囲の中心。描画だけに使い、効果と費用には関わらない。
 */
public record HeavenRegionState(int ownerId, float centerX, float centerZ, int shape, float centerY, boolean on) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(ownerId); buffer.writeFloat(centerX); buffer.writeFloat(centerZ); buffer.writeByte(shape); buffer.writeFloat(centerY); buffer.writeBoolean(on);
    }
    public static HeavenRegionState decode(FriendlyByteBuf buffer) {
        return new HeavenRegionState(buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(), buffer.readByte(), buffer.readFloat(), buffer.readBoolean());
    }
}
