package io.github.pinchan4273.reacademycraft.network;

import io.github.pinchan4273.reacademycraft.skill.Flashing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Flashingの4方向のうち1つへの瞬間移動。クライアントからは方向だけを受け取り、行き先はサーバーが求める（原作のserverPerformと同じ）。 */
public record FlashingPerform(int direction) {
    public FlashingPerform {
        if (direction < Flashing.LEFT || direction > Flashing.BACK) throw new IllegalArgumentException("Invalid flash direction");
    }
    public void encode(FriendlyByteBuf buffer) { buffer.writeByte(direction); }
    public static FlashingPerform decode(FriendlyByteBuf buffer) { return new FlashingPerform(buffer.readUnsignedByte()); }
    public void handle(ServerPlayer player) { if (player != null) Flashing.perform(player, direction); }
}
