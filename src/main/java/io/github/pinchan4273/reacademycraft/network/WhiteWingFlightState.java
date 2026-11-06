package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/** 白翼飛行の状態（サーバー→術者）。epochは今のモードの番号で、入力に付けて返す。 */
public record WhiteWingFlightState(boolean on, int epoch, boolean fast, boolean safetyHover) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(on); buffer.writeVarInt(epoch); buffer.writeBoolean(fast); buffer.writeBoolean(safetyHover);
    }
    public static WhiteWingFlightState decode(FriendlyByteBuf buffer) {
        return new WhiteWingFlightState(buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
    }
}
