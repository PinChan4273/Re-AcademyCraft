package io.github.pinchan4273.reacademycraft.network;

import net.minecraft.network.FriendlyByteBuf;

/** FreqRequestへのサーバーの返答: このidの要求に対する原作のFutureの結果。 */
public record FreqReply(int id, boolean ok, String text) {
    public FreqReply {
        if (text == null || text.length() > FreqRequest.MAX_TEXT) throw new IllegalArgumentException("Invalid transmitter reply");
    }
    public void encode(FriendlyByteBuf b) { b.writeVarInt(id); b.writeBoolean(ok); b.writeUtf(text, FreqRequest.MAX_TEXT); }
    public static FreqReply decode(FriendlyByteBuf b) { return new FreqReply(b.readVarInt(), b.readBoolean(), b.readUtf(FreqRequest.MAX_TEXT)); }
}
