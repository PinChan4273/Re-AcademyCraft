package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.WhiteWingFlight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** 白翼飛行の入力（術者のクライアント→サーバー）。入力だけで、費用と許可はサーバーが決める。 */
public record WhiteWingFlightInput(int epoch, boolean moving, boolean fastPressed) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(epoch); buffer.writeBoolean(moving); buffer.writeBoolean(fastPressed); }
    public static WhiteWingFlightInput decode(FriendlyByteBuf buffer) { return new WhiteWingFlightInput(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()); }
    public void handle(ServerPlayer player) { if (player != null) WhiteWingFlight.input(player, epoch, moving, fastPressed); }
}
