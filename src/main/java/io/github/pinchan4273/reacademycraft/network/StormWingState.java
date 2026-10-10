package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/** 術者自身のクライアントへのStorm Wingのモード: -1はオフ、0は溜め、1は有効。 */
public record StormWingState(int state) {
    public StormWingState { if (state < -1 || state > 1) throw new IllegalArgumentException("Invalid Storm Wing state"); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(state); }
    public static StormWingState decode(FriendlyByteBuf buffer) { return new StormWingState(buffer.readByte()); }
}
