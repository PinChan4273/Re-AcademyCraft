package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 六翼の状態（サーバー→術者と見ている人、v1.5.0）。変わったときと、見え始めたときに送る。
 * 視線・姿勢は送らない（描画側がその場の値で補間する）。
 */
public record DarkWingState(int entityId, boolean deployed) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeBoolean(deployed);
    }
    public static DarkWingState decode(FriendlyByteBuf buffer) {
        return new DarkWingState(buffer.readVarInt(), buffer.readBoolean());
    }
}
