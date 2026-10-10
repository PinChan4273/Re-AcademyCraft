package io.github.pinchan4273.reacademycraft.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 受け付けた完了1回分の見た目だけ。音・溜め時間・強化効果は運ばない。 */
public record IntensifyBurstEffect(ResourceLocation dimension, UUID player, int entityId, long session) {
    public IntensifyBurstEffect { new ChargingLoopEffect(dimension, player, entityId, session, true, false); }
    public ChargingLoopEffect grant() { return new ChargingLoopEffect(dimension, player, entityId, session, true, false); }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH); buffer.writeUUID(player);
        buffer.writeVarInt(entityId); buffer.writeLong(session);
    }
    public static IntensifyBurstEffect decode(FriendlyByteBuf buffer) {
        return new IntensifyBurstEffect(ResourceLocation.parse(buffer.readUtf(AbilitySnapshot.MAX_ID_LENGTH)),
                buffer.readUUID(), buffer.readVarInt(), buffer.readLong());
    }
}
