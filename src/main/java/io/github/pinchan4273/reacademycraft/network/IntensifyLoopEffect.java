package io.github.pinchan4273.reacademycraft.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** 所有者だけの、受付済みBody Intensifyのループの寿命。強化効果を与えたり溜め時間を渡したりはしない。 */
public record IntensifyLoopEffect(ResourceLocation dimension, UUID player, int entityId, long session, boolean playing, boolean performed) {
    public IntensifyLoopEffect(ResourceLocation dimension, UUID player, int entityId, long session, boolean playing) {
        this(dimension, player, entityId, session, playing, false);
    }
    public IntensifyLoopEffect {
        if (playing && performed) throw new IllegalArgumentException("Only terminal Intensify may report success");
        // 既存の表示の識別の範囲を再利用する。そのネットワーク識別子や観測者への経路は使わない。
        new ChargingLoopEffect(dimension, player, entityId, session, playing, false);
    }
    public ChargingLoopEffect grant() { return new ChargingLoopEffect(dimension, player, entityId, session, playing, false); }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension.toString(), AbilitySnapshot.MAX_ID_LENGTH); buffer.writeUUID(player);
        buffer.writeVarInt(entityId); buffer.writeLong(session); buffer.writeBoolean(playing); buffer.writeBoolean(performed);
    }
    public static IntensifyLoopEffect decode(FriendlyByteBuf buffer) {
        return new IntensifyLoopEffect(ResourceLocation.parse(buffer.readUtf(AbilitySnapshot.MAX_ID_LENGTH)),
                buffer.readUUID(), buffer.readVarInt(), buffer.readLong(), buffer.readBoolean(), buffer.readBoolean());
    }
}
