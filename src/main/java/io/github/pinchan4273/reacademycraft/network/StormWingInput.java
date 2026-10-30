package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.StormWing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Storm Wingの術者のクライアントから: 移動キーで推進しようとしているか。入力だけを送る: CPや費用、「今は待機なので無料」という申告は
 * 受け取らない。epochはサーバーが{@link StormWingState}で伝えた今のモードの番号で、前のモードの遅れた入力を新しいモードに効かせない。
 * 費用・回復・推進を許すかはサーバーが決める（StormWing.input）。
 */
public record StormWingInput(int epoch, boolean thrust) implements AcademyPayload {
    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(epoch); buffer.writeBoolean(thrust); }
    public static StormWingInput decode(FriendlyByteBuf buffer) { return new StormWingInput(buffer.readVarInt(), buffer.readBoolean()); }
    public void handle(ServerPlayer player) { if (player != null) StormWing.input(player, epoch, thrust); }
}
