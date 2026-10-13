package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 術者自身のクライアントへのStorm Wingのモード: stateは-1でオフ、0で溜め、1で有効。epochはモードの番号（クライアントは推進の入力に
 * 付けて返す）。stalledは、推進を求めているのにサーバーが払えない（CP不足・オーバーロード）ので滞空だけを許す印。
 */
public record StormWingState(int state, int epoch, boolean stalled) {
    public StormWingState { if (state < -1 || state > 1) throw new IllegalArgumentException("Invalid Storm Wing state"); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(state); buffer.writeVarInt(epoch); buffer.writeBoolean(stalled); }
    public static StormWingState decode(FriendlyByteBuf buffer) { return new StormWingState(buffer.readByte(), buffer.readVarInt(), buffer.readBoolean()); }
}
