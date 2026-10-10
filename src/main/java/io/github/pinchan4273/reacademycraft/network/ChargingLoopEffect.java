package io.github.pinchan4273.reacademycraft.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** サーバーが認めた一時的な音・見た目の表示。モードはサーバーがセッションを受け付けたときに決まり、保存もクライアントからの受け取りもしない。 */
public record ChargingLoopEffect(ResourceLocation dimension, UUID player, int entityId, long session, boolean playing, boolean itemMode) {
    public ChargingLoopEffect {
        Objects.requireNonNull(dimension); Objects.requireNonNull(player);
        if (dimension.toString().length() > AbilitySnapshot.MAX_ID_LENGTH || entityId < 0 || session <= 0)
            throw new IllegalArgumentException("Invalid charging loop effect");
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH);
        buffer.writeUUID(player); buffer.writeVarInt(entityId); buffer.writeLong(session); buffer.writeBoolean(playing); buffer.writeBoolean(itemMode);
    }
    public static ChargingLoopEffect decode(FriendlyByteBuf buffer) {
        return new ChargingLoopEffect(ResourceLocation.parse(buffer.readUtf(AbilitySnapshot.MAX_ID_LENGTH)),
                buffer.readUUID(), buffer.readVarInt(), buffer.readLong(), buffer.readBoolean(), buffer.readBoolean());
    }
}
